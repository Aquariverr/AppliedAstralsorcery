package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.spatial.SpatialStoragePlot;
import appeng.spatial.SpatialStoragePlotManager;
import com.appliedastralsorcery.gateway.SpatialCellAccess;
import com.appliedastralsorcery.gateway.SpatialReturnPortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceGatewayGameTests {
    private static final String SEARCH = "appliedas_return_portal_search";
    private static final String CURSOR = "appliedas_gateway_format_cursor";
    private static final String ELAPSED = "appliedas_gateway_format_ticks";
    private static final String VERSION = "appliedas_gateway_format_version";

    @GameTest(template = "attunement_test", batch = "gateway_search_order", timeoutTicks = 5000)
    public static void portalSearchSkipsBlockedSpaceAndReusesExistingPortal(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var plot = manager.allocatePlot(new BlockPos(6, 6, 6), -1);
        var level = manager.getLevel();
        var origin = plot.getOrigin();
        var cell = cell(plot);
        fill(plot, Blocks.STONE);
        helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(cell, plot) == null && searchCursor(cell) == 64,
                "A full plot must complete its bounded pass without changing blocks");
        level.setBlock(origin.offset(1, 0, 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        var expected = origin.offset(2, 1, 1);
        clearArrival(expected);
        helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(cell, plot) == null,
                "An unsuccessful pass must respect its retry delay");
        var found = new AtomicReference<SpatialReturnPortalBlockEntity>();
        helper.startSequence()
                .thenExecuteAfter(SpatialReturnPortalBlockEntity.FAILED_SEARCH_RETRY_TICKS + 1, () -> {})
                // The idle retry interval outlives the work ticket: allow asynchronous reloading.
                .thenWaitUntil(() -> {
                    var portal = SpatialReturnPortalBlockEntity.prepare(cell, plot);
                    helper.assertTrue(portal != null && portal.getBlockPos().equals(expected),
                            "A later pass must recover edits and pick the first candidate with a floor and headroom");
                    found.set(portal);
                    helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(cell, plot) == portal,
                            "A remembered portal must be reused without scanning");
                    cell.remove(DataComponents.CUSTOM_DATA);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(cell, plot) == found.get(),
                            "An existing portal must be rediscovered when its marker is missing");
                    level.setBlock(expected.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    clearArrival(origin.offset(1, 0, 1));
                })
                .thenExecuteAfter(1, () -> {
                    var portal = SpatialReturnPortalBlockEntity.prepare(cell, plot);
                    helper.assertTrue(portal != null && portal.getBlockPos().equals(origin.offset(1, 0, 1)),
                            "Blocking a remembered portal must restart from the first candidate");
                    manager.freePlot(plot.getId(), true);
                }).thenSucceed();
    }

    @GameTest(template = "attunement_test", batch = "gateway_search_resume")
    public static void portalSearchResumesSavedCursorAndRejectsAnotherPlotsCursor(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var first = manager.allocatePlot(new BlockPos(20, 20, 20), -1);
        var second = manager.allocatePlot(new BlockPos(20, 20, 20), -1);
        fill(first, Blocks.STONE);
        fill(second, Blocks.STONE);
        var expected = candidate(first, 5000);
        clearArrival(expected);
        var otherExpected = candidate(second, 0);
        clearArrival(otherExpected);
        var original = cell(first);
        helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(original, first) == null,
                "A late arrival must not force a synchronous full scan");
        int cursor = searchCursor(original);
        helper.assertTrue(cursor > 0 && cursor <= SpatialReturnPortalBlockEntity.SEARCH_CANDIDATES_PER_TICK,
                "One step must persist at most its per-plot budget");
        var restored = ItemStack.parse(manager.getLevel().registryAccess(),
                original.save(manager.getLevel().registryAccess())).orElseThrow();
        var switched = restored.copy();
        AEItems.SPATIAL_CELL128.asItem().setStoredDimension(switched, second.getId(), second.getSize());
        helper.startSequence().thenExecuteAfter(1, () -> {
            var portal = SpatialReturnPortalBlockEntity.prepare(restored, first);
            helper.assertTrue(portal != null && portal.getBlockPos().equals(expected),
                    "Saved partial searches must resume in the original Y/Z/X order");
            var other = SpatialReturnPortalBlockEntity.prepare(switched, second);
            helper.assertTrue(other != null && other.getBlockPos().equals(otherExpected),
                    "Changing the plot component must discard another plot's cursor");
            manager.freePlot(first.getId(), true);
            manager.freePlot(second.getId(), true);
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", batch = "gateway_search_budget")
    public static void concurrentPortalSearchesShareOneTickBudgetAndKeepMakingProgress(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var plots = new ArrayList<SpatialStoragePlot>();
        var cells = new ArrayList<ItemStack>();
        for (int i = 0; i < 6; i++) {
            var plot = manager.allocatePlot(new BlockPos(20, 20, 20), -1);
            plots.add(plot);
            fill(plot, Blocks.STONE);
            cells.add(cell(plot));
        }
        int[] first = new int[cells.size()];
        int total = 0;
        for (int i = 0; i < cells.size(); i++) {
            SpatialReturnPortalBlockEntity.prepare(cells.get(i), plots.get(i));
            first[i] = searchCursor(cells.get(i));
            helper.assertTrue(first[i] <= SpatialReturnPortalBlockEntity.SEARCH_CANDIDATES_PER_TICK,
                    "Each plot must respect its candidate budget");
            total += first[i];
            SpatialReturnPortalBlockEntity.prepare(cells.get(i), plots.get(i));
            helper.assertTrue(searchCursor(cells.get(i)) == first[i],
                    "Repeated same-tick calls must not acquire another budget");
        }
        helper.assertTrue(total > 0 && total <= SpatialReturnPortalBlockEntity.GLOBAL_SEARCH_CANDIDATES,
                "Concurrent gateways must share one aggregate candidate limit");
        helper.startSequence().thenExecuteAfter(1, () -> {
            int advanced = 0;
            for (int i = 0; i < cells.size(); i++) {
                SpatialReturnPortalBlockEntity.prepare(cells.get(i), plots.get(i));
                int delta = searchCursor(cells.get(i)) - first[i];
                helper.assertTrue(delta > 0, "Previously delayed plots must receive a share next tick");
                advanced += delta;
            }
            helper.assertTrue(advanced <= SpatialReturnPortalBlockEntity.GLOBAL_SEARCH_CANDIDATES,
                    "Fair shares must retain the aggregate limit");
            plots.forEach(plot -> manager.freePlot(plot.getId(), true));
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", batch = "gateway_format_budget")
    public static void formattingSharesBlockBudgetAndReportsRealProgress(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var plots = new ArrayList<SpatialStoragePlot>();
        var cells = new ArrayList<ItemStack>();
        for (int i = 0; i < 3; i++) {
            var plot = manager.allocatePlot(new BlockPos(32, 32, 8), -1);
            plots.add(plot);
            fill(plot, AEBlocks.MATRIX_FRAME.block());
            var cell = cell(plot);
            setFormatting(cell, 0, 100, true);
            cells.add(cell);
        }
        int[] first = new int[cells.size()];
        int total = 0;
        for (int i = 0; i < cells.size(); i++) {
            SpatialCellAccess.formatStep(cells.get(i));
            first[i] = formatCursor(cells.get(i));
            helper.assertTrue(first[i] <= SpatialCellAccess.BLOCKS_PER_CELL_TICK,
                    "One formatter must obey its block budget");
            total += first[i];
            helper.assertTrue(SpatialCellAccess.isFormatting(cells.get(i))
                            && SpatialCellAccess.progress(cells.get(i)) == 100 * first[i] / 8192,
                    "Elapsed minimum time must not advertise uncleared blocks as complete");
            SpatialCellAccess.formatStep(cells.get(i));
            helper.assertTrue(formatCursor(cells.get(i)) == first[i], "Same-tick retries must not bypass the budget");
        }
        helper.assertTrue(total > 0 && total <= SpatialCellAccess.GLOBAL_BLOCK_BUDGET,
                "All formatters must share one aggregate block budget");
        helper.startSequence().thenExecuteAfter(1, () -> {
            int advanced = 0;
            for (int i = 0; i < cells.size(); i++) {
                SpatialCellAccess.formatStep(cells.get(i));
                int delta = formatCursor(cells.get(i)) - first[i];
                helper.assertTrue(delta > 0, "Every active formatter must receive work next tick");
                advanced += delta;
            }
            helper.assertTrue(advanced <= SpatialCellAccess.GLOBAL_BLOCK_BUDGET, "Fair shares must retain the global cap");
            plots.forEach(plot -> manager.freePlot(plot.getId(), true));
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", batch = "gateway_format_migration", timeoutTicks = 160)
    public static void formattingMigratesLegacyColumnsResumesAndPreservesNonMatrixBlocks(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var plot = manager.allocatePlot(new BlockPos(6, 6, 6), -1);
        fill(plot, AEBlocks.MATRIX_FRAME.block());
        var level = manager.getLevel();
        var preserved = plot.getOrigin().offset(1, 1, 1);
        level.setBlock(preserved, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        var original = cell(plot);
        setFormatting(original, 7, 40, false);
        helper.assertTrue(SpatialCellAccess.progress(original) == 19,
                "Legacy progress must reflect 7 completed columns out of 36");
        SpatialCellAccess.formatStep(original);
        helper.assertTrue(formatCursor(original) >= 42 && data(original).getInt(VERSION) == 2,
                "Legacy columns must migrate to the equivalent block cursor");
        helper.assertTrue(level.getBlockState(plot.getOrigin()).is(AEBlocks.MATRIX_FRAME.block()),
                "Migrated work must not revisit completed columns");
        var restored = ItemStack.parse(level.registryAccess(), original.save(level.registryAccess())).orElseThrow();
        helper.startSequence().thenWaitUntil(() -> {
            SpatialCellAccess.formatStep(restored);
            helper.assertTrue(!SpatialCellAccess.isFormatting(restored), "Resumed formatting must finish");
        }).thenExecute(() -> {
            helper.assertTrue(level.getBlockState(preserved).is(Blocks.DIAMOND_BLOCK),
                    "Clearing must preserve non-matrix blocks");
            helper.assertTrue(level.getBlockState(plot.getOrigin().offset(5, 5, 5)).isAir()
                            && SpatialCellAccess.progress(restored) == 100,
                    "The final block must be processed before reporting completion");
            manager.freePlot(plot.getId(), true);
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", batch = "gateway_format_minimum", timeoutTicks = 160)
    public static void smallFormattingStillTakesAtLeastOneHundredWorldTicks(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var plot = manager.allocatePlot(new BlockPos(6, 6, 6), -1);
        fill(plot, AEBlocks.MATRIX_FRAME.block());
        var cell = cell(plot);
        setFormatting(cell, 0, 0, true);
        var count = new AtomicInteger();
        helper.startSequence().thenExecuteFor(99, () -> {
            SpatialCellAccess.formatStep(cell);
            count.incrementAndGet();
            helper.assertTrue(SpatialCellAccess.isFormatting(cell) && SpatialCellAccess.progress(cell) < 100,
                    "Small spaces must remain formatting before the hundredth tick");
        }).thenExecute(() -> {
            SpatialCellAccess.formatStep(cell);
            helper.assertTrue(count.get() == 99 && !SpatialCellAccess.isFormatting(cell)
                            && SpatialCellAccess.progress(cell) == 100,
                    "An unobstructed small cell must finish on its hundredth tick");
            manager.freePlot(plot.getId(), true);
        }).thenSucceed();
    }

    // GameTest runs without a 50 ms tick delay; cold asynchronous generation needs wall-clock time.
    @GameTest(template = "attunement_test", batch = "gateway_unloaded_work", timeoutTicks = 5000)
    public static void unloadedGatewayWorkWaitsWithoutAdvancingItsCursors(GameTestHelper helper) {
        var manager = SpatialStoragePlotManager.INSTANCE;
        var allocated = new ArrayList<SpatialStoragePlot>();
        SpatialStoragePlot selected = null;
        for (int i = 0; i < 32; i++) {
            var plot = manager.allocatePlot(new BlockPos(128, 128, 128), -1);
            allocated.add(plot);
            var pos = plot.getOrigin().offset(120, 0, 120);
            if (manager.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                selected = plot;
                break;
            }
        }
        helper.assertTrue(selected != null, "The fixture needs an unloaded chunk");
        var plot = selected;
        var cell = cell(plot);
        int start = (120 * 128 + 120) * 128;
        setFormatting(cell, start, 100, true);
        SpatialCellAccess.formatStep(cell);
        helper.assertTrue(formatCursor(cell) == start, "An unloaded chunk must leave the exact cursor unchanged");
        var target = plot.getOrigin().offset(120, 0, 120);
        var searching = cell(plot);
        int searchStart = 119 * 126 + 119;
        CustomData.update(DataComponents.CUSTOM_DATA, searching, tag -> {
            var search = new net.minecraft.nbt.CompoundTag();
            search.putInt("plot", plot.getId());
            search.putLong("size", plot.getSize().asLong());
            search.putInt("cursor", searchStart);
            tag.put(SEARCH, search);
        });
        helper.assertTrue(SpatialReturnPortalBlockEntity.prepare(searching, plot) == null
                        && searchCursor(searching) == searchStart,
                "Search must also retain its candidate when its chunk is unloaded");
        helper.assertTrue(manager.getLevel().getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null,
                "A work step must not synchronously wait for chunk generation");
        helper.startSequence().thenWaitUntil(() -> {
            SpatialCellAccess.formatStep(cell);
            helper.assertTrue(formatCursor(cell) > start, "Work must resume when the requested chunk is ready");
            var portal = SpatialReturnPortalBlockEntity.prepare(searching, plot);
            helper.assertTrue(portal != null || searchCursor(searching) > searchStart,
                    "Search must resume its retained candidate after asynchronous loading");
        }).thenExecute(() -> allocated.forEach(p -> manager.freePlot(p.getId(), false))).thenSucceed();
    }

    private static ItemStack cell(SpatialStoragePlot plot) {
        var stack = AEItems.SPATIAL_CELL128.stack();
        AEItems.SPATIAL_CELL128.asItem().setStoredDimension(stack, plot.getId(), plot.getSize());
        return stack;
    }

    private static void fill(SpatialStoragePlot plot, Block block) {
        var level = SpatialStoragePlotManager.INSTANCE.getLevel();
        for (var pos : BlockPos.betweenClosed(plot.getOrigin(), plot.getOrigin().offset(plot.getSize()).offset(-1, -1, -1)))
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void clearArrival(BlockPos pos) {
        var level = SpatialStoragePlotManager.INSTANCE.getLevel();
        for (int y = 0; y < 3; y++) level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static BlockPos candidate(SpatialStoragePlot plot, int index) {
        int width = plot.getSize().getX() - 2, depth = plot.getSize().getZ() - 2;
        return plot.getOrigin().offset(1 + index % width, index / width / depth, 1 + index / width % depth);
    }

    private static net.minecraft.nbt.CompoundTag data(ItemStack cell) {
        return cell.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static int searchCursor(ItemStack cell) { return data(cell).getCompound(SEARCH).getInt("cursor"); }
    private static int formatCursor(ItemStack cell) { return data(cell).getInt(CURSOR); }

    private static void setFormatting(ItemStack cell, int cursor, int elapsed, boolean currentVersion) {
        CustomData.update(DataComponents.CUSTOM_DATA, cell, tag -> {
            tag.putInt(CURSOR, cursor);
            tag.putInt(ELAPSED, elapsed);
            if (currentVersion) tag.putInt(VERSION, 2);
            else tag.remove(VERSION);
        });
    }
}
