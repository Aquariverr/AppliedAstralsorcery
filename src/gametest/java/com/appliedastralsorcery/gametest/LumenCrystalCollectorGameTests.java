package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
import com.appliedastralsorcery.crystallizer.MELumenCrystalCollectorPart;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class LumenCrystalCollectorGameTests {
    @GameTest(template = "attunement_test")
    public static void collectorUsesNativePartPlacementPreviewAndNoMenuOnEveryFace(GameTestHelper helper) {
        var stack = new ItemStack(ModContent.ME_LUMEN_CRYSTAL_COLLECTOR_ITEM.get());
        helper.assertTrue(stack.getItem() instanceof IPartItem<?>,
                "Collector must be an AE2 part item to use the native placement preview");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var previous = player.getMainHandItem();
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            int index = 0;
            for (var face : Direction.values()) {
                var relative = new BlockPos(5 + (index % 3) * 5, 4, 5 + (index / 3) * 5);
                index++;
                var pos = helper.absolutePos(relative);
                placeCable(helper, pos);
                var click = pos.getCenter().add(face.getStepX() * 0.5, face.getStepY() * 0.5,
                        face.getStepZ() * 0.5);
                var preview = PartPlacement.getPartPlacement(null, helper.getLevel(), stack, pos, face, click);
                helper.assertTrue(preview != null && preview.pos().equals(pos) && preview.side() == face,
                        "Native preview must select the collector's requested cable face " + face);
                var part = PartPlacement.placePart(null, helper.getLevel(),
                        ModContent.ME_LUMEN_CRYSTAL_COLLECTOR_ITEM.get(), null, preview.pos(), preview.side());
                var host = PartHelper.getPartHost(helper.getLevel(), pos);
                helper.assertTrue(part != null && host != null && host.getPart(face) == part
                                && host.getPart(null) != null && part.getCollectionTarget().equals(pos.relative(face)),
                        "Collector must coexist with its cable and target exactly the block in front");
                List<AABB> boxes = new ArrayList<>();
                part.getBoxes(new BusCollisionHelper(boxes, face, true));
                helper.assertTrue(!boxes.isEmpty() && boxes.stream().allMatch(box -> touchesOuterFace(box, face))
                                && !part.getStaticModels().getModels().isEmpty(),
                        "Each collector orientation must expose a native thin-panel outline and static model");
                helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                        new BlockHitResult(click, face, pos, false));
                helper.assertTrue(player.containerMenu == player.inventoryMenu,
                        "Collector interaction must not open a menu");
            }
        } finally {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void collectorPoweredSchedulerHarvestsEveryStageAndPreservesTypedSeed(GameTestHelper helper) {
        var part = placeCollector(helper, new BlockPos(10, 4, 10), Direction.EAST, true);
        var inventory = new Storage();
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        var original = new AtomicReference<TileLumenCrystalCluster>();
        helper.startSequence()
                .thenWaitUntil(() -> assertOnline(helper, part))
                .thenExecute(() -> {
                    part.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
                    original.set(placeCluster(helper, part.getCollectionTarget(), 4));
                })
                .thenWaitUntil(() -> helper.assertTrue(inventory.amount(crystalKey()) == 4,
                        "AE2's actual tick manager must collect one crystal from each of the four growth stages"))
                .thenExecute(() -> {
                    try {
                        assertCluster(helper, part.getCollectionTarget(), original.get(), 0);
                        for (int tick = 0; tick < 12; tick++) part.serverTick();
                        helper.assertTrue(inventory.amount(crystalKey()) == 4 && inventory.totalItems() == 4,
                                "A retained stage-zero crystal must never be harvested again");
                        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(),
                                "Harvested crystals must enter ME storage without spawning loose drops");
                    } finally {
                        part.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void collectorIgnoresOfflineGrowthStageZeroOrdinaryBlocksAndDroppedItems(GameTestHelper helper) {
        var relative = new BlockPos(10, 4, 10);
        var part = placeCollector(helper, relative, Direction.EAST, false);
        var target = part.getCollectionTarget();
        var original = placeCluster(helper, target, 1);
        part.serverTick();
        assertCluster(helper, target, original, 1);
        helper.assertTrue(!part.getMainNode().isOnline(), "The unpowered fixture must be offline");
        helper.setBlock(relative.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var inventory = new Storage();
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        helper.startSequence().thenWaitUntil(() -> assertOnline(helper, part)).thenExecute(() -> {
            var service = part.getMainNode().getGrid().getStorageService();
            service.addGlobalStorageProvider(provider);
            try {
                part.serverTick();
                assertCluster(helper, target, original, 0);
                helper.assertTrue(inventory.amount(crystalKey()) == 1,
                        "A stage-one cluster must yield one crystal and keep its seed");
                part.serverTick();
                helper.getLevel().setBlockAndUpdate(target, Blocks.DIAMOND_BLOCK.defaultBlockState());
                var center = target.getCenter();
                var dropped = new ItemEntity(helper.getLevel(), center.x, center.y, center.z,
                        new ItemStack(Items.DIAMOND, 3));
                dropped.setNoGravity(true);
                dropped.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(helper.getLevel().addFreshEntity(dropped), "The dropped-item fixture must spawn");
                for (int tick = 0; tick < 5; tick++) part.serverTick();
                helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.DIAMOND_BLOCK)
                                && !dropped.isRemoved() && dropped.getItem().getCount() == 3
                                && inventory.totalItems() == 1,
                        "The collector must ignore ordinary blocks and nearby dropped items");
                dropped.discard();
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void collectorFullStorageAndCancelledBreakLeaveCrystalUntouched(GameTestHelper helper) {
        var part = placeCollector(helper, new BlockPos(10, 4, 10), Direction.EAST, true);
        var inventory = new Storage();
        inventory.acceptSimulation = false;
        inventory.acceptModulation = false;
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        helper.startSequence().thenWaitUntil(() -> assertOnline(helper, part)).thenExecute(() -> {
            var service = part.getMainNode().getGrid().getStorageService();
            service.addGlobalStorageProvider(provider);
            var target = part.getCollectionTarget();
            var original = placeCluster(helper, target, 2);
            try {
                for (int tick = 0; tick < 3; tick++) part.serverTick();
                assertCluster(helper, target, original, 2);
                helper.assertTrue(inventory.totalItems() == 0,
                        "A full ME inventory must not decrease the crystal's stage");
                inventory.acceptSimulation = true;
                inventory.acceptModulation = true;
                Consumer<BlockEvent.BreakEvent> protection = event -> {
                    if (event.getLevel() == helper.getLevel() && event.getPos().equals(target)) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(protection);
                try {
                    part.serverTick();
                    assertCluster(helper, target, original, 2);
                    helper.assertTrue(inventory.totalItems() == 0,
                            "A cancelled block-break event must forbid both stage reduction and collection");
                } finally {
                    NeoForge.EVENT_BUS.unregister(protection);
                }
                part.serverTick();
                assertCluster(helper, target, original, 1);
                helper.assertTrue(inventory.amount(crystalKey()) == 1,
                        "After space and permission are restored, one tick must harvest exactly one stage");
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 240)
    public static void collectorRefusedActualInsertionSurvivesDismantleWithoutLossOrDuplicate(GameTestHelper helper) {
        var relative = new BlockPos(10, 4, 10);
        var part = placeCollector(helper, relative, Direction.EAST, true);
        var inventory = new Storage();
        inventory.acceptModulation = false;
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        var restored = new AtomicReference<MELumenCrystalCollectorPart>();
        var crystal = new AtomicReference<TileLumenCrystalCluster>();
        helper.startSequence().thenWaitUntil(() -> assertOnline(helper, part)).thenExecute(() -> {
            part.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
            var target = part.getCollectionTarget();
            crystal.set(placeCluster(helper, target, 3));
            part.serverTick();
            assertCluster(helper, target, crystal.get(), 2);
            helper.assertTrue(inventory.totalItems() == 0,
                    "A refused real insertion must retain the harvested item in the part's buffer");
            for (int tick = 0; tick < 5; tick++) part.serverTick();
            assertCluster(helper, target, crystal.get(), 2);
            var carried = part.exportSettings(SettingsFrom.DISMANTLE_ITEM);
            var pos = part.getBlockEntity().getBlockPos();
            helper.assertTrue(part.getHost().removePart(part), "Dismantling must remove the original part");
            var replacement = PartPlacement.placePart(null, helper.getLevel(),
                    ModContent.ME_LUMEN_CRYSTAL_COLLECTOR_ITEM.get(), carried, pos, Direction.EAST);
            helper.assertTrue(replacement != null, "The dismantled collector must place back on its cable");
            restored.set(replacement);
        }).thenWaitUntil(() -> assertOnline(helper, restored.get())).thenExecute(() -> {
            var replacement = restored.get();
            var service = replacement.getMainNode().getGrid().getStorageService();
            try {
                assertCluster(helper, replacement.getCollectionTarget(), crystal.get(), 2);
                inventory.acceptModulation = true;
                replacement.serverTick();
                assertCluster(helper, replacement.getCollectionTarget(), crystal.get(), 2);
                helper.assertTrue(inventory.amount(crystalKey()) == 1,
                        "The restored pending output must be returned once before harvesting another stage");
                replacement.serverTick();
                assertCluster(helper, replacement.getCollectionTarget(), crystal.get(), 1);
                replacement.serverTick();
                assertCluster(helper, replacement.getCollectionTarget(), crystal.get(), 0);
                for (int tick = 0; tick < 8; tick++) replacement.serverTick();
                helper.assertTrue(inventory.amount(crystalKey()) == 3 && inventory.totalItems() == 3,
                        "Three harvested stages must yield exactly three items across refusal and replacement");
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void collectorAndCrystallizerRegrowTheSameSeedWithoutAnotherCatalyst(GameTestHelper helper) {
        var collector = placeCollector(helper, new BlockPos(10, 4, 10), Direction.EAST, true);
        var target = collector.getCollectionTarget();
        for (int x = 10; x <= 12; x++) placeCable(helper, helper.absolutePos(new BlockPos(x, 4, 11)));
        var growerPos = helper.absolutePos(new BlockPos(12, 4, 10));
        placeCable(helper, growerPos);
        MELumenCrystallizerPart grower = PartPlacement.placePart(null, helper.getLevel(),
                ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get(), null, growerPos, Direction.WEST);
        helper.assertTrue(grower != null && grower.getCrystallizationTarget().equals(target),
                "The actual growing and harvesting panels must face the same world position");
        var inventory = new Storage();
        inventory.amounts.put(crystalKey(), 1L);
        inventory.amounts.put(LumenKey.of(LumenAS.AEVITAS.get()), 100_000L);
        IStorageProvider provider = mounts -> mounts.mount(inventory);
        helper.startSequence().thenWaitUntil(() -> {
            assertOnline(helper, collector);
            helper.assertTrue(grower.getMainNode().isOnline(), "The crystallizer must share the powered ME network");
        }).thenExecute(() -> {
            var service = collector.getMainNode().getGrid().getStorageService();
            service.addGlobalStorageProvider(provider);
            try {
                grower.markCatalyst(crystalStack());
                helper.getLevel().random.setSeed(successSeed(WorldCrystallization.SEED_CHANCE));
                grower.serverTick();
                var seed = (TileLumenCrystalCluster) helper.getLevel().getBlockEntity(target);
                assertCluster(helper, target, seed, 0);
                helper.assertTrue(inventory.extracted.getOrDefault(crystalKey(), 0L) == 1,
                        "The first world seed must consume one marked catalyst");
                for (int cycle = 0; cycle < 3; cycle++) {
                    helper.getLevel().random.setSeed(successSeed(WorldCrystallization.GROWTH_CHANCE));
                    grower.serverTick();
                    assertCluster(helper, target, seed, 1);
                    collector.serverTick();
                    assertCluster(helper, target, seed, 0);
                }
                helper.assertTrue(inventory.extracted.getOrDefault(crystalKey(), 0L) == 1
                                && inventory.amount(crystalKey()) == 3,
                        "Three regrowth/collection cycles must reuse the seed and never consume another catalyst");
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }

    private static MELumenCrystalCollectorPart placeCollector(GameTestHelper helper, BlockPos relative,
            Direction side, boolean powered) {
        if (powered) helper.setBlock(relative.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var pos = helper.absolutePos(relative);
        placeCable(helper, pos);
        var part = PartPlacement.placePart(null, helper.getLevel(),
                ModContent.ME_LUMEN_CRYSTAL_COLLECTOR_ITEM.get(), null, pos, side);
        helper.assertTrue(part != null, "Native collector placement must succeed");
        return part;
    }

    private static void placeCable(GameTestHelper helper, BlockPos pos) {
        var cable = PartPlacement.placePart(null, helper.getLevel(), AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT),
                null, pos, null);
        helper.assertTrue(cable != null, "The native cable fixture must place");
    }

    private static TileLumenCrystalCluster placeCluster(GameTestHelper helper, BlockPos pos, int stage) {
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, BlocksAS.LUMEN_CRYSTAL_CLUSTER.get().defaultBlockState()
                .setValue(LumenCrystalClusterBlock.STAGE, stage));
        var tile = (TileLumenCrystalCluster) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(tile != null, "The native cluster block must create its real tile");
        tile.getTileData().setLumen(LumenAS.AEVITAS.get());
        tile.getTileData().markForUpdate();
        return tile;
    }

    private static void assertOnline(GameTestHelper helper, MELumenCrystalCollectorPart part) {
        helper.assertTrue(part.getMainNode().isOnline(), "The collector must receive grid power and a channel");
    }

    private static void assertCluster(GameTestHelper helper, BlockPos pos, TileLumenCrystalCluster original, int stage) {
        var state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(original != null && helper.getLevel().getBlockEntity(pos) == original
                        && state.is(BlocksAS.LUMEN_CRYSTAL_CLUSTER.get())
                        && state.getValue(LumenCrystalClusterBlock.STAGE) == stage
                        && original.getTileData().getLumen() == LumenAS.AEVITAS.get(),
                "The same native typed crystal tile must remain at stage " + stage);
    }

    private static boolean touchesOuterFace(AABB box, Direction face) {
        double min = face.getAxis().choose(box.minX, box.minY, box.minZ);
        double max = face.getAxis().choose(box.maxX, box.maxY, box.maxZ);
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? min >= 0.75 && max == 1 : min == 0 && max <= 0.25;
    }

    private static ItemStack crystalStack() { return LumenCrystalItem.getCrystal(LumenAS.AEVITAS); }
    private static AEItemKey crystalKey() { return AEItemKey.of(crystalStack()); }

    private static long successSeed(int bound) {
        long seed = 0;
        while (RandomSource.create(seed).nextInt(bound) != 0) seed++;
        return seed;
    }

    private static final class Storage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final Map<AEKey, Long> extracted = new HashMap<>();
        private boolean acceptSimulation = true;
        private boolean acceptModulation = true;
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        long totalItems() { return amounts.entrySet().stream().filter(entry -> entry.getKey() instanceof AEItemKey)
                .mapToLong(Map.Entry::getValue).sum(); }
        @Override public Component getDescription() { return Component.literal("Lumen collector test storage"); }
        @Override public void getAvailableStacks(KeyCounter out) { amounts.forEach(out::add); }
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long taken = Math.min(amount(key), requested);
            if (mode == Actionable.MODULATE) {
                amounts.put(key, amount(key) - taken);
                extracted.merge(key, taken, Long::sum);
            }
            return taken;
        }
        @Override public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (mode == Actionable.SIMULATE ? !acceptSimulation : !acceptModulation) return 0;
            if (mode == Actionable.MODULATE) amounts.merge(key, requested, Long::sum);
            return requested;
        }
    }
}
