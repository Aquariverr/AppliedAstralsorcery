package com.appliedastralsorcery.chalice;

import java.util.Set;
import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Keeps the original chalice tank and all its native interactions. */
public final class MEChaliceBlockEntity extends TileChalice implements IInWorldGridNodeHost, IActionHost {
    public static final int CAPACITY = 64_000;
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (IGridNodeListener<MEChaliceBlockEntity>) (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(Set.of(Direction.UP, Direction.DOWN))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);
    private AEFluidKey selected;
    private int pullTarget = CAPACITY / 2;
    private boolean pull = true;
    private boolean export = true;

    public MEChaliceBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.CHALICE_ENTITY), pos, state);
    }

    public IManagedGridNode getMainNode() { return mainNode; }
    @Override public IGridNode getGridNode(Direction side) {
        return side == Direction.UP || side == Direction.DOWN ? mainNode.getNode() : null;
    }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public Fluid getSelectedFluid() { return selected == null ? null : selected.getFluid(); }
    public AEFluidKey getSelectedKey() { return selected; }
    public int getPullTarget() { return pullTarget; }
    public int getStoredAmount() { return getContainedFluid().getAmount(); }
    public boolean isPullEnabled() { return pull; }
    public boolean isExportEnabled() { return export; }
    public boolean isSwitchPending() {
        return selected != null && !getContainedFluid().isEmpty()
                && !selected.matches(getContainedFluid());
    }
    public void setSelectedFluid(Fluid fluid) {
        setSelectedKey(fluid == null || fluid == Fluids.EMPTY ? null : AEFluidKey.of(fluid));
    }
    public void setSelectedKey(AEFluidKey key) {
        selected = key;
        setChanged();
    }
    public void setPullTarget(int amount) { pullTarget = Math.clamp(amount, 0, CAPACITY); setChanged(); }
    public void setPullEnabled(boolean enabled) { pull = enabled; setChanged(); }
    public void setExportEnabled(boolean enabled) { export = enabled; setChanged(); }

    @Override public void serverTick(ServerLevel server) {
        if (!mainNode.isReady()) {
            mainNode.setVisualRepresentation(ModContent.ME_CHALICE_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        updateConnectedBases(server);
        // The original lightwell draw, reactions, redstone control, lighting and timers still run offline.
        super.serverTick(server);
        if (server.getGameTime() % 20 == 0 && mainNode.isOnline()) transferWithNetwork();
    }

    private void updateConnectedBases(ServerLevel server) {
        var node = mainNode.getNode();
        var sides = node == null ? Set.<Direction>of() : node.getConnectedSides();
        var state = getBlockState();
        var connected = state.setValue(MEChaliceBlock.TOP_CONNECTED, sides.contains(Direction.UP))
                .setValue(MEChaliceBlock.BOTTOM_CONNECTED, sides.contains(Direction.DOWN));
        // Physical connections, independent of power/channels. Block states also sync the model to clients.
        if (state != connected) server.setBlock(worldPosition, connected, Block.UPDATE_CLIENTS);
    }

    private void transferWithNetwork() {
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        var tank = getTankView();
        var contained = getContainedFluid();
        if (!contained.isEmpty() && export) {
            // Unselected/previous fluids are fully returned. The selected fluid retains one shared
            // reserve for both directions, so importing and exporting never fight one another.
            int reserve = selected != null && !isSwitchPending() ? pullTarget : 0;
            int surplus = contained.getAmount() - reserve;
            if (surplus > 0) {
                var available = tank.drain(surplus, FluidAction.SIMULATE);
                if (!available.isEmpty()) {
                    long inserted = storage.insert(AEFluidKey.of(available), available.getAmount(), Actionable.MODULATE, source);
                    if (inserted > 0) tank.drain(available.copyWithAmount((int) inserted), FluidAction.EXECUTE);
                }
            }
        }
        if (!pull || selected == null || isSwitchPending()) return;
        int missing = pullTarget - getStoredAmount();
        if (missing <= 0) return;
        int accepted = tank.fill(selected.toStack(missing), FluidAction.SIMULATE);
        if (accepted <= 0) return;
        long extracted = storage.extract(selected, accepted, Actionable.MODULATE, source);
        if (extracted > 0) tank.fill(selected.toStack((int) extracted), FluidAction.EXECUTE);
    }

    @Override public void setRemoved() { mainNode.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { mainNode.destroy(); super.onChunkUnloaded(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        var settings = new CompoundTag();
        if (selected != null) settings.put("fluidKey", selected.toTag(registries));
        settings.putInt("target", pullTarget);
        settings.putBoolean("pull", pull);
        settings.putBoolean("export", export);
        tag.put("meChalice", settings);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        if (!tag.contains("meChalice")) return;
        var settings = tag.getCompound("meChalice");
        var id = ResourceLocation.tryParse(settings.getString("fluid"));
        var legacyFluid = id == null ? null : BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        selected = settings.contains("fluidKey") ? AEFluidKey.fromTag(registries, settings.getCompound("fluidKey"))
                : legacyFluid == null || legacyFluid == Fluids.EMPTY ? null : AEFluidKey.of(legacyFluid);
        pullTarget = Math.clamp(settings.getInt("target"), 0, CAPACITY);
        pull = settings.getBoolean("pull");
        export = settings.getBoolean("export");
    }
}
