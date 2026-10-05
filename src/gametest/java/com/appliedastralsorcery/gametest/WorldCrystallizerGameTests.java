package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntUnaryOperator;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.BusCollisionHelper;
import appeng.parts.PartPlacement;
import appeng.util.SettingsFrom;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerPart;
import com.appliedastralsorcery.crystallizer.WorldCrystallization;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.block.tile.LumenCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.item.LumenCrystalItem;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.tile.TileLumenCrystalCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class WorldCrystallizerGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final long INITIAL_LUMEN = 100_000;
    private static final LumenKey LUMEN = LumenKey.of(LumenAS.AEVITAS.get());

    @GameTest(template = "attunement_test")
    public static void crystallizerUsesNativePartPlacementAndPreviewOnEveryFace(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get());
        helper.assertTrue(stack.getItem() instanceof IPartItem<?>,
                "Crystallizer item must enter AE2's native part placement and preview path");
        int index = 0;
        for (var face : Direction.values()) {
            var pos = helper.absolutePos(new BlockPos(5 + (index % 3) * 5, 4, 5 + (index / 3) * 5));
            index++;
            placeCable(helper, pos);
            var click = pos.getCenter().add(face.getStepX() * 0.5, face.getStepY() * 0.5,
                    face.getStepZ() * 0.5);
            var preview = PartPlacement.getPartPlacement(null, helper.getLevel(), stack, pos, face, click);
            helper.assertTrue(preview != null && preview.pos().equals(pos) && preview.side() == face,
                    "Native placement preview must select the requested cable face " + face);
            var panel = PartPlacement.placePart(null, helper.getLevel(), ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get(),
                    null, preview.pos(), preview.side());
            var host = PartHelper.getPartHost(helper.getLevel(), pos);
            helper.assertTrue(panel != null && host != null && host.getPart(face) == panel
                            && host.getPart(null) != null,
                    "Crystallizer and cable must coexist in AE2's native part host on " + face);
            List<AABB> boxes = new ArrayList<>();
            panel.getBoxes(new BusCollisionHelper(boxes, face, true));
            helper.assertTrue(!boxes.isEmpty() && boxes.stream().allMatch(box -> touchesOuterFace(box, face)),
                    "The preview outline must be a thin panel on the outward " + face + " face");
            helper.assertTrue(!panel.getStaticModels().getModels().isEmpty()
                            && panel.getCrystallizationTarget().equals(pos.relative(face)),
                    "Part model and crystal target must use the native placement orientation");
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerNativeCableInteractionMarksAndClearsWithoutMenu(GameTestHelper helper) {
        var panel = placePanel(helper, new BlockPos(10, 4, 10), Direction.EAST);
        var level = helper.getLevel();
        var pos = panel.getBlockEntity().getBlockPos();
        var player = FakePlayerFactory.getMinecraft(level);
        var previous = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        try {
            var sample = crystal();
            sample.setCount(8);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, sample);
            var hit = new BlockHitResult(pos.getCenter().add(0.5, 0, 0), Direction.EAST, pos, false);
            var state = level.getBlockState(pos);
            state.useItemOn(sample, level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sample.getCount() == 8 && panel.getMarker().getCount() == 1
                            && ItemStack.isSameItemSameComponents(sample, panel.getMarker()),
                    "Cable bus interaction must mark a ghost catalyst without taking the held item");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            state.useWithoutItem(level, player, hit);
            helper.assertTrue(panel.getMarker().isEmpty() && player.containerMenu == player.inventoryMenu,
                    "Empty-hand sneak interaction must clear the actual part without opening a menu");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setShiftKeyDown(sneaking);
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerSeedsRealTypedClustersInFrontOfAllSixFaces(GameTestHelper helper) {
        List<BlockPos> targets = new ArrayList<>();
        int index = 0;
        for (var face : Direction.values()) {
            var panel = placePanel(helper, new BlockPos(5 + (index % 3) * 5, 4, 5 + (index / 3) * 5), face);
            index++;
            var engine = new WorldCrystallization();
            var inventory = filledStorage(1);
            helper.assertTrue(engine.markCatalyst(helper.getLevel(), crystal()) == LumenAS.AEVITAS.get(),
                    "The native component-sensitive recipe must select Aevitas");
            var target = panel.getCrystallizationTarget();
            engine.tick(helper.getLevel(), target, inventory, SOURCE, successfulRoll());
            assertCluster(helper, target, 0);
            helper.assertTrue(helper.getLevel().getBlockState(target).canSurvive(helper.getLevel(), target),
                    "The native cluster must recognize the adjacent panel as support on " + face);
            helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0,
                    "Creating a real cluster must consume its catalyst");
            targets.add(target);
        }
        helper.startSequence().thenExecuteAfter(2, () -> {
            for (var target : targets) assertCluster(helper, target, 0);
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerReloadAndClearKeepRealGrowthWithoutAutoHarvest(GameTestHelper helper) {
        var panel = placePanel(helper, new BlockPos(10, 4, 10), Direction.EAST);
        var target = panel.getCrystallizationTarget();
        var level = helper.getLevel();
        var inventory = filledStorage(1);
        var engine = new WorldCrystallization();
        engine.markCatalyst(level, crystal());
        int operationCost = engine.findRecipe(level, crystal()).value().getLumenConsumedPerOperation();
        engine.tick(level, target, inventory, SOURCE, successfulRoll());
        assertCluster(helper, target, 0);
        var originalCluster = level.getBlockEntity(target);
        var restored = new WorldCrystallization();
        restored.load(engine.save(level.registryAccess(), true), level.registryAccess(), true);
        restored.clearMarker();
        for (int stage = 1; stage <= 4; stage++) {
            restored.tick(level, target, inventory, SOURCE, successfulRoll());
            assertCluster(helper, target, stage);
        }
        for (int tick = 0; tick < 30; tick++) restored.tick(level, target, inventory, SOURCE, successfulRoll());
        helper.assertTrue(level.getBlockEntity(target) == originalCluster && restored.getMarker().isEmpty(),
                "Reload and marker clear must preserve the same real growing crystal through maturity");
        helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0
                        && inventory.amount(LUMEN) == INITIAL_LUMEN - 5L * operationCost,
                "A mature world crystal must stop drawing resources and must not become an ME item");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(),
                "World crystallization must not harvest or emit hidden item drops");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerBlockedTargetAndCancelledPartialSeedDoNotLoseResources(GameTestHelper helper) {
        var panel = placePanel(helper, new BlockPos(10, 4, 10), Direction.EAST);
        var target = panel.getCrystallizationTarget();
        var level = helper.getLevel();
        var engine = new WorldCrystallization();
        var marker = crystal();
        marker.set(DataComponents.CUSTOM_NAME, Component.literal("Exact marked catalyst"));
        var exact = AEItemKey.of(marker);
        var inventory = filledStorage(3);
        inventory.amounts.put(exact, 1L);
        engine.markCatalyst(level, marker);
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        engine.tick(level, target, inventory, SOURCE, successfulRoll());
        helper.assertTrue(level.getBlockState(target).is(Blocks.STONE) && inventory.amount(exact) == 1
                        && inventory.amount(LUMEN) == INITIAL_LUMEN,
                "An obstructed target must not be overwritten or charged for a seed");
        level.removeBlock(target, false);
        inventory.amounts.put(exact, 0L);
        engine.tick(level, target, inventory, SOURCE, successfulRoll());
        helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 3
                        && inventory.amount(LUMEN) == INITIAL_LUMEN && level.isEmptyBlock(target),
                "A plain catalyst must not substitute for the exact components used to mark the panel");
        inventory.amounts.put(exact, 1L);
        inventory.maxLumenExtraction = 1;
        engine.tick(level, target, inventory, SOURCE, successfulRoll());
        helper.assertTrue(inventory.amount(exact) == 0 && inventory.amount(LUMEN) == INITIAL_LUMEN - 1
                        && level.isEmptyBlock(target),
                "Partial extraction must reserve the real catalyst and lumen without fabricating a cluster");
        var restored = new WorldCrystallization();
        restored.load(engine.save(level.registryAccess(), true), level.registryAccess(), true);
        restored.clearMarker();
        for (int tick = 0; tick < 3; tick++) restored.tick(level, target, inventory, SOURCE, successfulRoll());
        helper.assertTrue(restored.getMarker().isEmpty() && inventory.amount(exact) == 1
                        && inventory.amount(LUMEN) == INITIAL_LUMEN && level.isEmptyBlock(target),
                "Clearing a reloaded unformed seed must refund every reserved resource exactly once");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerReseedsRemovedWorldClusterUsingAnotherCatalyst(GameTestHelper helper) {
        var panel = placePanel(helper, new BlockPos(10, 4, 10), Direction.WEST);
        var target = panel.getCrystallizationTarget();
        var level = helper.getLevel();
        var inventory = filledStorage(2);
        var engine = new WorldCrystallization();
        engine.markCatalyst(level, crystal());
        engine.tick(level, target, inventory, SOURCE, successfulRoll());
        assertCluster(helper, target, 0);
        var originalCluster = level.getBlockEntity(target);
        var restored = new WorldCrystallization();
        restored.load(engine.save(level.registryAccess(), true), level.registryAccess(), true);
        helper.assertTrue(level.destroyBlock(target, false), "The existing real cluster must be removable");
        for (int tick = 0; tick < 3 && level.isEmptyBlock(target); tick++)
            restored.tick(level, target, inventory, SOURCE, successfulRoll());
        assertCluster(helper, target, 0);
        helper.assertTrue(level.getBlockEntity(target) != originalCluster
                        && inventory.amount(AEItemKey.of(crystal())) == 0,
                "After external harvesting, another real seed must consume a new catalyst");
        helper.succeed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void crystallizerPoweredCableTicksAndGrowsThroughActualMEStorage(GameTestHelper helper) {
        var relative = new BlockPos(10, 4, 10);
        helper.setBlock(relative.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var panel = placePanel(helper, relative, Direction.EAST);
        var level = helper.getLevel();
        var target = panel.getCrystallizationTarget();
        var inventory = filledStorage(1);
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(panel.getMainNode().isOnline(),
                        "The cable-mounted panel must receive grid power and a channel"))
                .thenExecute(() -> {
                    panel.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
                    panel.markCatalyst(crystal());
                })
                .thenWaitUntil(() -> helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0,
                        "AE2's tick manager must reserve the marked catalyst without a manual serverTick call"))
                .thenExecute(() -> {
                    try {
                        if (level.isEmptyBlock(target)) {
                            level.random.setSeed(successSeed(WorldCrystallization.SEED_CHANCE));
                            panel.serverTick();
                        }
                        assertCluster(helper, target, 0);
                        for (int stage = 1; stage <= 4; stage++) {
                            level.random.setSeed(successSeed(WorldCrystallization.GROWTH_CHANCE));
                            panel.serverTick();
                            assertCluster(helper, target, stage);
                        }
                        panel.serverTick();
                        long afterMaturity = inventory.amount(LUMEN);
                        for (int tick = 0; tick < 25; tick++) panel.serverTick();
                        helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0
                                        && inventory.amount(LUMEN) == afterMaturity,
                                "Actual ME storage must retain the mature crystal in the world without auto-harvest");
                        helper.assertTrue(PartHelper.getPartHost(level, panel.getBlockEntity().getBlockPos())
                                        .getPart(Direction.EAST) == panel,
                                "Growing the native cluster must preserve the cable-mounted panel");
                    } finally {
                        panel.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void crystallizerDismantlePreservesReservationsButMemoryCardDoesNotCopyThem(GameTestHelper helper) {
        var relative = new BlockPos(10, 4, 10);
        helper.setBlock(relative.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var original = placePanel(helper, relative, Direction.EAST);
        var copy = placePanel(helper, relative.south(), Direction.EAST);
        var restored = new AtomicReference<MELumenCrystallizerPart>();
        var inventory = filledStorage(1);
        inventory.maxLumenExtraction = 1;
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(original.getMainNode().isOnline()
                                && copy.getMainNode().isOnline(),
                        "Both panels must share the powered native cable network"))
                .thenExecute(() -> {
                    var service = original.getMainNode().getGrid().getStorageService();
                    service.addGlobalStorageProvider(provider);
                    original.markCatalyst(crystal());
                    original.serverTick();
                    helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0
                                    && inventory.amount(LUMEN) == INITIAL_LUMEN - 1,
                            "The real part must reserve a catalyst and a partial lumen transfer before dismantling");
                    var carried = original.exportSettings(SettingsFrom.DISMANTLE_ITEM);
                    var clipboard = original.exportSettings(SettingsFrom.MEMORY_CARD);
                    copy.importSettings(SettingsFrom.MEMORY_CARD, clipboard, null);
                    helper.assertTrue(ItemStack.isSameItemSameComponents(copy.getMarker(), crystal()),
                            "Memory-card transfer must copy the exact catalyst marker");
                    copy.clearMarker();
                    copy.serverTick();
                    helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 0
                                    && inventory.amount(LUMEN) == INITIAL_LUMEN - 1,
                            "Clearing a memory-card copy must not refund or duplicate another panel's reservations");
                    var pos = original.getBlockEntity().getBlockPos();
                    helper.assertTrue(original.getHost().removePart(original),
                            "Native part dismantling must remove the original panel from its host");
                    var placed = PartPlacement.placePart(null, helper.getLevel(),
                            ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get(), carried, pos, Direction.EAST);
                    helper.assertTrue(placed != null && ItemStack.isSameItemSameComponents(placed.getMarker(), crystal()),
                            "Native part replacement must import the dismantled marker and reserved materials");
                    restored.set(placed);
                    placed.clearMarker();
                })
                .thenWaitUntil(() -> helper.assertTrue(restored.get().getMainNode().isOnline(),
                        "The replaced native part must reconnect before returning reserved materials"))
                .thenExecute(() -> {
                    try {
                        restored.get().serverTick();
                        for (int tick = 0; tick < 3; tick++) {
                            restored.get().serverTick();
                            copy.serverTick();
                        }
                        helper.assertTrue(inventory.amount(AEItemKey.of(crystal())) == 1
                                        && inventory.amount(LUMEN) == INITIAL_LUMEN,
                                "Dismantling must preserve and return resources once, while clipboard copies return none");
                    } finally {
                        copy.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    private static MELumenCrystallizerPart placePanel(GameTestHelper helper, BlockPos relative, Direction face) {
        var pos = helper.absolutePos(relative);
        placeCable(helper, pos);
        var part = PartPlacement.placePart(null, helper.getLevel(), ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get(),
                null, pos, face);
        helper.assertTrue(part != null, "Native crystallizer part placement must succeed");
        return part;
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = PartPlacement.placePart(null, helper.getLevel(), AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT),
                null, pos, null);
        helper.assertTrue(cable != null, "Native cable part placement must succeed");
    }

    private static void assertCluster(GameTestHelper helper, BlockPos pos, int stage) {
        var state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.is(BlocksAS.LUMEN_CRYSTAL_CLUSTER.get())
                        && state.getValue(LumenCrystalClusterBlock.STAGE) == stage,
                "Expected an actual Astral Sorcery world cluster at stage " + stage);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof TileLumenCrystalCluster cluster
                        && cluster.getTileData().getLumen() == LumenAS.AEVITAS.get(),
                "The native crystal tile must carry the catalyst's lumen type");
    }

    private static boolean touchesOuterFace(AABB box, Direction face) {
        double minimum = face.getAxis().choose(box.minX, box.minY, box.minZ);
        double maximum = face.getAxis().choose(box.maxX, box.maxY, box.maxZ);
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? minimum >= 0.875 && maximum == 1 : minimum == 0 && maximum <= 0.125;
    }

    private static ItemStack crystal() { return LumenCrystalItem.getCrystal(LumenAS.AEVITAS); }

    private static Storage filledStorage(int catalysts) {
        var inventory = new Storage();
        inventory.amounts.put(AEItemKey.of(crystal()), (long) catalysts);
        inventory.amounts.put(LUMEN, INITIAL_LUMEN);
        return inventory;
    }

    private static long successSeed(int bound) {
        long seed = 0;
        while (RandomSource.create(seed).nextInt(bound) != 0) seed++;
        return seed;
    }

    private static IntUnaryOperator successfulRoll() {
        return bound -> RandomSource.create(successSeed(bound)).nextInt(bound);
    }

    private static final class Storage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private long maxLumenExtraction = Long.MAX_VALUE;
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        @Override public Component getDescription() { return Component.literal("World crystallizer test storage"); }
        @Override public void getAvailableStacks(KeyCounter out) { amounts.forEach(out::add); }
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long taken = Math.min(amount(key), requested);
            if (key instanceof LumenKey) taken = Math.min(taken, maxLumenExtraction);
            if (mode == Actionable.MODULATE) amounts.put(key, amount(key) - taken);
            return taken;
        }
        @Override public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) amounts.merge(key, requested, Long::sum);
            return requested;
        }
    }
}
