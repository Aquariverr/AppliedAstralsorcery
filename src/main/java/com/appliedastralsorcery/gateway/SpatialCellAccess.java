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
    public static final int FORMAT_TICKS = 100;
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
        });
    }

    public static int progress(ItemStack stack) {
        var info = stack.get(AEComponents.SPATIAL_PLOT_INFO);
        if (info == null || !isFormatting(stack)) return 100;
        int elapsed = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(ELAPSED);
        return Math.clamp(100L * elapsed / FORMAT_TICKS, 0, 99);
    }

    public static void formatStep(ItemStack stack) {
        if (!isFormatting(stack)) return;
        var plot = plot(stack);
        if (plot == null) return;
        ServerLevel level = SpatialStoragePlotManager.INSTANCE.getLevel();
        var size = plot.getSize();
        int columns = size.getX() * size.getZ();
        var saved = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int elapsed = Math.clamp(saved.getInt(ELAPSED), 0, FORMAT_TICKS - 1) + 1;
        int start = Math.clamp(saved.getInt(CURSOR), 0, columns);
        // Spread every cell size over five seconds, including partially formatted legacy cells.
        int remainingTicks = FORMAT_TICKS - elapsed + 1;
        int end = start + (columns - start + remainingTicks - 1) / remainingTicks;
        // Only the freshly allocated plot's matrix is cleared, never an existing cell's contents.
        // X is the fast axis so successive work stays in a small set of chunks.
        var at = new BlockPos.MutableBlockPos();
        for (int column = start; column < end; column++) {
            int x = column % size.getX(), z = column / size.getX();
            for (int y = 0; y < size.getY(); y++) {
                at.setWithOffset(plot.getOrigin(), x, y, z);
                if (level.getBlockState(at).is(AEBlocks.MATRIX_FRAME.block()))
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (elapsed == FORMAT_TICKS) {
                tag.remove(CURSOR);
                tag.remove(ELAPSED);
            } else {
                tag.putInt(CURSOR, end);
                tag.putInt(ELAPSED, elapsed);
            }
        });
    }
}
