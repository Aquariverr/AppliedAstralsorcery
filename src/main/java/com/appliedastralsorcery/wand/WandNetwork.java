package com.appliedastralsorcery.wand;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class WandNetwork {
    private static final ResourceLocation INFINITY_CARD = ResourceLocation.fromNamespaceAndPath(
            "aeinfinitybooster", "infinity_card");
    private static final ResourceLocation DIMENSION_CARD = ResourceLocation.fromNamespaceAndPath(
            "aeinfinitybooster", "dimension_card");

    private WandNetwork() {}

    @SuppressWarnings("resource")
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
            if (!candidate.isActive()) continue;
            // AEInfinityBooster patches AE2's terminal menu, not the access point's getRange().
            // Resolve by item ID so the booster mod remains optional.
            var booster = candidate.getInternalInventory().getStackInSlot(0);
            var boosterId = BuiltInRegistries.ITEM.getKey(booster.getItem());
            if (DIMENSION_CARD.equals(boosterId)) return grid;
            var location = candidate.getLocation();
            if (location.getLevel() == player.level()
                    && (INFINITY_CARD.equals(boosterId)
                            || player.distanceToSqr(location.getPos().getX(), location.getPos().getY(), location.getPos().getZ())
                                    < candidate.getRange() * candidate.getRange())) {
                return grid;
            }
        }
        MEResonatingWandItem.message(player, "out_of_range");
        return null;
    }
}
