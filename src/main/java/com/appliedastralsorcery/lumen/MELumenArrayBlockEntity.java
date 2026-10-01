package com.appliedastralsorcery.lumen;

import java.util.Set;
import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.Codec;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Native AS array with an independent, bottom-only ME node. */
public final class MELumenArrayBlockEntity extends TileLumenArray implements IGridConnectedBlockEntity {
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (IGridNodeListener<MELumenArrayBlockEntity>) (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(Set.of(Direction.DOWN))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);
    private Lumen selected;
    private int pullTarget = 2000;
    private boolean export = true;
    private boolean supplyItems = true;
    private boolean interactWithFilaments = false;

    public MELumenArrayBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.ARRAY_ENTITY), pos, state);
    }

    @Override public IManagedGridNode getMainNode() { return mainNode; }
    @Override public void saveChanges() { setChanged(); }
    @Override public Codec<Data> dataCodec() { return MELumenArrayData.CODEC; }
    private MELumenArrayData arrayData() { return (MELumenArrayData) getTileData(); }
    public Lumen getActiveLumen() { return arrayData().getActiveLumen(); }
    public boolean isSwitchPending() { return selected != null && selected != getActiveLumen(); }
    @Override public IGridNode getGridNode(Direction side) { return side == Direction.DOWN ? mainNode.getNode() : null; }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public Lumen getSelectedLumen() {
        return selected == null ? getActiveLumen() : selected;
    }
    public int getPullTarget() { return pullTarget; }
    public boolean isExportEnabled() { return export; }
    public boolean isSupplyItemsEnabled() { return supplyItems; }
    public boolean canInteractWithFilaments() { return interactWithFilaments; }
    public int getStoredAmount() {
        return getTileData().getLumenHandler().getContainedLumen().stream().mapToInt(s -> s.getAmount()).sum();
    }
    public void setPullTarget(int target) { pullTarget = Math.clamp(target, 0, Data.LUMEN_TANK_CAPACITY); setChanged(); }
    public void setExportEnabled(boolean value) { export = value; setChanged(); }
    public void setSupplyItemsEnabled(boolean value) { supplyItems = value; setChanged(); }
    public void setInteractWithFilaments(boolean value) { interactWithFilaments = value; setChanged(); }
    public void setSelectedLumen(Lumen type) {
        if (type == null || type == LumenAS.NONE.get()) return;
        selected = type;
        finishSwitchIfEmpty();
        setChanged();
    }

    private void finishSwitchIfEmpty() {
        if (!isSwitchPending() || getStoredAmount() != 0 || !getTileData().getInventory().getStackInSlot(0).isEmpty()) return;
        // With an empty slot this only clears AS's cached recipe and request chains, without breaking any item.
        breakCatalyst();
        arrayData().bindEmptyArray(selected);
        if (level instanceof ServerLevel server) {
            LumenNetworkHelper.getNode(server, worldPosition).ifPresent(node ->
                    LumenNetworkHelper.setNodeProvidedLumenTypes(server, node, Set.of(selected)));
        }
    }

    private void returnPreviousContents() {
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        var handler = getTileData().getLumenHandler();
        for (var stack : handler.getContainedLumen()) {
            if (stack.isEmpty()) continue;
            long accepted = storage.insert(LumenKey.of(stack.getLumen()), stack.getAmount(), Actionable.MODULATE, source);
            if (accepted > 0) handler.drain(stack.getLumen(), (int) accepted, ILumenHandler.Action.EXECUTE);
        }
        var inventory = getTileData().getInventory();
        var catalyst = inventory.getStackInSlot(0);
        if (!catalyst.isEmpty()) {
            long accepted = storage.insert(AEItemKey.of(catalyst), catalyst.getCount(), Actionable.MODULATE, source);
            if (accepted > 0) {
                inventory.setStackInSlot(0, catalyst.copyWithCount(catalyst.getCount() - (int) accepted));
                getTileData().markForUpdate();
            }
        }
        finishSwitchIfEmpty();
    }

    @Override public void serverTick(ServerLevel server) {
        if (!mainNode.isReady()) {
            mainNode.setVisualRepresentation(ModContent.ME_LUMEN_ARRAY_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        if (isSwitchPending()) {
            finishSwitchIfEmpty();
            if (isSwitchPending() && server.getGameTime() % 20 == 0 && mainNode.isOnline()) returnPreviousContents();
            // Pause production and refills until old contents have been safely stored; the old source remains drainable.
            if (isSwitchPending()) return;
        }
        if (server.getGameTime() % 20 == 0 && mainNode.isOnline()) supplyFromNetwork();
        super.serverTick(server);
        if (server.getGameTime() % 20 == 0 && mainNode.isOnline()) exportSurplus();
    }

    private void supplyFromNetwork() {
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        var handler = getTileData().getLumenHandler();
        var actualType = getSelectedLumen();
        if (selected != actualType) {
            selected = actualType;
            setChanged();
        }
        if (selected != null && getStoredAmount() < pullTarget) {
            int wanted = handler.fill(selected.stack(pullTarget - getStoredAmount()), ILumenHandler.Action.SIMULATE);
            long extracted = storage.extract(LumenKey.of(selected), wanted, Actionable.MODULATE, source);
            if (extracted > 0) handler.fill(selected.stack((int) extracted), ILumenHandler.Action.EXECUTE);
        }
        if (!supplyItems) return;
        // Refill the original 2000 mB tank; native generation still consumes starlight normally.
        var fluid = FluidsAS.LIQUID_STARLIGHT.getSource().get();
        var tank = getTileData().getFluidTank();
        int wantedFluid = tank.fill(new FluidStack(fluid, Data.TANK_CAPACITY), IFluidHandler.FluidAction.SIMULATE);
        long extractedFluid = storage.extract(AEFluidKey.of(fluid), wantedFluid, Actionable.MODULATE, source);
        if (extractedFluid > 0) tank.fill(new FluidStack(fluid, (int) extractedFluid), IFluidHandler.FluidAction.EXECUTE);
        var inv = getTileData().getInventory();
        if (selected == null || !inv.getStackInSlot(0).isEmpty()) return;
        for (var entry : storage.getAvailableStacks()) {
            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0) continue;
            var candidate = key.toStack(1);
            if (hellfirepvp.astralsorcery.common.util.RecipeFinder.of()
                    .flatMap(finder -> finder.findLumenGenerationRecipe(candidate, selected, false)).isEmpty()) continue;
            if (!inv.insertItem(0, candidate, true).isEmpty()) continue;
            if (storage.extract(key, 1, Actionable.MODULATE, source) == 1) {
                inv.insertItem(0, candidate, false);
                break;
            }
        }
    }

    private void exportSurplus() {
        if (!export || mainNode.getGrid() == null) return;
        var storage = mainNode.getGrid().getStorageService().getInventory();
        var handler = getTileData().getLumenHandler();
        var source = IActionSource.ofMachine(this);
        for (var stack : handler.getContainedLumen()) {
            // A single target is both the refill limit and retained reserve, avoiding feedback loops.
            int surplus = stack.getAmount() - pullTarget;
            if (surplus <= 0) continue;
            var available = handler.drain(stack.getLumen(), surplus, ILumenHandler.Action.SIMULATE);
            long inserted = storage.insert(LumenKey.of(stack.getLumen()), available.getAmount(), Actionable.MODULATE, source);
            if (inserted > 0) handler.drain(stack.getLumen(), (int) inserted, ILumenHandler.Action.EXECUTE);
        }
    }

    @Override public void setRemoved() { mainNode.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { mainNode.destroy(); super.onChunkUnloaded(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        var settings = new CompoundTag();
        if (selected != null) settings.putString("lumen", RegistriesAS.REGISTRY_LUMEN.getKey(selected).toString());
        settings.putInt("target", pullTarget);
        settings.putBoolean("export", export);
        settings.putBoolean("items", supplyItems);
        settings.putBoolean("filaments", interactWithFilaments);
        tag.put("meArray", settings);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        if (!tag.contains("meArray")) return;
        var settings = tag.getCompound("meArray");
        var id = ResourceLocation.tryParse(settings.getString("lumen"));
        selected = id == null ? null : RegistriesAS.REGISTRY_LUMEN.get(id);
        if (selected == LumenAS.NONE.get()) selected = null;
        pullTarget = Math.clamp(settings.getInt("target"), 0, Data.LUMEN_TANK_CAPACITY);
        export = settings.getBoolean("export");
        supplyItems = settings.getBoolean("items");
        interactWithFilaments = settings.getBoolean("filaments");
    }
}
