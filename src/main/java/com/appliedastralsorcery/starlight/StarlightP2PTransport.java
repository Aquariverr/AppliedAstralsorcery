package com.appliedastralsorcery.starlight;

import java.util.ArrayList;
import java.util.List;

import appeng.api.parts.PartHelper;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.recipe.focal.place.ActiveTransmutationHandler;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public final class StarlightP2PTransport {
    private static final Direction[] SIDES = Direction.values();

    // AS packets describe focused beams only; the origin must survive every P2P hop separately.
    enum Kind { FOCAL, FOCUSED }

    private StarlightP2PTransport() {}

    public static boolean isValid(StarlightTransmissionPacket packet) {
        return Float.isFinite(packet.amount()) && packet.amount() > 0;
    }

    static List<StarlightP2PTunnelPart> inputsAt(ServerLevel level, BlockPos pos) {
        if (isUnloaded(level, pos)) return List.of();
        var host = PartHelper.getPartHost(level, pos);
        if (host == null) return List.of();
        List<StarlightP2PTunnelPart> inputs = null;
        for (var side : SIDES) {
            if (host.getPart(side) instanceof StarlightP2PTunnelPart tunnel && tunnel.canReceiveStarlight()) {
                if (inputs == null) inputs = new ArrayList<>();
                inputs.add(tunnel);
            }
        }
        return inputs == null ? List.of() : inputs;
    }

    static int countInputsAt(ServerLevel level, BlockPos pos) {
        if (isUnloaded(level, pos)) return 0;
        var host = PartHelper.getPartHost(level, pos);
        if (host == null) return 0;
        int count = 0;
        for (var side : SIDES) {
            if (host.getPart(side) instanceof StarlightP2PTunnelPart tunnel && tunnel.canReceiveStarlight()) count++;
        }
        return count;
    }

    public static boolean receiveAt(ServerLevel level, BlockPos pos, StarlightTransmissionPacket packet) {
        return receiveAt(level, pos, packet, Kind.FOCUSED);
    }

    private static boolean receiveAt(ServerLevel level, BlockPos pos, StarlightTransmissionPacket packet, Kind kind) {
        var inputs = inputsAt(level, pos);
        if (inputs.isEmpty()) return false;
        if (isValid(packet)) {
            var share = packet.withMultiplier(1.0F / inputs.size());
            for (var input : inputs) input.receiveStarlight(share, kind);
        }
        return true;
    }

    static void emit(ServerLevel level, BlockPos pos, Direction side, StarlightTransmissionPacket packet, Kind kind,
            StarlightRouteCache routes) {
        var target = pos.relative(side);
        if (!isValid(packet) || isUnloaded(level, pos) || isUnloaded(level, target)) return;
        if (kind == Kind.FOCAL) {
            // Transporting a focal point's light does not focus it. In particular, it must
            // neither enter a lens graph nor satisfy recipes that require a focused beam.
            if (receiveAt(level, target, packet, kind)) return;
            var receiver = level.getBlockEntity(target);
            if (receiver instanceof FocalStarlightReceiver crystal) {
                crystal.appliedas$receiveFocalStarlight(level, packet);
            } else if (receiver instanceof StarlightTransmutationBlockEntity chamber) {
                chamber.receiveFocalStarlight(level, packet);
            } else {
                ActiveTransmutationHandler.receiveStarlight(level, target, packet.constellation(), packet.amount(), false);
            }
            return;
        }
        var chain = routes.get(level, pos, side);
        // Saved AS lens nodes can outlive their loaded chunks. Never send through an unloaded path.
        for (var chunk : chain.getInvolvedChunks()) {
            if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) return;
        }
        var losses = chain.getInvolvedStepLossMap();
        for (var receiver : chain.getReceiverEndpoints()) {
            var multiplier = losses.get(receiver.getNodePos());
            if (multiplier != null && multiplier > 0)
                receiver.receiveStarlight(level, packet.withMultiplier(multiplier));
        }
        if (packet.amount() > 0.001F) {
            chain.getTransmissionNotifierMap().forEach((notifier, multiplier) -> {
                if (multiplier > 0.001F) notifier.updateTransmission(level, packet.withMultiplier(multiplier));
            });
        }
        chain.getBlockEndpoints().forEach((endpoint, multiplier) -> {
            if (multiplier > 0.001F)
                ActiveTransmutationHandler.receiveStarlight(level, endpoint, packet.withMultiplier(multiplier));
        });
    }

    private static boolean isUnloaded(ServerLevel level, BlockPos pos) {
        return !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
