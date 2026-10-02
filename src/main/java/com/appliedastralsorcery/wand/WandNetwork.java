package com.appliedastralsorcery.wand;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class WandNetwork {
    private WandNetwork() {}

    @SuppressWarnings("resource") // Minecraft manages the player's world lifetime.
    static IGrid findGrid(ServerPlayer player, ItemStack wand) {
        var target = wand.get(AEComponents.WIRELESS_LINK_TARGET);
        if (target == null) {
            MEResonatingWandItem.message(player, "unlinked");
            return null;
        }
        var level = player.server.getLevel(target.dimension());
        // Never load chunks or dimensions just to resolve a wireless link.
        if (level == null || !level.isLoaded(target.pos())
                || !(level.getBlockEntity(target.pos()) instanceof IWirelessAccessPoint accessPoint)
                || accessPoint.getGrid() == null) {
            MEResonatingWandItem.message(player, "unavailable");
            return null;
        }
        IGrid grid = accessPoint.getGrid();
        for (var candidate : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            var location = candidate.getLocation();
            if (location.getLevel() == player.level() && candidate.isActive()
                    && player.distanceToSqr(location.getPos().getX(), location.getPos().getY(), location.getPos().getZ())
                            < candidate.getRange() * candidate.getRange()) {
                return grid;
            }
        }
        MEResonatingWandItem.message(player, "out_of_range");
        return null;
    }
}
