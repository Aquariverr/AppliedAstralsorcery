package com.appliedastralsorcery.starlight;

import java.util.ArrayList;
import java.util.List;

import appeng.api.parts.PartHelper;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.linking.Linkable;
import hellfirepvp.astralsorcery.common.recipe.focal.place.ActiveTransmutationHandler;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionChain;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public final class StarlightP2PTransport {
    // AS packets describe focused beams only; the origin must survive every P2P hop separately.
    enum Kind { FOCAL, FOCUSED }

    private StarlightP2PTransport() {}

    public static boolean isValid(StarlightTransmissionPacket packet) {
        return Float.isFinite(packet.amount()) && packet.amount() > 0;
    }

    static List<StarlightP2PTunnelPart> inputsAt(ServerLevel level, BlockPos pos) {
        List<StarlightP2PTunnelPart> inputs = new ArrayList<>();
        if (isUnloaded(level, pos)) return inputs;
        var host = PartHelper.getPartHost(level, pos);
        if (host != null) {
            for (var side : Direction.values()) {
                if (host.getPart(side) instanceof StarlightP2PTunnelPart tunnel && tunnel.canReceiveStarlight())
                    inputs.add(tunnel);
            }
        }
        return inputs;
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

    static void emit(ServerLevel level, BlockPos pos, Direction side, StarlightTransmissionPacket packet, Kind kind) {
        var target = pos.relative(side);
        if (!isValid(packet) || isUnloaded(level, pos) || isUnloaded(level, target)) return;
        if (kind == Kind.FOCAL) {
            // Transporting a focal point's light does not focus it. In particular, it must
            // neither enter a lens graph nor satisfy recipes that require a focused beam.
            if (receiveAt(level, target, packet, kind)) return;
            if (level.getBlockEntity(target) instanceof FocalStarlightReceiver crystal) {
                crystal.appliedas$receiveFocalStarlight(level, packet);
            } else if (level.getBlockEntity(target) instanceof StarlightTransmutationBlockEntity chamber) {
                chamber.receiveFocalStarlight(level, packet);
            } else {
                ActiveTransmutationHandler.receiveStarlight(level, target, packet.constellation(), packet.amount(), false);
            }
            return;
        }
        // A temporary one-edge source lets AS resolve receivers, lens loss/splitting and block
        // transmutation using its own graph rules. Rebuild so link edits take effect immediately.
        var source = new FocusCrystalSourceNode(pos);
        source.getLinkDataContainer().orElseThrow().link(level, pos, target, Linkable.LinkAction.EXECUTE);
        var chain = StarlightTransmissionChain.makeChain(level, source);
        // Saved AS lens nodes can outlive their loaded chunks. Never send through an unloaded path.
        if (chain.getInvolvedChunks().stream().anyMatch(chunk -> !level.getChunkSource().hasChunk(chunk.x, chunk.z))) return;
        for (var receiver : chain.getReceiverEndpoints()) {
            var multiplier = chain.getInvolvedStepLossMap().get(receiver.getNodePos());
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
