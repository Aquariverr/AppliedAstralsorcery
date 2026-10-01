package com.appliedastralsorcery.transmutation;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.constellation.level.LevelSkyHandler;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.data.level.FocalPointData;
import hellfirepvp.astralsorcery.common.focal.node.BasicFocalPointNode;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.util.data.ColumnPos;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.linking.Linkable.LinkAction;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.tile.TileStarlightFocusCrystal;
import hellfirepvp.astralsorcery.common.tile.network.SimpleTransmissionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationCrystalGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", batch = "transmutation_crystals", timeoutTicks = 120)
    public static void crystalOutsideFocalPointCannotSupplyChamber(GameTestHelper helper) {
        checkNativeCrystal(helper, false, null);
    }

    @GameTest(template = "wand_empty", batch = "transmutation_crystals", timeoutTicks = 120)
    public static void crystalAtMatchingFocalPointSuppliesChamberThroughLens(GameTestHelper helper) {
        checkNativeCrystal(helper, true, ConstellationsAS.AEVITAS.get());
    }

    @GameTest(template = "wand_empty", batch = "transmutation_crystals", timeoutTicks = 120)
    public static void crystalAtMatchingFocalPointSuppliesChamber(GameTestHelper helper) {
        checkNativeCrystal(helper, false, ConstellationsAS.AEVITAS.get());
    }

    @GameTest(template = "wand_empty", batch = "transmutation_crystals", timeoutTicks = 120)
    public static void crystalAtWrongFocalPointCannotSupplyChamber(GameTestHelper helper) {
        checkNativeCrystal(helper, false, ConstellationsAS.DISCIDIA.get());
    }

    private static void checkNativeCrystal(GameTestHelper helper, boolean lens, BaseConstellation focalConstellation) {
        checkNativeCrystal(helper, lens, focalConstellation, false);
    }

    @GameTest(template = "wand_empty", batch = "transmutation_crystals", timeoutTicks = 120)
    public static void loadingChamberClearsLegacyOrdinaryCrystalSource(GameTestHelper helper) {
        checkNativeCrystal(helper, false, null, true);
    }

    private static void checkNativeCrystal(GameTestHelper helper, boolean lens, BaseConstellation focalConstellation, boolean legacy) {
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        var machine = (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
        var crystalPos = POS.west(2);
        var focalData = (FocalPointData) DataAS.DOMAIN_AS.getData(helper.getLevel(), DataAS.KEY_FOCAL_POINT_DATA);
        if (focalConstellation != null)
            focalData.addNode(new BasicFocalPointNode(ColumnPos.of(helper.absolutePos(crystalPos)), focalConstellation));
        helper.setBlock(crystalPos, BlocksAS.STARLIGHT_FOCUS_ROCK_CRYSTAL.get());
        var crystal = (TileStarlightFocusCrystal) helper.getBlockEntity(crystalPos);
        var attributes = ItemsAS.ROCK_CRYSTAL.toStack().get(DataComponentsAS.CRYSTAL_ATTRIBUTES)
                .setAttributeTier(CrystalPropertiesAS.SIZE, 3).setAttributeTier(CrystalPropertiesAS.PURITY, 3);
        crystal.getTileData().setCrystalAttributes(attributes);
        crystal.getTileData().setConstellation(ConstellationsAS.AEVITAS.get());
        crystal.serverTick(helper.getLevel());
        var source = crystal.getNetworkNode().orElseThrow();
        if (legacy) {
            // Model the source data saved by the previous unrestricted compatibility patch.
            source.setConstellation(ConstellationsAS.AEVITAS.get());
            source.setCrystalProperties(attributes);
        }
        var target = helper.absolutePos(POS);
        if (lens) {
            helper.setBlock(POS.north(2), BlocksAS.LENS.get());
            var relayTile = (hellfirepvp.astralsorcery.common.tile.base.TileEntityNetwork<?, ?>)
                    helper.getBlockEntity(POS.north(2));
            relayTile.serverTick(helper.getLevel());
            var relay = (SimpleTransmissionNode) StarlightNetworkLevelHelper.get(helper.getLevel())
                    .getNode(helper.absolutePos(POS.north(2))).orElseThrow();
            helper.assertTrue(relay.tryLinkTo(helper.getLevel(), target, LinkAction.EXECUTE).isSuccess(), "Lens must link");
            relay.updateLinkStateChange(helper.getLevel(), target, true);
            target = relay.getNodePos();
        }
        helper.assertTrue(source.tryLinkTo(helper.getLevel(), target, LinkAction.EXECUTE).isSuccess(), "Crystal must link");
        source.updateLinkStateChange(helper.getLevel(), target, true);
        helper.runAfterDelay(5, () -> {
            var sky = LevelSkyHandler.getContext(helper.getLevel()).orElseThrow().getConstellationHandler();
            for (int day = 0; day < 8; day++) {
                helper.getLevel().setDayTime(day * 24000L + 18000);
                sky.tick(helper.getLevel());
                if (sky.getDistributionMultiplier(helper.getLevel(), ConstellationsAS.AEVITAS.get()) > 0) break;
            }
        });
        helper.runAfterDelay(40, () -> {
            if (focalConstellation != ConstellationsAS.AEVITAS.get()) {
                helper.assertTrue(source.getConstellation().isEmpty() && !machine.hasStarlight(),
                        "The chamber must not initialize a crystal outside its matching focal point");
                focalData.getNode(helper.absolutePos(crystalPos)).ifPresent(focalData::removeNode);
                helper.succeed();
                return;
            }
            helper.assertTrue(source.getConstellation().orElse(null) == ConstellationsAS.AEVITAS.get()
                    && source.getCrystalProperties().equals(attributes), "AS must initialize crystals at their matching focal point");
            helper.assertTrue(source.produceStarlight(helper.getLevel()).orElseThrow().amount() > 0,
                    "The actual native crystal must generate positive starlight");
            helper.assertTrue(machine.hasStarlight() && machine.getDisplayConstellation() == ConstellationsAS.AEVITAS.get(),
                    "Native transmission must deliver Aevitas starlight without manual packet injection");
            focalData.getNode(helper.absolutePos(crystalPos)).ifPresent(focalData::removeNode);
            helper.succeed();
        });
    }
}
