package com.appliedastralsorcery.gametest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.tree.METreeBeaconBlockEntity;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.tile.TileTranslucentTree;
import hellfirepvp.astralsorcery.common.tile.TileTreeBeacon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TreeBeaconGameTests {
    private static final BlockPos BEACON = new BlockPos(10, 2, 10);
    private static final BlockPos TREE = new BlockPos(14, 2, 10);

    @GameTest(template = "attunement_test")
    public static void offlineBeaconRetainsNativeHarvestAndTreeDecay(GameTestHelper helper) {
        helper.setBlock(BEACON.below(), Blocks.STONE);
        var beacon = placeBeacon(helper);
        harvestOneLog(helper, beacon);
        helper.assertTrue(!beacon.getMainNode().isOnline(), "Unpowered beacon must remain offline");
        helper.assertTrue(droppedLogs(helper) == 1, "Offline native harvest must drop exactly one log");
        helper.succeed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void onlineBeaconExportsOwnHarvestAndDropsRejectedOutput(GameTestHelper helper) {
        helper.setBlock(BEACON.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var beacon = placeBeacon(helper);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(beacon.getMainNode().isOnline(),
                "Beacon must obtain power and a channel from its physical ME connection")).thenExecute(() -> {
            var storage = new OneLogStorage();
            IStorageProvider provider = mounts -> mounts.mount(storage);
            var service = beacon.getMainNode().getGrid().getStorageService();
            service.addGlobalStorageProvider(provider);
            try {
                var center = beacon.getBlockPos().getCenter();
                var unrelated = new ItemEntity(helper.getLevel(), center.x, center.y, center.z,
                        new ItemStack(Items.DIAMOND));
                helper.assertTrue(helper.getLevel().addFreshEntity(unrelated), "Unrelated drop must spawn");
                harvestOneLog(helper, beacon);
                helper.assertTrue(storage.stored == 1 && droppedLogs(helper) == 0,
                        "Accepted harvest must enter ME without spawning a duplicate");
                harvestOneLog(helper, beacon);
                helper.assertTrue(storage.stored == 1 && droppedLogs(helper) == 1,
                        "A full network must preserve rejected output as a native drop");
                helper.assertTrue(!unrelated.isRemoved() && unrelated.getItem().is(Items.DIAMOND),
                        "Beacon must never vacuum unrelated nearby items");
            } finally {
                service.removeGlobalStorageProvider(provider);
            }
        }).thenSucceed();
    }

    private static METreeBeaconBlockEntity placeBeacon(GameTestHelper helper) {
        helper.setBlock(BEACON, ModContent.ME_TREE_BEACON.get());
        return (METreeBeaconBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BEACON));
    }

    private static void harvestOneLog(GameTestHelper helper, METreeBeaconBlockEntity beacon) {
        helper.setBlock(TREE, BlocksAS.TRANSLUCENT_TREE.get());
        var tree = (TileTranslucentTree) helper.getLevel().getBlockEntity(helper.absolutePos(TREE));
        tree.getTileData().setStoredState(Blocks.OAK_LOG.defaultBlockState());
        tree.getTileData().setTreeBeaconPos(beacon.getBlockPos());
        beacon.getTileData().addTreeComponent(tree.getBlockPos(), 1);
        beacon.getTileData().setStoredStarlight(0);
        int pivot = TileTreeBeacon.CONFIG.productionPivot.get();
        int harvestChance = TileTreeBeacon.CONFIG.harvestChance.get();
        int breakChance = TileTreeBeacon.CONFIG.breakChance.get();
        try {
            // One guaranteed native cycle, with its normal decay path; restore before any other test ticks.
            TileTreeBeacon.CONFIG.productionPivot.set(1);
            TileTreeBeacon.CONFIG.harvestChance.set(1);
            TileTreeBeacon.CONFIG.breakChance.set(1);
            beacon.serverTick(helper.getLevel());
            helper.assertTrue(helper.getLevel().isEmptyBlock(tree.getBlockPos())
                            && beacon.getTileData().getTreeComponents().isEmpty(),
                    "Native tree decay must still remove the captured block and bookkeeping");
        } finally {
            TileTreeBeacon.CONFIG.productionPivot.set(pivot);
            TileTreeBeacon.CONFIG.harvestChance.set(harvestChance);
            TileTreeBeacon.CONFIG.breakChance.set(breakChance);
        }
    }

    private static int droppedLogs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                entity -> entity.getItem().is(Items.OAK_LOG)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static final class OneLogStorage implements MEStorage {
        private long stored;

        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!AEItemKey.of(Items.OAK_LOG).equals(what)) return 0;
            long accepted = Math.min(amount, 1 - stored);
            if (mode == Actionable.MODULATE) stored += accepted;
            return accepted;
        }

        @Override public Component getDescription() { return Component.literal("Tree beacon test storage"); }
    }
}
