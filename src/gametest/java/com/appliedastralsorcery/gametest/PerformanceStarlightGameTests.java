package com.appliedastralsorcery.gametest;

import com.appliedastralsorcery.starlight.StarlightRouteCache;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.linking.Linkable;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionChain;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import hellfirepvp.astralsorcery.common.tile.network.ForwardingStarlightReceiverNode;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceStarlightGameTests {
    @GameTest(template = "attunement_test")
    public static void routeCacheTracksDirectGraphChangesWithoutNotifications(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_STARLIGHT_NETWORK_DATA);
        var origin = helper.absolutePos(new BlockPos(5, 3, 5));
        var target = origin.east();
        var first = new ForwardingStarlightReceiverNode(origin.east(4));
        var second = new ForwardingStarlightReceiverNode(origin.south(4));
        var lens = new SimpleTransmissionNode(target);
        var cache = new StarlightRouteCache();
        try {
            var empty = cache.get(level, origin, Direction.EAST);
            helper.assertTrue(empty.getBlockEndpoints().containsKey(target), "Initially the route must end at a plain block");
            data.addTransmissionNode(lens);
            data.addTransmissionNode(first);
            data.addTransmissionNode(second);
            var links = lens.getLinkDataContainer().orElseThrow();
            links.link(level, target, first.getNodePos(), Linkable.LinkAction.EXECUTE);
            var initial = cache.get(level, origin, Direction.EAST);
            helper.assertTrue(initial != empty && initial.getReceiverEndpoints().contains(first),
                    "Adding a lens/receiver at an empty endpoint must invalidate the route");
            for (int i = 0; i < 256; i++) {
                helper.assertTrue(cache.get(level, origin, Direction.EAST) == initial,
                        "An unchanged route must be reused across repeated packets");
            }
            lens.setLossMultiplier(0.25F);
            var lossy = cache.get(level, origin, Direction.EAST);
            helper.assertTrue(lossy != initial && lossy.getInvolvedStepLossMap().get(first.getNodePos()) == 0.25F,
                    "Live loss changes must immediately invalidate cached multipliers");
            var connection = links.getLinkedTo().iterator().next();
            connection.setCanConnect(false);
            helper.assertTrue(cache.get(level, origin, Direction.EAST).getReceiverEndpoints().isEmpty(),
                    "A blocked connection must stop transmission immediately");
            connection.setCanConnect(true);
            helper.assertTrue(cache.get(level, origin, Direction.EAST).getReceiverEndpoints().contains(first),
                    "Unblocking a connection must restore transmission immediately");
            links.unlink(level, target, first.getNodePos(), Linkable.LinkAction.EXECUTE);
            links.link(level, target, second.getNodePos(), Linkable.LinkAction.EXECUTE);
            var relinked = cache.get(level, origin, Direction.EAST);
            helper.assertTrue(!relinked.getReceiverEndpoints().contains(first)
                            && relinked.getReceiverEndpoints().contains(second),
                    "Direct relinking without a change event must not use the old receiver");
            data.removeTransmissionNode(second.getNodePos());
            var replacement = new ForwardingStarlightReceiverNode(second.getNodePos());
            data.addTransmissionNode(replacement);
            helper.assertTrue(cache.get(level, origin, Direction.EAST).getReceiverEndpoints().contains(replacement),
                    "Replacing a node at the same coordinates must discard the old instance");
            helper.assertTrue(cache.get(level, origin, Direction.NORTH).getBlockEndpoints().containsKey(origin.north()),
                    "Moving the output face must rebuild its synthetic source");
            System.out.println("P2P route cache: 256 unchanged lookups reused one chain; all direct graph mutations invalidated it");
        } finally {
            data.removeTransmissionNode(target);
            data.removeTransmissionNode(first.getNodePos());
            data.removeTransmissionNode(second.getNodePos());
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void cachedBranchingRoutesMatchNativeSplittingAndCycles(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_STARLIGHT_NETWORK_DATA);
        var origin = helper.absolutePos(new BlockPos(4, 3, 4));
        var branch = new SimpleTransmissionNode(origin.east());
        var left = new SimpleTransmissionNode(origin.east(3));
        var right = new SimpleTransmissionNode(origin.south(3));
        var receiver = new ForwardingStarlightReceiverNode(origin.offset(3, 0, 3));
        var cache = new StarlightRouteCache();
        try {
            data.addTransmissionNode(branch);
            data.addTransmissionNode(left);
            data.addTransmissionNode(right);
            data.addTransmissionNode(receiver);
            branch.getLinkDataContainer().orElseThrow().link(level, branch.getNodePos(), left.getNodePos(), Linkable.LinkAction.EXECUTE);
            branch.getLinkDataContainer().orElseThrow().link(level, branch.getNodePos(), right.getNodePos(), Linkable.LinkAction.EXECUTE);
            left.getLinkDataContainer().orElseThrow().link(level, left.getNodePos(), receiver.getNodePos(), Linkable.LinkAction.EXECUTE);
            right.getLinkDataContainer().orElseThrow().link(level, right.getNodePos(), receiver.getNodePos(), Linkable.LinkAction.EXECUTE);
            right.getLinkDataContainer().orElseThrow().link(level, right.getNodePos(), branch.getNodePos(), Linkable.LinkAction.EXECUTE);
            left.setLossMultiplier(0.5F);
            right.setLossMultiplier(0.25F);
            var source = new FocusCrystalSourceNode(origin);
            source.getLinkDataContainer().orElseThrow().link(level, origin, branch.getNodePos(), Linkable.LinkAction.EXECUTE);
            var expected = StarlightTransmissionChain.makeChain(level, source);
            var actual = cache.get(level, origin, Direction.EAST);
            helper.assertTrue(actual.getInvolvedStepLossMap().equals(expected.getInvolvedStepLossMap())
                            && actual.getReceiverEndpoints().equals(expected.getReceiverEndpoints())
                            && actual.getBlockEndpoints().equals(expected.getBlockEndpoints())
                            && cache.get(level, origin, Direction.EAST) == actual,
                    "Cached branch merges, losses and cycle termination must agree with the native traversal");
            var extra = origin.south(5);
            right.getLinkDataContainer().orElseThrow().link(level, right.getNodePos(), extra, Linkable.LinkAction.EXECUTE);
            var expanded = cache.get(level, origin, Direction.EAST);
            expected = StarlightTransmissionChain.makeChain(level, source);
            helper.assertTrue(expanded != actual
                            && expanded.getInvolvedStepLossMap().equals(expected.getInvolvedStepLossMap())
                            && expanded.getBlockEndpoints().equals(expected.getBlockEndpoints()),
                    "Adding a branch must immediately recalculate every affected split and merge");
            right.getLinkDataContainer().orElseThrow().unlink(level, right.getNodePos(), extra, Linkable.LinkAction.EXECUTE);
            var reduced = cache.get(level, origin, Direction.EAST);
            expected = StarlightTransmissionChain.makeChain(level, source);
            helper.assertTrue(reduced != expanded
                            && reduced.getInvolvedStepLossMap().equals(expected.getInvolvedStepLossMap())
                            && reduced.getBlockEndpoints().equals(expected.getBlockEndpoints()),
                    "Removing a branch must discard its endpoint and restore native splitting");
        } finally {
            data.removeTransmissionNode(branch.getNodePos());
            data.removeTransmissionNode(left.getNodePos());
            data.removeTransmissionNode(right.getNodePos());
            data.removeTransmissionNode(receiver.getNodePos());
        }
        helper.succeed();
    }
}
