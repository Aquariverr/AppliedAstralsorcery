package com.appliedastralsorcery.gateway;

import java.util.Objects;
import appeng.api.ids.AEComponents;
import appeng.spatial.SpatialStoragePlot;
import appeng.spatial.SpatialStoragePlotManager;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.tile.TileCelestialGateway;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import hellfirepvp.astralsorcery.common.util.data.ObserverRegistryObject;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SpatialReturnPortalBlockEntity extends TileCelestialGateway {
    private static final String PORTAL = "appliedas_return_portal";
    private GlobalPos returnGateway;
    private int plotId = -1;
    public SpatialReturnPortalBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.RETURN_PORTAL_ENTITY), pos, state);
    }

    public boolean isBoundTo(GlobalPos gateway) { return gateway.equals(returnGateway); }

    // The gateway owns its Level; binding only reads its dimension.
    @SuppressWarnings("resource")
    public void bind(MECelestialGatewayBlockEntity gateway, int plotId, Component cellName) {
        var gatewayLevel = gateway.getLevel();
        if (gatewayLevel == null) return;
        var source = GlobalPos.of(gatewayLevel.dimension(), gateway.getBlockPos());
        boolean changed = !source.equals(returnGateway) || this.plotId != plotId;
        returnGateway = source;
        this.plotId = plotId;
        if (!Objects.equals(cellName, getCustomName())) {
            getTileData().setCustomName(cellName.copy());
            changed = true;
        }
        if (getTileData().getColor() != gateway.getTileData().getColor()) {
            getTileData().setColor(gateway.getTileData().getColor());
            changed = true;
        }
        if (changed) { refreshGatewayRegistration(); markForUpdate(); }
    }

    private MECelestialGatewayBlockEntity activeGateway() {
        if (!(level instanceof ServerLevel server) || returnGateway == null || plotId < 0) return null;
        var source = server.getServer().getLevel(returnGateway.dimension());
        if (source == null || !(source.getBlockEntity(returnGateway.pos()) instanceof MECelestialGatewayBlockEntity gate)) return null;
        var cell = gate.getInventory().getStackInSlot(0);
        var info = cell.get(AEComponents.SPATIAL_PLOT_INFO);
        if (info == null || info.id() != plotId || !SpatialCellAccess.accepts(cell) || SpatialCellAccess.isFormatting(cell)
                || !cell.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(PORTAL)
                || cell.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(PORTAL) != worldPosition.asLong()
                || SpatialCellAccess.plot(cell) == null || !gate.hasStructure() || !gate.doesSeeSky()) return null;
        return gate;
    }

    @Override public boolean hasStructure() { return activeGateway() != null; }
    @Override public boolean doesSeeSky() { return true; }
    @Override public ObserverRegistryObject getRequiredObserver() { return null; }
    @Override public void clientTick(net.minecraft.world.level.Level world) { /* Small pedestal particles are supplied by the block. */ }
    @Override public void serverTick(ServerLevel server) {
        if (hasStructure()) super.serverTick(server);
        else { tick(server); withdrawDestination(); }
    }
    public void withdrawDestination() {
        if (level instanceof ServerLevel server) {
            var gateways = DataAS.DOMAIN_AS.getData(server, DataAS.KEY_CELESTIAL_GATEWAY_DATA);
            if (gateways.getGateway(worldPosition).isPresent()) gateways.removeGateway(server, worldPosition);
        }
        refreshGatewayRegistration();
    }

    public static SpatialReturnPortalBlockEntity prepare(ItemStack cell, SpatialStoragePlot plot) {
        var level = SpatialStoragePlotManager.INSTANCE.getLevel();
        var tag = cell.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(PORTAL)) {
            var pos = BlockPos.of(tag.getLong(PORTAL));
            if (inside(plot, pos) && level.getBlockEntity(pos) instanceof SpatialReturnPortalBlockEntity portal
                    && arrival(level, pos) != null) return portal;
        }
        var origin = plot.getOrigin();
        var size = plot.getSize();
        // Search the existing interior without deleting blocks, inventories, fluids or ceilings.
        // Low layers are preferred so an empty cell's matrix shell provides the floor.
        for (int y = 0; y < size.getY() - 2; y++) {
            for (int z = 1; z < size.getZ() - 1; z++) for (int x = 1; x < size.getX() - 1; x++) {
                var pos = origin.offset(x, y, z);
                if (level.getBlockEntity(pos) instanceof SpatialReturnPortalBlockEntity portal && arrival(level, pos) != null) {
                    remember(cell, pos);
                    return portal;
                }
                if (!clear(level, pos) || !clear(level, pos.above()) || !clear(level, pos.above(2))) continue;
                if (!Block.canSupportRigidBlock(level, pos.below())) {
                    // Only add a floor in empty space. Existing non-supporting blocks are left alone.
                    if (!inside(plot, pos.below()) || !clear(level, pos.below())) continue;
                    if (!level.setBlockAndUpdate(pos.below(), BlocksAS.MARBLE_RAW.get().defaultBlockState())) continue;
                }
                if (!level.setBlockAndUpdate(pos, ModContent.SPATIAL_RETURN_PORTAL.get().defaultBlockState())) continue;
                if (level.getBlockEntity(pos) instanceof SpatialReturnPortalBlockEntity portal) {
                    remember(cell, pos);
                    return portal;
                }
            }
        }
        return null;
    }
    private static void remember(ItemStack cell, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, cell, tag -> tag.putLong(PORTAL, pos.asLong()));
    }
    private static boolean inside(SpatialStoragePlot plot, BlockPos pos) {
        var offset = pos.subtract(plot.getOrigin());
        return offset.getX() >= 0 && offset.getY() >= 0 && offset.getZ() >= 0
                && offset.getX() < plot.getSize().getX() && offset.getY() < plot.getSize().getY() && offset.getZ() < plot.getSize().getZ();
    }
    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty();
    }
    private static Vec3 arrival(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || !clear(level, pos.above()) || !clear(level, pos.above(2))
                || !Block.canSupportRigidBlock(level, pos.below())) return null;
        var block = level.getBlockState(pos).getBlock();
        if (block instanceof MECelestialGatewayBlock || block instanceof SpatialReturnPortalBlock)
            return Vec3.atBottomCenterOf(pos).add(0, 1.0 / 16, 0);
        return clear(level, pos) ? Vec3.atBottomCenterOf(pos) : null;
    }
    public boolean canReceive(ServerPlayer player) {
        if (!(level instanceof ServerLevel target)) return false;
        var gateway = activeGateway();
        if (gateway == null || !gateway.canUse(player)) return false;
        var destination = arrival(target, worldPosition);
        if (destination == null) return MECelestialGatewayBlockEntity.message(player, "blocked");
        return true;
    }
    // Player and server levels are borrowed and must not be closed here.
    @SuppressWarnings("resource")
    public void returnPlayer(ServerPlayer player) {
        if (player.isSpectator() || player.serverLevel() != level || player.distanceToSqr(worldPosition.getCenter()) > 64) return;
        var source = returnGateway;
        if (source == null) { MECelestialGatewayBlockEntity.message(player, "no_return"); return; }
        var target = player.serverLevel().getServer().getLevel(source.dimension());
        if (target == null) { MECelestialGatewayBlockEntity.message(player, "no_return"); return; }
        // Loading the entrance also allows return after its chunk unloaded. The saved return survives cell removal.
        target.getChunkAt(source.pos());
        var destination = arrival(target, source.pos());
        if (destination == null) {
            for (var pos : BlockPos.withinManhattan(source.pos(), 8, 4, 8)) {
                destination = arrival(target, pos);
                if (destination != null) break;
            }
        }
        if (destination == null) { MECelestialGatewayBlockEntity.message(player, "blocked"); return; }
        transfer(player, target, destination);
    }
    @SuppressWarnings("resource") // Minecraft manages the player's world lifetime.
    private static void transfer(ServerPlayer player, ServerLevel target, Vec3 pos) {
        player.closeContainer();
        if (player.serverLevel() == target) {
            if (!player.teleportTo(target, pos.x, pos.y, pos.z, java.util.Set.of(), player.getYRot(), player.getXRot())) return;
        } else {
            player.stopRiding();
            if (player.isSleeping()) player.stopSleepInBed(true, true);
            // Unlike teleportTo's boolean overload, changeDimension reports a cancelled travel event.
            var transition = new net.minecraft.world.level.portal.DimensionTransition(target, pos, Vec3.ZERO,
                    player.getYRot(), player.getXRot(), net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
            if (player.changeDimension(transition) == null) return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (returnGateway != null) tag.put("returnGateway", writePos(returnGateway));
        tag.putInt("plotId", plotId);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Existing return pedestals predate the native gateway data codec.
        if (!tag.contains("saveData")) { tag = tag.copy(); tag.put("saveData", new CompoundTag()); }
        super.loadAdditional(tag, registries);
        returnGateway = readPos(tag.getCompound(tag.contains("returnGateway") ? "returnGateway" : "lastEntrance"));
        plotId = tag.contains("plotId") ? tag.getInt("plotId") : -1;
        refreshGatewayRegistration();
    }
    private static CompoundTag writePos(GlobalPos pos) {
        var tag = new CompoundTag();
        tag.putString("dimension", pos.dimension().location().toString());
        tag.putLong("pos", pos.pos().asLong());
        return tag;
    }
    private static GlobalPos readPos(CompoundTag tag) {
        var id = ResourceLocation.tryParse(tag.getString("dimension"));
        return id == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong("pos")));
    }
}
