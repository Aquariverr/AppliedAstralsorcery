package com.appliedastralsorcery.transmutation;

import java.util.Optional;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.data.level.StarlightNetworkData;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.linking.Linkable.LinkAction;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationNetworkGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void existingBeamRecognizesNewReceiver(GameTestHelper helper) {
        checkExistingBeam(helper, false, false);
    }

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void existingBeamRecognizesRestoredReceiver(GameTestHelper helper) {
        checkExistingBeam(helper, true, false);
    }

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void existingLensBeamRecognizesNewReceiver(GameTestHelper helper) {
        checkExistingBeam(helper, false, true);
    }

    private static void checkExistingBeam(GameTestHelper helper, boolean restoredNode, boolean lens) {
        // Let the native network cache this destination as an ordinary block first.
        connectSource(helper, lens);
        helper.runAfterDelay(5, () -> {
            helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
            if (restoredNode) {
                // Model world-data restoration: the receiver already exists before the tile's first tick.
                var machine = (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
                StarlightNetworkLevelHelper.get(helper.getLevel()).createNetworkNode(machine);
            }
            helper.runAfterDelay(5, () -> {
                var machine = (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
                helper.assertTrue(machine.hasStarlight(),
                        "A pre-existing beam must discover the new chamber receiver without relinking");
                helper.assertTrue(machine.getDisplayConstellation() == ConstellationsAS.MINERALIS.get(),
                        "The real transmission handler must deliver the source constellation");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void newlyLinkedBeamReachesRegisteredReceiver(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.runAfterDelay(5, () -> {
            connectSource(helper, false);
            helper.runAfterDelay(5, () -> {
                var machine = (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
                helper.assertTrue(machine.hasStarlight(), "A newly linked beam must reach a registered receiver");
                helper.succeed();
            });
        });
    }

    private static void connectSource(GameTestHelper helper, boolean lens) {
        // Fix only source generation so the test is independent of sky, time and constellation visibility.
        // Registration, linking, cached routes, level ticks and receiver delivery are all native AS code.
        var source = new FocusCrystalSourceNode(helper.absolutePos(POS.west(2))) {
            @Override public Optional<StarlightTransmissionPacket> produceStarlight(Level level) {
                return Optional.of(new StarlightTransmissionPacket(ConstellationsAS.MINERALIS.get(), 10));
            }
        };
        var data = (StarlightNetworkData) DataAS.DOMAIN_AS.getData(helper.getLevel(), DataAS.KEY_STARLIGHT_NETWORK_DATA);
        data.addTransmissionNode(source);
        var target = helper.absolutePos(POS);
        if (lens) {
            var relay = new SimpleTransmissionNode(helper.absolutePos(POS.north(2)));
            data.addTransmissionNode(relay);
            helper.assertTrue(relay.tryLinkTo(helper.getLevel(), target, LinkAction.EXECUTE).isSuccess(),
                    "The lens must link to the chamber position");
            relay.updateLinkStateChange(helper.getLevel(), target, true);
            target = relay.getNodePos();
        }
        helper.assertTrue(source.tryLinkTo(helper.getLevel(), target, LinkAction.EXECUTE).isSuccess(),
                "The crystal source must link to the target position");
        source.updateLinkStateChange(helper.getLevel(), target, true);
    }
}
