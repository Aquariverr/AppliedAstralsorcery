package com.appliedastralsorcery.gateway;

import appeng.api.ids.AEComponents;
import appeng.items.storage.SpatialStorageCellItem;
import appeng.spatial.SpatialStoragePlot;
import appeng.spatial.SpatialStoragePlotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import appeng.core.definitions.AEBlocks;

/** Formatting is incremental and its cursor travels with the cell, including when the gate is broken. */
public final class SpatialCellAccess {
    private static final String CURSOR = "appliedas_gateway_format_cursor";
    private static final String ELAPSED = "appliedas_gateway_format_ticks";
    private static final String VERSION = "appliedas_gateway_format_version";
    public static final int FORMAT_TICKS = 100;
    public static final int BLOCKS_PER_CELL_TICK = 4096;
    public static final int GLOBAL_BLOCK_BUDGET = 8192;
    private SpatialCellAccess() {}

    public static boolean largeEnough(BlockPos size) {
        return size.getX() > 5 && size.getY() > 5 && size.getZ() > 5;
    }

    public static boolean accepts(ItemStack stack) {
        if (!(stack.getItem() instanceof SpatialStorageCellItem cell)) return false;
        var info = stack.get(AEComponents.SPATIAL_PLOT_INFO);
        return info == null ? cell.getMaxStoredDim(stack) > 5 : largeEnough(info.size());
    }

    public static boolean isFormatting(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(CURSOR);
    }

    public static SpatialStoragePlot plot(ItemStack stack) {
        if (!accepts(stack)) return null;
        var info = stack.get(AEComponents.SPATIAL_PLOT_INFO);
        if (info == null) return null;
        var plot = SpatialStoragePlotManager.INSTANCE.getPlot(info.id());
        return plot != null && plot.getSize().equals(info.size()) && largeEnough(plot.getSize()) ? plot : null;
    }

    public static void beginFormatting(ItemStack stack, int owner) {
        if (!accepts(stack) || stack.has(AEComponents.SPATIAL_PLOT_INFO)) return;
        var cell = (SpatialStorageCellItem) stack.getItem();
        int size = Math.min(cell.getMaxStoredDim(stack), SpatialStoragePlot.MAX_SIZE);
        var plot = SpatialStoragePlotManager.INSTANCE.allocatePlot(new BlockPos(size, size, size), owner);
        cell.setStoredDimension(stack, plot.getId(), plot.getSize());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(CURSOR, 0);
            tag.putInt(ELAPSED, 0);
            tag.putInt(VERSION, 2);
        });
    }

    public static int progress(ItemStack stack) {
        var info = stack.get(AEComponents.SPATIAL_PLOT_INFO);
        if (info == null || !isFormatting(stack)) return 100;
        var saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int elapsed = Math.clamp(saved.getInt(ELAPSED), 0, FORMAT_TICKS);
        var size = info.size();
        int total = size.getX() * size.getY() * size.getZ();
        if (total <= 0) return 0;
        return (int) Math.clamp(Math.min(100L * elapsed / FORMAT_TICKS,
                100L * blockCursor(saved, size) / total), 0, 99);
    }

    public static void formatStep(ItemStack stack) {
        if (!isFormatting(stack)) return;
        var plot = plot(stack);
        if (plot == null) return;
        ServerLevel level = SpatialStoragePlotManager.INSTANCE.getLevel();
        var size = plot.getSize();
        int total = size.getX() * size.getY() * size.getZ();
        var saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int elapsed = Math.clamp(saved.getInt(ELAPSED), 0, FORMAT_TICKS);
        int start = blockCursor(saved, size);
        // Small cells retain the five-second minimum. Large/concurrent jobs obey the shared cap.
        int remainingTicks = Math.max(1, FORMAT_TICKS - elapsed);
        int requested = Math.min(BLOCKS_PER_CELL_TICK, (total - start + remainingTicks - 1) / remainingTicks);
        int granted = GatewayWorkBudget.format(level, plot.getId(), requested);
        if (granted < 0) return; // A plot, including a copied cell, advances at most once per world tick.
        elapsed = Math.min(FORMAT_TICKS, elapsed + 1);
        // Only the freshly allocated plot's matrix is cleared, never an existing cell's contents.
        // Keep the legacy column order (Y inside each column, then X, then Z).
        var at = new BlockPos.MutableBlockPos();
        var origin = plot.getOrigin();
        int end = start;
        for (int limit = start + granted; end < limit; end++) {
            int column = end / size.getY();
            at.setWithOffset(origin, column % size.getX(), end % size.getY(), column / size.getX());
            if (!GatewayWorkBudget.chunkReady(level, at)) break;
            if (level.getBlockState(at).is(AEBlocks.MATRIX_FRAME.block()))
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        int completed = end;
        int ticks = elapsed;
        if (completed == start && completed < total && ticks == saved.getInt(ELAPSED) && saved.getInt(VERSION) == 2)
            return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (completed == total && ticks >= FORMAT_TICKS) {
                tag.remove(CURSOR);
                tag.remove(ELAPSED);
                tag.remove(VERSION);
            } else {
                tag.putInt(CURSOR, completed);
                tag.putInt(ELAPSED, ticks);
                tag.putInt(VERSION, 2);
            }
        });
    }

    private static int blockCursor(net.minecraft.nbt.CompoundTag saved, BlockPos size) {
        long cursor = Math.max(0, saved.getInt(CURSOR));
        if (saved.getInt(VERSION) < 2) cursor *= size.getY();
        return (int) Math.min(cursor, (long) size.getX() * size.getY() * size.getZ());
    }
}
