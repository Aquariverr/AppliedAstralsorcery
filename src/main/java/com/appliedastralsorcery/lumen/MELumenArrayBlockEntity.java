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
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
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

public class MELumenArrayBlockEntity extends TileLumenArray implements IGridConnectedBlockEntity {
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(Set.of(Direction.DOWN))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);
    private Lumen selected;
    private int pullTarget = 2000;
    private boolean export = true;
    private boolean supplyItems = true;
    private boolean interactWithFilaments = false;
    private boolean redstoneControl;
    // Production-only overflow, retained if ME storage changes after the preflight check.
    private LumenStack pendingOutput = LumenStack.EMPTY;
    // Native recipes run once per second, including starlight-consuming cycles at full lumen capacity.
    private long glowWorkingUntil = Long.MIN_VALUE;

    public MELumenArrayBlockEntity(BlockPos pos, BlockState state) {
        this(new TileRegistryObject<>(ModContent.ARRAY_ENTITY), pos, state);
    }

    protected MELumenArrayBlockEntity(TileRegistryObject<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isAlchemyArray() { return false; }

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
    public boolean isRedstoneControlEnabled() { return redstoneControl; }
    public boolean isWorkAllowed() {
        return !redstoneControl || level != null && level.hasNeighborSignal(worldPosition);
    }
    public void setRedstoneControlEnabled(boolean value) {
        redstoneControl = value;
        updateEnabledState();
        setChanged();
    }
    private void updateEnabledState() {
        if (!(level instanceof ServerLevel server)) return;
        var state = getBlockState();
        boolean enabled = isWorkAllowed();
        if (state.getValue(MELumenArrayBlock.ENABLED) != enabled)
            server.setBlock(worldPosition, state.setValue(MELumenArrayBlock.ENABLED, enabled),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    public int getStoredAmount() {
        return getTileData().getLumenHandler().getContainedLumen().stream().mapToInt(LumenStack::getAmount).sum();
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
        if (!isSwitchPending() || getStoredAmount() != 0 || !pendingOutput.isEmpty() || !getTileData().getInventory().getStackInSlot(0).isEmpty()
                || hasPendingIngredients()) return;
        // With an empty slot this only clears AS's cached recipe and request chains, without breaking any item.
        breakCatalyst();
        arrayData().bindEmptyArray(selected);
        if (level instanceof ServerLevel server) {
            LumenNetworkHelper.getNode(server, worldPosition).ifPresent(node ->
                    LumenNetworkHelper.setNodeProvidedLumenTypes(server, node, Set.of(selected)));
        }
    }

    protected boolean hasPendingIngredients() { return false; }

    protected void returnPreviousContents() {
        var grid = mainNode.getGrid();
        if (grid == null) return;
        exportPendingOutput();
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
            mainNode.setVisualRepresentation(isAlchemyArray() ? ModContent.ME_LUMEN_ALCHEMY_ARRAY_ITEM.get()
                    : ModContent.ME_LUMEN_ARRAY_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        // Redstone gates only the crafting hook; ME refills, exports and type changes keep running.
        updateEnabledState();
        if (isSwitchPending()) {
            finishSwitchIfEmpty();
            if (isSwitchPending() && server.getGameTime() % 20 == 0 && mainNode.isOnline()) returnPreviousContents();
            // Pause production and refills until old contents have been safely stored; the old source remains drainable.
            if (isSwitchPending()) {
                updateGlowState(server);
                return;
            }
        }
        if (server.getGameTime() % 20 == 0 && mainNode.isOnline()) supplyFromNetwork();
        int starlightBeforeCycle = getTileData().getContainedFluid().getAmount();
        super.serverTick(server);
        // Full tanks and zero random generation attempts can still run a recipe cycle without producing lumen.
        // Sample after ME refills so only native/alchemy recipe consumption extends the working animation.
        if (getTileData().getContainedFluid().getAmount() < starlightBeforeCycle)
            glowWorkingUntil = server.getGameTime() + 20;
        if (server.getGameTime() % 20 == 0 && mainNode.isOnline()) exportSurplus();
        updateGlowState(server);
    }

    private void updateGlowState(ServerLevel server) {
        if (!isWorkAllowed() || isSwitchPending() || getTileData().getInventory().getStackInSlot(0).isEmpty()
                || getTileData().getContainedFluid().isEmpty()) glowWorkingUntil = Long.MIN_VALUE;
        boolean working = server.getGameTime() < glowWorkingUntil;
        var state = getBlockState();
        var next = state.setValue(MELumenArrayBlock.LIT, getStoredAmount() > 0 || !pendingOutput.isEmpty())
                .setValue(MELumenArrayBlock.WORKING, working);
        if (state != next) server.setBlock(worldPosition, next, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    private int trackGeneratedLumen(int amount, ILumenHandler.Action action) {
        if (amount > 0 && action == ILumenHandler.Action.EXECUTE && level != null && !level.isClientSide())
            glowWorkingUntil = level.getGameTime() + 20;
        return amount;
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
                    .flatMap(finder -> finder.findLumenGenerationRecipe(candidate, selected, isAlchemyArray())).isEmpty()) continue;
            if (!inv.insertItem(0, candidate, true).isEmpty()) continue;
            if (storage.extract(key, 1, Actionable.MODULATE, source) == 1) {
                inv.insertItem(0, candidate, false);
                break;
            }
        }
    }

    /** Native and alchemy production may send overflow to ME without consuming the retained reserve. */
    public int fillGeneratedLumen(LumenStack stack, ILumenHandler.Action action) {
        if (stack.isEmpty() || isSwitchPending()
                || (getActiveLumen() != null && getActiveLumen() != stack.getLumen())) return 0;
        int local = getTileData().getLumenHandler().fill(stack, action);
        var grid = mainNode.getGrid();
        if (local == stack.getAmount() || !export || !mainNode.isOnline() || grid == null
                || (!pendingOutput.isEmpty() && pendingOutput.getLumen() != stack.getLumen())) return trackGeneratedLumen(local, action);
        int overflow = Math.min(stack.getAmount() - local, Data.LUMEN_TANK_CAPACITY - pendingOutput.getAmount());
        if (action == ILumenHandler.Action.SIMULATE) {
            overflow = (int) grid.getStorageService().getInventory().insert(LumenKey.of(stack.getLumen()),
                    overflow, Actionable.SIMULATE, IActionSource.ofMachine(this));
        } else if (overflow > 0) {
            // Do not recheck storage after ingredients were consumed: buffer the promised output safely.
            pendingOutput = stack.getLumen().stack(pendingOutput.getAmount() + overflow);
            setChanged();
        }
        return trackGeneratedLumen(local + overflow, action);
    }

    private void exportPendingOutput() {
        if (pendingOutput.isEmpty() || mainNode.getGrid() == null) return;
        int inserted = (int) mainNode.getGrid().getStorageService().getInventory().insert(
                LumenKey.of(pendingOutput.getLumen()), pendingOutput.getAmount(), Actionable.MODULATE,
                IActionSource.ofMachine(this));
        if (inserted > 0) {
            pendingOutput = pendingOutput.getLumen().stack(pendingOutput.getAmount() - inserted);
            setChanged();
        }
    }

    private void exportSurplus() {
        if (!export || mainNode.getGrid() == null) return;
        exportPendingOutput();
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
        if (selected != null) settings.putString("lumen", selected.getRegistryKey().orElseThrow().location().toString());
        settings.putInt("target", pullTarget);
        settings.putBoolean("export", export);
        settings.putBoolean("items", supplyItems);
        settings.putBoolean("filaments", interactWithFilaments);
        settings.putBoolean("redstoneControl", redstoneControl);
        if (!pendingOutput.isEmpty()) {
            settings.putString("outputLumen", pendingOutput.getLumen().getRegistryKey().orElseThrow().location().toString());
            settings.putInt("outputAmount", pendingOutput.getAmount());
        }
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
        redstoneControl = settings.getBoolean("redstoneControl");
        var outputId = ResourceLocation.tryParse(settings.getString("outputLumen"));
        var outputType = outputId == null ? null : RegistriesAS.REGISTRY_LUMEN.get(outputId);
        pendingOutput = outputType == null || outputType == LumenAS.NONE.get() ? LumenStack.EMPTY
                : outputType.stack(Math.clamp(settings.getInt("outputAmount"), 0, Data.LUMEN_TANK_CAPACITY));
    }
}
