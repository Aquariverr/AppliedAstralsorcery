package com.appliedastralsorcery.gateway;

import java.util.Optional;
import hellfirepvp.astralsorcery.common.data.level.CelestialGatewayData.GatewayEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class GatewaySourceEntry {
    private GatewaySourceEntry() {}

    /** Local source metadata lets native UI creation work without publishing the ME block as a destination. */
    public static Optional<GatewayEntry> resolve(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MECelestialGatewayBlockEntity gateway
                && gateway.hasStructure() && gateway.doesSeeSky()) {
            return Optional.of(new GatewayEntry(pos, Optional.of(gateway.getName()), gateway.getTileData().getColor()));
        }
        return Optional.empty();
    }
}
