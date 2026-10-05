package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.PartPlacement;
import com.appliedastralsorcery.ModConfig;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.starlight.StarlightP2PTunnelPart;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.focal.node.BasicFocalPointNode;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import hellfirepvp.astralsorcery.common.tile.TileLens;
import hellfirepvp.astralsorcery.common.tile.TileStarlightFocusCrystal;
import hellfirepvp.astralsorcery.common.linking.Linkable;
import hellfirepvp.astralsorcery.common.recipe.focal.place.ActiveTransmutationHandler;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import hellfirepvp.astralsorcery.common.tile.network.ForwardingStarlightReceiverNode;
import hellfirepvp.astralsorcery.common.tile.network.SimpleSingleTransmissionNode;
import hellfirepvp.astralsorcery.common.util.data.ColumnPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class StarlightP2PGameTests {
    private static BaseConstellation constellation(int index) {
        return RegistriesAS.REGISTRY_CONSTELLATIONS.byId(index);
    }

    @GameTest(template = "attunement_test")
    public static void starlightTunnelSupportsNativePlacementModelsAndAttunement(GameTestHelper helper) {
        int index = 0;
        for (var face : Direction.values()) {
            var pos = helper.absolutePos(new BlockPos(4 + index++ * 2, 4, 6));
            cable(helper, pos);
            var part = PartPlacement.placePart(null, helper.getLevel(), ModContent.STARLIGHT_P2P_TUNNEL.get(),
                    null, pos, face);
            helper.assertTrue(part != null && PartHelper.getPartHost(helper.getLevel(), pos).getPart(face) == part,
                    "Starlight tunnels must use native cable placement on " + face);
            helper.assertTrue(part.getStaticModels().getModels().size() == 3,
                    "Native status and frequency models must accompany the starlight front");
        }
        helper.assertTrue(P2PTunnelAttunement.getTunnelPartByTriggerItem(new ItemStack(BlocksAS.LENS.get()))
                        .is(ModContent.STARLIGHT_P2P_TUNNEL.get()),
                "A native Lens must attune a P2P to starlight");
        helper.assertTrue(helper.getLevel().getRecipeManager()
                        .byKey(net.minecraft.resources.ResourceLocation.parse("appliedas:starlight_p2p_tunnel")).isPresent(),
                "The survival crafting recipe must load");
        helper.succeed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void nativeMemoryCardPairsAndSplitsAllConstellationsWithoutBuffering(GameTestHelper helper) {
        var fixture = network(helper);
        var first = probe(helper, fixture.firstTarget());
        var second = probe(helper, fixture.secondTarget());
        helper.startSequence().thenWaitUntil(() -> fixture.assertOnline(helper)).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first, fixture.second);
            beam(helper, fixture.input, constellation(0), 12);
            beam(helper, fixture.input, constellation(1), 6);
            first.assertPacket(helper, 0, constellation(0), 6);
            first.assertPacket(helper, 1, constellation(1), 3);
            second.assertPacket(helper, 0, constellation(0), 6);
            second.assertPacket(helper, 1, constellation(1), 3);
            for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
                beam(helper, fixture.input, constellation(0), invalid);
            helper.assertTrue(first.packets.size() == 2 && second.packets.size() == 2,
                    "Invalid amounts must not create light");
            var saved = new CompoundTag();
            fixture.first.writeToNBT(saved, helper.getLevel().registryAccess());
            var restored = new StarlightP2PTunnelPart(ModContent.STARLIGHT_P2P_TUNNEL.get());
            restored.setPartHostInfo(fixture.first.getSide(), fixture.first.getHost(), fixture.first.getBlockEntity());
            restored.readFromNBT(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.isOutput() && restored.getFrequency() == fixture.input.getFrequency(),
                    "Native NBT must preserve frequency and direction");
        }).thenExecuteAfter(5, () -> {
            helper.assertTrue(first.packets.size() == 2 && second.packets.size() == 2,
                    "Ending the beam must leave no stored or replayed light");
            fixture.first.getMainNode().destroy();
            beam(helper, fixture.input, constellation(0), 8);
            second.assertPacket(helper, 2, constellation(0), 8);
            helper.assertTrue(first.packets.size() == 2, "Offline outputs must receive no share");
            fixture.input.getMainNode().destroy();
            beam(helper, fixture.input, constellation(0), 8);
            helper.assertTrue(second.packets.size() == 3, "An offline input must stop forwarding immediately");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void nativeSourceTickTraversesTunnelAndLensWithLossAndLiveRelinking(GameTestHelper helper) {
        var fixture = network(helper);
        var first = probe(helper, fixture.firstTarget().south(3));
        var second = probe(helper, fixture.secondTarget());
        var lens = new SimpleSingleTransmissionNode(fixture.firstTarget());
        lens.setLossMultiplier(0.25F);
        lens.getLinkDataContainer().orElseThrow().link(helper.getLevel(), lens.getNodePos(), first.getNodePos(), Linkable.LinkAction.EXECUTE);
        addNode(helper, lens);
        var source = new FocusCrystalSourceNode(helper.absolutePos(new BlockPos(4, 3, 4))) {
            @Override public Optional<StarlightTransmissionPacket> produceStarlight(Level level) {
                return Optional.of(new StarlightTransmissionPacket(constellation(0), 16));
            }
        };
        source.getLinkDataContainer().orElseThrow().link(helper.getLevel(), source.getNodePos(),
                fixture.input.getBlockEntity().getBlockPos(), Linkable.LinkAction.EXECUTE);
        helper.startSequence().thenWaitUntil(() -> fixture.assertOnline(helper)).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first, fixture.second);
            addNode(helper, source);
        }).thenWaitUntil(() -> {
            helper.assertTrue(!first.packets.isEmpty() && !second.packets.isEmpty(),
                    "The actual AS source tick must enter the cable endpoint and reach both outputs");
            first.assertPacket(helper, 0, constellation(0), 2);
            second.assertPacket(helper, 0, constellation(0), 8);
        }).thenExecute(() -> {
            removeNode(helper, source);
            first.packets.clear();
            second.packets.clear();
            lens.getLinkDataContainer().orElseThrow().unlink(helper.getLevel(), lens.getNodePos(), first.getNodePos(), Linkable.LinkAction.EXECUTE);
            lens.getLinkDataContainer().orElseThrow().link(helper.getLevel(), lens.getNodePos(), second.getNodePos(), Linkable.LinkAction.EXECUTE);
            beam(helper, fixture.input, constellation(1), 16);
            helper.assertTrue(first.packets.isEmpty(), "Relinking the lens must stop its old route immediately");
            helper.assertTrue(second.packets.size() == 2 && Math.abs(second.total() - 10) < 0.0001F,
                    "The new route must receive the direct share plus the lossy lens share");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void focalLightRequiresNightOpenSkyAndExistingFocalPoint(GameTestHelper helper) {
        var fixture = network(helper);
        var first = chamber(helper, fixture.firstTarget());
        var second = chamber(helper, fixture.secondTarget());
        var pos = fixture.input.getBlockEntity().getBlockPos();
        var focal = new BasicFocalPointNode(ColumnPos.of(pos), constellation(0));
        helper.startSequence().thenWaitUntil(() -> fixture.assertOnline(helper)).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first, fixture.second);
            var level = helper.getLevel();
            var data = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA);
            long previousTime = level.getDayTime();
            data.addNode(focal);
            try {
                level.setDayTime(18000);
                helper.assertTrue(fixture.input.canReceiveStarlight(), "The focal collector must be an online paired input");
                helper.assertTrue(hellfirepvp.astralsorcery.common.util.level.DayTimeHelper.isNight(level),
                        "The focal fixture must use AS nighttime; time=" + level.getDayTime());
                helper.assertTrue(data.getNode(pos).orElse(null) == focal, "The focal fixture must be registered in its column");
                // GameTest surrounds its structure with barriers, including a roof above its bounds.
                for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++)
                    level.setBlockAndUpdate(new BlockPos(pos.getX(), y, pos.getZ()), Blocks.AIR.defaultBlockState());
                helper.assertTrue(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                                pos.getX(), pos.getZ()) <= pos.getY() + 1,
                        "The focal fixture must have open sky; surface=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                                pos.getX(), pos.getZ()) + ", collector=" + pos);
                fixture.input.collectFocalStarlight();
                first.serverTick(level);
                second.serverTick(level);
                helper.assertTrue(first.hasStarlight() && second.hasStarlight()
                                && first.getDisplayConstellation() == constellation(0)
                                && second.getDisplayConstellation() == constellation(0),
                        "Both chambers must receive the focal constellation");
            } finally {
                data.removeNode(focal);
                level.setDayTime(previousTime);
            }
        }).thenExecuteAfter(4, () -> {
            var level = helper.getLevel();
            var data = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA);
            long previousTime = level.getDayTime();
            data.addNode(focal);
            try {
                level.setDayTime(18000);
                level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
                fixture.input.collectFocalStarlight();
                level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                level.setDayTime(1000);
                fixture.input.collectFocalStarlight();
                level.setDayTime(18000);
                data.removeNode(focal);
                fixture.input.collectFocalStarlight();
                first.serverTick(level);
                second.serverTick(level);
                helper.assertTrue(!first.hasStarlight() && !second.hasStarlight(),
                        "Covering the collector, daylight and removal must each stop focal input");
            } finally {
                data.removeNode(focal);
                level.setDayTime(previousTime);
                level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
            }
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void focalOriginSurvivesChainedTunnelsAndKeepsSlowChamberDuration(GameTestHelper helper) {
        var fixture = network(helper);
        var relayPos = fixture.firstTarget();
        cable(helper, relayPos);
        cable(helper, relayPos.east());
        var relay = PartPlacement.placePart(null, helper.getLevel(), ModContent.STARLIGHT_P2P_TUNNEL.get(),
                null, relayPos, Direction.UP);
        var chamber = chamber(helper, fixture.secondTarget());
        var recipe = chamberRecipe(helper);
        var matching = recipe.getRequiredConstellation().orElseThrow();
        var other = RegistriesAS.REGISTRY_CONSTELLATIONS.stream().filter(cst -> !recipe.isRequiredConstellation(cst))
                .findFirst().orElseThrow();
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(relay.getMainNode().isOnline() && chamber.getMainNode().isOnline(),
                    "The relay and chamber must be online");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            pair(helper, relay, fixture.second);
            supplyRecipe(chamber, recipe);
            collectFocal(helper, fixture.input, matching);
            chamber.serverTick(helper.getLevel());
            assertDuration(helper, chamber, ModConfig.TRANSMUTATION_FOCAL_TICKS.get());
            beam(helper, fixture.input, matching, 12);
            collectFocal(helper, fixture.input, matching);
            chamber.serverTick(helper.getLevel());
            assertDuration(helper, chamber, ModConfig.TRANSMUTATION_STARLIGHT_TICKS.get());
        }).thenExecuteAfter(4, () -> {
            collectFocal(helper, fixture.input, matching);
            beam(helper, fixture.input, other, 12);
            chamber.serverTick(helper.getLevel());
            assertDuration(helper, chamber, ModConfig.TRANSMUTATION_FOCAL_TICKS.get());
            helper.assertTrue(chamber.getDisplayConstellation() == matching,
                    "A mismatched focused beam must not upgrade the matching focal light");
        }).thenExecuteAfter(4, () -> {
            chamber.serverTick(helper.getLevel());
            helper.assertTrue(!chamber.hasStarlight() && chamber.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT,
                    "Both origin types must expire when the source stops");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void realLensRejectsFocalLightButStillForwardsFocusedStarlight(GameTestHelper helper) {
        var fixture = network(helper);
        var level = helper.getLevel();
        var lensPos = fixture.firstTarget();
        level.setBlockAndUpdate(lensPos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lensPos, BlocksAS.LENS.get().defaultBlockState());
        var lens = (TileLens) level.getBlockEntity(lensPos);
        lens.getTileData().setCrystalAttributes(CrystalAttributesComponent.defaultEmpty()
                .setAttributeTier(CrystalPropertiesAS.PURITY, CrystalPropertiesAS.PURITY.get().getMaxTier()));
        var chamber = chamber(helper, lensPos.south(3));
        var recipe = chamberRecipe(helper);
        var cst = recipe.getRequiredConstellation().orElseThrow();
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(chamber.getMainNode().isOnline() && lens.getNetworkNode().isPresent(),
                    "The real lens and chamber must initialize");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            supplyRecipe(chamber, recipe);
            var lensNode = lens.getNetworkNode().orElseThrow();
            helper.assertTrue(lensNode.tryLinkTo(level, chamber.getBlockPos(), Linkable.LinkAction.EXECUTE).isSuccess(),
                    "The lens must accept a native, unobstructed link to the chamber");
            lensNode.updateLinkStateChange(level, chamber.getBlockPos(), true);
            collectFocal(helper, fixture.input, cst);
            chamber.serverTick(level);
            helper.assertTrue(!chamber.hasStarlight() && chamber.getProgress() == 0,
                    "A real lens must not relay unfocused focal-point light");
            beam(helper, fixture.input, cst, 12);
            chamber.serverTick(level);
            assertDuration(helper, chamber, ModConfig.TRANSMUTATION_STARLIGHT_TICKS.get());
        }).thenExecuteAfter(4, () -> {
            collectFocal(helper, fixture.input, cst);
            chamber.serverTick(level);
            helper.assertTrue(!chamber.hasStarlight(), "Focal input must not keep a previously focused lens route alive");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void focalP2PActivatesRealCrystalsAndFocusesThroughNativeLens(GameTestHelper helper) {
        var fixture = network(helper);
        var level = helper.getLevel();
        var recipe = visibleChamberRecipe(helper);
        var cst = recipe.getRequiredConstellation().orElseThrow();
        var first = focusCrystal(helper, fixture.firstTarget(), cst);
        var second = focusCrystal(helper, fixture.secondTarget(), cst, true);
        var lensPos = first.getBlockPos().south(3);
        level.setBlockAndUpdate(lensPos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(lensPos, BlocksAS.LENS.get().defaultBlockState());
        var lens = (TileLens) level.getBlockEntity(lensPos);
        lens.getTileData().setCrystalAttributes(CrystalAttributesComponent.defaultEmpty()
                .setAttributeTier(CrystalPropertiesAS.PURITY, CrystalPropertiesAS.PURITY.get().getMaxTier()));
        var chamber = chamber(helper, lensPos.east(3));
        long[] previousTime = {0};
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(first.getNetworkNode().isPresent() && second.getNetworkNode().isPresent()
                            && lens.getNetworkNode().isPresent() && chamber.getMainNode().isOnline(),
                    "Real crystals, lens and chamber must initialize");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first, fixture.second);
            supplyRecipe(chamber, recipe);
            var source = first.getNetworkNode().orElseThrow();
            var lensNode = lens.getNetworkNode().orElseThrow();
            helper.assertTrue(source.tryLinkTo(level, lensPos, Linkable.LinkAction.EXECUTE).isSuccess()
                            && lensNode.tryLinkTo(level, chamber.getBlockPos(), Linkable.LinkAction.EXECUTE).isSuccess(),
                    "The real crystal and lens must accept their native links");
            source.updateLinkStateChange(level, lensPos, true);
            lensNode.updateLinkStateChange(level, chamber.getBlockPos(), true);
            previousTime[0] = level.getDayTime();
            collectFocal(helper, fixture.input, cst);
            first.serverTick(level);
            second.serverTick(level);
            helper.assertTrue(first.getTileData().isOnFocalNode() && second.getTileData().isOnFocalNode(),
                    "Matching P2P focal light must activate both actual crystals");
            helper.assertTrue(source.getConstellation().isEmpty()
                            && DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA).getNode(first.getBlockPos()).isEmpty(),
                    "P2P must not create a permanent generator or a world focal point");
            level.setDayTime(18000);
            try {
                var baseline = new FocusCrystalSourceNode(first.getBlockPos());
                baseline.setConstellation(cst);
                baseline.setCrystalProperties(first.getTileData().getCrystalAttributes());
                float expected = baseline.produceStarlight(level).orElseThrow().amount() / 2;
                helper.assertTrue(expected > 0, "The native nighttime crystal must generate light");
                for (var crystal : List.of(first, second)) {
                    var packet = crystal.getNetworkNode().orElseThrow().produceStarlight(level).orElseThrow();
                    helper.assertTrue(packet.constellation() == cst && Math.abs(packet.amount() - expected) < 0.0001F,
                            "Each real crystal must focus only its half of the input");
                }
            } catch (Throwable failure) {
                level.setDayTime(previousTime[0]);
                throw failure;
            }
        }).thenExecuteAfter(2, () -> {
            try {
                chamber.serverTick(level);
                assertDuration(helper, chamber, ModConfig.TRANSMUTATION_STARLIGHT_TICKS.get());
            } finally {
                level.setDayTime(previousTime[0]);
            }
        }).thenExecuteAfter(5, () -> {
            first.serverTick(level);
            second.serverTick(level);
            chamber.serverTick(level);
            helper.assertTrue(!first.getTileData().isOnFocalNode() && !second.getTileData().isOnFocalNode()
                            && first.getNetworkNode().orElseThrow().produceStarlight(level).isEmpty()
                            && !chamber.hasStarlight(),
                    "Ending the source must deactivate crystals and stop their native downstream route");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void focusCrystalRejectsWrongOriginAndConstellationAndDoesNotSaveSupply(GameTestHelper helper) {
        var fixture = network(helper);
        var level = helper.getLevel();
        var cst = constellation(0);
        var crystal = focusCrystal(helper, fixture.firstTarget(), null);
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(crystal.getNetworkNode().isPresent(), "The crystal source must initialize");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            collectFocal(helper, fixture.input, cst);
            crystal.serverTick(level);
            helper.assertTrue(!crystal.getTileData().isOnFocalNode(), "Unattuned crystals must not activate");
            crystal.getTileData().setConstellation(cst);
            collectFocal(helper, fixture.input, constellation(1));
            beam(helper, fixture.input, cst, 12);
            crystal.serverTick(level);
            helper.assertTrue(!crystal.getTileData().isOnFocalNode()
                            && crystal.getNetworkNode().orElseThrow().produceStarlight(level).isEmpty(),
                    "Wrong constellations and focused beams must not substitute for a focal point");
            collectFocal(helper, fixture.input, cst);
            crystal.serverTick(level);
            helper.assertTrue(crystal.getTileData().isOnFocalNode(), "Matching raw focal light must activate");
            crystal.getTileData().setConstellation(constellation(1));
            helper.assertTrue(crystal.getNetworkNode().orElseThrow().produceStarlight(level).isEmpty(),
                    "Retuning must invalidate the old constellation immediately");
            crystal.getTileData().setConstellation(cst);
            var saved = crystal.saveWithFullMetadata(level.registryAccess());
            var restored = (TileStarlightFocusCrystal) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    crystal.getBlockPos(), crystal.getBlockState(), saved, level.registryAccess());
            level.setBlockEntity(restored);
            restored.serverTick(level);
            helper.assertTrue(!restored.getTileData().isOnFocalNode()
                            && restored.getNetworkNode().orElseThrow().produceStarlight(level).isEmpty(),
                    "Reloading a crystal must not restore a saved P2P supply or leave an active visual flag");
            collectFocal(helper, fixture.input, cst);
            restored.serverTick(level);
            helper.assertTrue(restored.getTileData().isOnFocalNode(), "A reloaded crystal must accept fresh supply");
            var removedSource = restored.getNetworkNode().orElseThrow();
            level.removeBlock(restored.getBlockPos(), false);
            helper.assertTrue(removedSource.produceStarlight(level).isEmpty(),
                    "A saved network source must not generate after its crystal is removed");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void naturalFocusCrystalKeepsNativeProductionWithP2PInput(GameTestHelper helper) {
        var fixture = network(helper);
        var level = helper.getLevel();
        var cst = constellation(0);
        var crystal = focusCrystal(helper, fixture.firstTarget(), cst);
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(crystal.getNetworkNode().isPresent(), "The native crystal must initialize");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            var points = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA);
            var focal = new BasicFocalPointNode(ColumnPos.of(crystal.getBlockPos()), cst);
            points.addNode(focal);
            long previousTime = level.getDayTime();
            try {
                focal.updateFocalPosition(level);
                level.setDayTime(18000);
                for (int tick = 0; tick < 20; tick++) crystal.serverTick(level);
                var source = crystal.getNetworkNode().orElseThrow();
                helper.assertTrue(source.getConstellation().orElse(null) == cst && crystal.getTileData().isOnFocalNode(),
                        "A natural focal point must still initialize the original crystal source");
                var before = source.produceStarlight(level).orElseThrow();
                collectFocal(helper, fixture.input, cst);
                var after = source.produceStarlight(level).orElseThrow();
                helper.assertTrue(before.equals(after), "P2P input must not duplicate a natural crystal's production");
            } finally {
                points.removeNode(focal);
                level.setDayTime(previousTime);
            }
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void focalTunnelCannotUnlockNativeFocusedOnlyBlockTransmutation(GameTestHelper helper) {
        var fixture = network(helper);
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_TRANSMUTATION_TYPE.get()).stream()
                .map(holder -> holder.value()).filter(value -> value.requiresFocusedStarlight()
                        && value.getInputDisplay().getItems().length > 0
                        && value.getInputDisplay().getItems()[0].getItem() instanceof net.minecraft.world.item.BlockItem)
                .findFirst().orElseThrow();
        var block = ((net.minecraft.world.item.BlockItem) recipe.getInputDisplay().getItems()[0].getItem()).getBlock();
        var cst = recipe.getRequiredConstellation().orElse(constellation(0));
        level.setBlockAndUpdate(fixture.firstTarget(), block.defaultBlockState());
        helper.startSequence().thenWaitUntil(() -> fixture.assertOnline(helper)).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            collectFocal(helper, fixture.input, cst);
            helper.assertTrue(ActiveTransmutationHandler.getActiveTransmutation(level, fixture.firstTarget()).isEmpty(),
                    "Raw focal light must not start a focused-only native recipe");
            beam(helper, fixture.input, cst, 12);
            helper.assertTrue(ActiveTransmutationHandler.getActiveTransmutation(level, fixture.firstTarget()).isPresent(),
                    "The same block must accept actual focused starlight through the tunnel");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void sharedCableInputsAndFeedbackDoNotDuplicateStarlight(GameTestHelper helper) {
        var fixture = network(helper);
        var extra = PartPlacement.placePart(null, helper.getLevel(), ModContent.STARLIGHT_P2P_TUNNEL.get(),
                null, fixture.input.getBlockEntity().getBlockPos(), Direction.UP);
        var first = probe(helper, fixture.firstTarget());
        var second = probe(helper, fixture.secondTarget());
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(extra.getMainNode().isOnline(), "The second input must be online");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            pair(helper, extra, fixture.second);
            beam(helper, fixture.input, constellation(0), 12);
            first.assertPacket(helper, 0, constellation(0), 6);
            second.assertPacket(helper, 0, constellation(0), 6);
            pair(helper, fixture.input, fixture.first, fixture.second);
            extra.getMainNode().destroy();
            removeNode(helper, first);
            var feedback = new SimpleSingleTransmissionNode(fixture.firstTarget());
            feedback.getLinkDataContainer().orElseThrow().link(helper.getLevel(), feedback.getNodePos(),
                    fixture.input.getBlockEntity().getBlockPos(), Linkable.LinkAction.EXECUTE);
            addNode(helper, feedback);
            second.packets.clear();
            beam(helper, fixture.input, constellation(1), 12);
            helper.assertTrue(second.packets.size() == 1,
                    "A lens feeding its own input must terminate without recursion or additional energy");
            second.assertPacket(helper, 0, constellation(1), 6);
            beam(helper, fixture.input, constellation(0), 8);
            second.assertPacket(helper, 1, constellation(0), 4);
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void tunnelSuppliesRealTransmutationChamberAndStopsAfterBeamEnds(GameTestHelper helper) {
        var fixture = network(helper);
        helper.getLevel().setBlockAndUpdate(fixture.firstTarget(), ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get().defaultBlockState());
        var chamber = (StarlightTransmutationBlockEntity) helper.getLevel().getBlockEntity(fixture.firstTarget());
        helper.startSequence().thenWaitUntil(() -> {
            fixture.assertOnline(helper);
            helper.assertTrue(chamber.getNetworkNode().isPresent(), "The real AS receiver must register");
        }).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first);
            beam(helper, fixture.input, constellation(1), 12);
            chamber.serverTick(helper.getLevel());
            helper.assertTrue(chamber.hasStarlight() && chamber.getDisplayConstellation() == constellation(1),
                    "The actual chamber must receive the transmitted constellation");
        }).thenExecuteAfter(4, () -> {
            chamber.serverTick(helper.getLevel());
            helper.assertTrue(!chamber.hasStarlight(), "A real machine must stop seeing light when the input ends");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 160)
    public static void unloadedLensPathsAndRemovedReceiversStopTransmission(GameTestHelper helper) {
        var fixture = network(helper);
        var first = probe(helper, fixture.firstTarget());
        var farPos = new BlockPos(25_000_000, 80, 25_000_000);
        var far = probe(helper, farPos);
        var lens = new SimpleSingleTransmissionNode(fixture.secondTarget());
        lens.getLinkDataContainer().orElseThrow().link(helper.getLevel(), lens.getNodePos(), farPos, Linkable.LinkAction.EXECUTE);
        addNode(helper, lens);
        helper.startSequence().thenWaitUntil(() -> fixture.assertOnline(helper)).thenExecute(() -> {
            pair(helper, fixture.input, fixture.first, fixture.second);
            helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4),
                    "The distant endpoint fixture must be unloaded");
            beam(helper, fixture.input, constellation(0), 12);
            first.assertPacket(helper, 0, constellation(0), 6);
            helper.assertTrue(far.packets.isEmpty()
                            && !helper.getLevel().getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4),
                    "Stored AS nodes must not cause transmission into, or loading of, absent chunks");
            removeNode(helper, first);
            beam(helper, fixture.input, constellation(0), 12);
            helper.assertTrue(first.packets.size() == 1, "Removed receivers must not remain cached as outputs");
            removeNode(helper, far);
        }).thenSucceed();
    }

    private record Fixture(StarlightP2PTunnelPart input, StarlightP2PTunnelPart first, StarlightP2PTunnelPart second) {
        BlockPos firstTarget() { return first.getBlockEntity().getBlockPos().relative(first.getSide()); }
        BlockPos secondTarget() { return second.getBlockEntity().getBlockPos().relative(second.getSide()); }
        void assertOnline(GameTestHelper helper) {
            helper.assertTrue(input.getMainNode().isOnline() && first.getMainNode().isOnline()
                    && second.getMainNode().isOnline(), "All tunnels must receive real ME power and channels");
        }
    }

    private static Fixture network(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 2, 8), AEBlocks.CREATIVE_ENERGY_CELL.block());
        for (int x = 4; x <= 16; x++) cable(helper, helper.absolutePos(new BlockPos(x, 3, 8)));
        return new Fixture(tunnel(helper, 4, Direction.NORTH), tunnel(helper, 10, Direction.SOUTH),
                tunnel(helper, 16, Direction.SOUTH));
    }

    private static void cable(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(PartPlacement.placePart(null, helper.getLevel(), AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT),
                null, pos, null) != null, "The cable fixture must place");
    }

    private static StarlightP2PTunnelPart tunnel(GameTestHelper helper, int x, Direction side) {
        var part = PartPlacement.placePart(null, helper.getLevel(), ModContent.STARLIGHT_P2P_TUNNEL.get(),
                null, helper.absolutePos(new BlockPos(x, 3, 8)), side);
        helper.assertTrue(part != null, "The tunnel fixture must place");
        return part;
    }

    private static void pair(GameTestHelper helper, StarlightP2PTunnelPart input, StarlightP2PTunnelPart... outputs) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        boolean sneaking = player.isShiftKeyDown();
        var held = player.getMainHandItem();
        var card = AEItems.MEMORY_CARD.stack();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            player.setShiftKeyDown(true);
            helper.assertTrue(input.onUseItemOn(card, player, InteractionHand.MAIN_HAND, Vec3.ZERO),
                    "Native memory card must configure the input");
            player.setShiftKeyDown(false);
            for (var output : outputs) {
                helper.assertTrue(output.onUseItemOn(card, player, InteractionHand.MAIN_HAND, Vec3.ZERO),
                        "Native memory card must configure each output");
                helper.assertTrue(output.isOutput() && output.getInput() == input,
                        "The actual P2P service must resolve the configured input");
            }
        } finally {
            player.setShiftKeyDown(sneaking);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
    }

    private static void beam(GameTestHelper helper, StarlightP2PTunnelPart input, BaseConstellation constellation, float amount) {
        ActiveTransmutationHandler.receiveStarlight(helper.getLevel(), input.getBlockEntity().getBlockPos(),
                new StarlightTransmissionPacket(constellation, amount));
    }

    private static RecordingReceiver probe(GameTestHelper helper, BlockPos pos) {
        var probe = new RecordingReceiver(pos);
        addNode(helper, probe);
        return probe;
    }

    private static StarlightTransmutationBlockEntity chamber(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get().defaultBlockState());
        return (StarlightTransmutationBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static TileStarlightFocusCrystal focusCrystal(GameTestHelper helper, BlockPos pos, BaseConstellation cst) {
        return focusCrystal(helper, pos, cst, false);
    }

    private static TileStarlightFocusCrystal focusCrystal(GameTestHelper helper, BlockPos pos, BaseConstellation cst,
            boolean celestial) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, (celestial ? BlocksAS.STARLIGHT_FOCUS_CELESTIAL_CRYSTAL
                : BlocksAS.STARLIGHT_FOCUS_ROCK_CRYSTAL).get().defaultBlockState());
        var crystal = (TileStarlightFocusCrystal) level.getBlockEntity(pos);
        crystal.getTileData().setConstellation(cst);
        crystal.getTileData().setCrystalAttributes(CrystalAttributesComponent.defaultEmpty()
                .setAttributeTier(CrystalPropertiesAS.SIZE, 3).setAttributeTier(CrystalPropertiesAS.CUT, 2));
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++)
            level.setBlockAndUpdate(new BlockPos(pos.getX(), y, pos.getZ()), Blocks.AIR.defaultBlockState());
        return crystal;
    }

    private static FocalCombineRecipe visibleChamberRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        long previousTime = level.getDayTime();
        try {
            level.setDayTime(18000);
            var sky = hellfirepvp.astralsorcery.common.constellation.level.LevelSkyHandler.getContext(level).orElseThrow();
            return level.getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                    .map(holder -> holder.value()).filter(recipe -> recipe.getRequiredConstellation()
                            .filter(cst -> sky.getConstellationHandler().getDistributionMultiplier(level, cst) > 0.01F).isPresent()
                            && !recipe.getInputs().isEmpty()
                            && recipe.getInputs().size() <= StarlightTransmutationBlockEntity.INPUT_SLOTS
                            && recipe.getInputs().stream().allMatch(ingredient -> ingredient.getItems().length > 0))
                    .findFirst().orElseThrow();
        } finally {
            level.setDayTime(previousTime);
        }
    }

    private static FocalCombineRecipe chamberRecipe(GameTestHelper helper) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .map(holder -> holder.value()).filter(recipe -> recipe.getRequiredConstellation().isPresent()
                        && !recipe.getInputs().isEmpty() && recipe.getInputs().size() <= StarlightTransmutationBlockEntity.INPUT_SLOTS
                        && recipe.getInputs().stream().allMatch(ingredient -> ingredient.getItems().length > 0))
                .findFirst().orElseThrow();
    }

    private static void supplyRecipe(StarlightTransmutationBlockEntity chamber, FocalCombineRecipe recipe) {
        for (int slot = 0; slot < recipe.getInputs().size(); slot++)
            chamber.getInventory().setStackInSlot(slot, recipe.getInputs().get(slot).getItems()[0].copyWithCount(1));
    }

    private static void assertDuration(GameTestHelper helper, StarlightTransmutationBlockEntity chamber, int ticks) {
        helper.assertTrue(chamber.getStatus() == StarlightTransmutationBlockEntity.Status.RUNNING
                        && chamber.getDuration() == ticks,
                "The real chamber must run for " + ticks + " ticks; got " + chamber.getStatus() + "/" + chamber.getDuration());
    }

    private static void collectFocal(GameTestHelper helper, StarlightP2PTunnelPart input, BaseConstellation constellation) {
        var level = helper.getLevel();
        var pos = input.getBlockEntity().getBlockPos();
        var data = DataAS.DOMAIN_AS.getData(level, DataAS.KEY_FOCAL_POINT_DATA);
        var focal = new BasicFocalPointNode(ColumnPos.of(pos), constellation);
        long previousTime = level.getDayTime();
        data.addNode(focal);
        try {
            level.setDayTime(18000);
            for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++)
                level.setBlockAndUpdate(new BlockPos(pos.getX(), y, pos.getZ()), Blocks.AIR.defaultBlockState());
            input.collectFocalStarlight();
        } finally {
            data.removeNode(focal);
            level.setDayTime(previousTime);
        }
    }

    private static void addNode(GameTestHelper helper, TransmissionNode node) {
        DataAS.DOMAIN_AS.getData(helper.getLevel(), DataAS.KEY_STARLIGHT_NETWORK_DATA).addTransmissionNode(node);
    }

    private static void removeNode(GameTestHelper helper, TransmissionNode node) {
        DataAS.DOMAIN_AS.getData(helper.getLevel(), DataAS.KEY_STARLIGHT_NETWORK_DATA).removeTransmissionNode(node.getNodePos());
        StarlightTransmissionLevelHelper.getInstance().getHandler(helper.getLevel()).ifPresent(handler -> handler.notifyNodeChange(node));
    }

    private static final class RecordingReceiver extends ForwardingStarlightReceiverNode {
        private final List<StarlightTransmissionPacket> packets = new ArrayList<>();
        RecordingReceiver(BlockPos pos) { super(pos); }
        @Override public void receiveStarlight(ServerLevel level, StarlightTransmissionPacket packet) { packets.add(packet); }
        float total() { return (float) packets.stream().mapToDouble(StarlightTransmissionPacket::amount).sum(); }
        void assertPacket(GameTestHelper helper, int index, BaseConstellation constellation, float amount) {
            helper.assertTrue(packets.size() > index && packets.get(index).constellation() == constellation
                    && Math.abs(packets.get(index).amount() - amount) < 0.0001F,
                    "Packet " + index + " must preserve constellation and carry " + amount + "; got " + packets);
        }
    }
}
