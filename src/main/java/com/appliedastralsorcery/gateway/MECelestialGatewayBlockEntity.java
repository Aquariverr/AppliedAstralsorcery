package com.appliedastralsorcery.gateway;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import appeng.api.features.IPlayerRegistry;
import appeng.api.ids.AEComponents;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.tile.TileCelestialGateway;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MECelestialGatewayBlockEntity extends TileCelestialGateway {
    private BlockPos publishedPortal;
    private boolean destinationDirty = true;
    private long nextDestinationUpdate;
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return SpatialCellAccess.accepts(stack); }
        @Override protected void onContentsChanged(int slot) {
            withdrawDestination();
            destinationDirty = true;
            setChanged();
        }
    };

    public MECelestialGatewayBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.GATEWAY_ENTITY), pos, state);
    }
    public ItemStackHandler getInventory() { return inventory; }
    public boolean canUse(Player player) {
        return !player.isSpectator() && (!getTileData().isLocked() || !getTileData().hasOwner() || getTileData().isOwner(player));
    }
    @Override public Component getName() {
        var customName = getCustomName();
        return customName != null ? customName : Component.translatable("block.appliedas.me_celestial_gateway");
    }
    @Override public void serverTick(ServerLevel server) {
        // Keep native tick/structure/sky synchronization, but never advertise the ME block as a destination.
        tick(server);
        var gateways = DataAS.DOMAIN_AS.getData(server, DataAS.KEY_CELESTIAL_GATEWAY_DATA);
        if (gateways.getGateway(worldPosition).isPresent()) gateways.removeGateway(server, worldPosition);
        boolean active = hasStructure() && doesSeeSky();
        var cell = inventory.getStackInSlot(0);
        if (!SpatialCellAccess.accepts(cell)) { withdrawDestination(); return; }
        if (!cell.has(AEComponents.SPATIAL_PLOT_INFO)) {
            int owner = getTileData().hasOwner()
                    ? IPlayerRegistry.getMapping(server.getServer()).getPlayerId(getTileData().getOwnerId()) : -1;
            SpatialCellAccess.beginFormatting(cell, owner);
            setChanged();
        }
        if (SpatialCellAccess.isFormatting(cell)) {
            SpatialCellAccess.formatStep(cell);
            setChanged();
        }
        if (!active || SpatialCellAccess.isFormatting(cell)) { withdrawDestination(); return; }
        if (!destinationDirty && getTileData().getTicksExisted() < nextDestinationUpdate) return;
        destinationDirty = false;
        nextDestinationUpdate = getTileData().getTicksExisted() + 20;
        var plot = SpatialCellAccess.plot(cell);
        if (plot == null) { withdrawDestination(); return; }
        var portal = SpatialReturnPortalBlockEntity.prepare(cell, plot);
        if (portal == null) {
            // A full cell can require scanning the whole plot; do not repeat that work every second.
            nextDestinationUpdate = getTileData().getTicksExisted() + 200;
            withdrawDestination();
            return;
        }
        if (publishedPortal != null && !publishedPortal.equals(portal.getBlockPos())) withdrawDestination();
        publishedPortal = portal.getBlockPos();
        portal.bind(this, plot.getId(), cell.getHoverName());
        portal.serverTick((ServerLevel) portal.getLevel());
        setRenderActive(true);
        setChanged();
    }
    private void setRenderActive(boolean active) {
        if (!(level instanceof ServerLevel server) || isRemoved() || server.getBlockEntity(worldPosition) != this) return;
        var state = getBlockState();
        if (state.getValue(MECelestialGatewayBlock.ACTIVE) != active)
            server.setBlock(worldPosition, state.setValue(MECelestialGatewayBlock.ACTIVE, active),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    public void withdrawDestination() {
        setRenderActive(false);
        if (publishedPortal != null && level instanceof ServerLevel server) {
            var storage = server.getServer().getLevel(appeng.spatial.SpatialStorageDimensionIds.WORLD_ID);
            if (storage != null && storage.getBlockEntity(publishedPortal) instanceof SpatialReturnPortalBlockEntity portal
                    && portal.isBoundTo(GlobalPos.of(server.dimension(), worldPosition))) portal.withdrawDestination();
            publishedPortal = null;
        }
    }
    @Override public void onTileEntityRemove(net.minecraft.world.level.Level world, BlockPos pos) {
        withdrawDestination();
        super.onTileEntityRemove(world, pos);
    }
    static boolean message(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.appliedas.gateway." + key), true);
        return false;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("spatialCell", inventory.serializeNBT(registries));
        if (publishedPortal != null) tag.putLong("spatialDestination", publishedPortal.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("spatialCell"));
        publishedPortal = tag.contains("spatialDestination") ? BlockPos.of(tag.getLong("spatialDestination")) : null;
        destinationDirty = true;
    }
}
