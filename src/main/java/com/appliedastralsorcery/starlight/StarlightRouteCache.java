package com.appliedastralsorcery.starlight;

import java.util.Collection;

import hellfirepvp.astralsorcery.common.linking.BlockLinkConnection;
import hellfirepvp.astralsorcery.common.linking.Linkable;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionChain;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** One output's resolved route. Validate live graph inputs instead of relying on optional change notifications. */
public final class StarlightRouteCache {
    private ServerLevel level;
    private BlockPos origin;
    private Direction side;
    private StarlightTransmissionChain chain;
    private NodeState[] nodes;

    public void clear() {
        level = null;
        origin = null;
        side = null;
        chain = null;
        nodes = null;
    }

    public StarlightTransmissionChain get(ServerLevel currentLevel, BlockPos currentOrigin, Direction currentSide) {
        var network = StarlightNetworkLevelHelper.get(currentLevel);
        if (chain != null && level == currentLevel && currentOrigin.equals(origin) && side == currentSide
                && unchanged(network)) return chain;

        // The synthetic source is never registered or saved in AS's world network.
        var source = new FocusCrystalSourceNode(currentOrigin);
        source.getLinkDataContainer().orElseThrow().link(currentLevel, currentOrigin,
                currentOrigin.relative(currentSide), Linkable.LinkAction.EXECUTE);
        var rebuilt = StarlightTransmissionChain.makeChain(currentLevel, source);
        var positions = rebuilt.getInvolvedStepLossMap().keySet();
        var snapshots = new NodeState[positions.size()];
        int index = 0;
        for (var pos : positions) snapshots[index++] = NodeState.capture(pos, network.getNode(pos).orElse(null));
        level = currentLevel;
        origin = currentOrigin.immutable();
        side = currentSide;
        nodes = snapshots;
        chain = rebuilt;
        return rebuilt;
    }

    private boolean unchanged(StarlightNetworkLevelHelper network) {
        for (var state : nodes) {
            // Include empty endpoints so adding a new receiver/lens invalidates a formerly plain block.
            if (network.getNode(state.pos()).orElse(null) != state.node() || !state.unchanged()) return false;
        }
        return true;
    }

    private record EdgeState(BlockLinkConnection connection, BlockPos target, boolean connected) {
        boolean matches(BlockLinkConnection current) {
            return current == connection && current.getTo().equals(target) && current.canConnect() == connected;
        }
    }

    private record NodeState(BlockPos pos, TransmissionNode node, float loss, EdgeState[] links) {
        static NodeState capture(BlockPos pos, TransmissionNode node) {
            if (node == null) return new NodeState(pos.immutable(), null, 0, null);
            EdgeState[] edges = null;
            if (node instanceof Linkable linkable) {
                Collection<BlockLinkConnection> connections = linkable.getLinkedPositions();
                edges = new EdgeState[connections.size()];
                int index = 0;
                for (var connection : connections) edges[index++] = new EdgeState(connection,
                        connection.getTo().immutable(), connection.canConnect());
            }
            return new NodeState(pos.immutable(), node, node.getTransmissionLossMultiplier(), edges);
        }

        boolean unchanged() {
            if (node == null) return true;
            if (Float.compare(loss, node.getTransmissionLossMultiplier()) != 0) return false;
            if (links != null) {
                var connections = ((Linkable) node).getLinkedPositions();
                if (connections.size() != links.length) return false;
                int index = 0;
                // Order matters to floating-point accumulation in AS's original chain traversal.
                for (var connection : connections) if (!links[index++].matches(connection)) return false;
            }
            return true;
        }
    }
}
