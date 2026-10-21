package com.appliedastralsorcery.gateway;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Server-thread work limits shared by every gateway using the same storage dimension. */
final class GatewayWorkBudget {
    private static final Map<ServerLevel, Budget> FORMATTING = new WeakHashMap<>();
    private static final Map<ServerLevel, Budget> SEARCHING = new WeakHashMap<>();
    private static final Map<ServerLevel, ChunkRequests> CHUNKS = new WeakHashMap<>();
    private static final TicketType<ChunkPos> WORK_CHUNK = TicketType.create(
            "appliedas_gateway_work", java.util.Comparator.comparingLong(ChunkPos::toLong), 20);

    private GatewayWorkBudget() {}

    static int format(ServerLevel level, int plot, int requested) {
        return FORMATTING.computeIfAbsent(level, ignored -> new Budget())
                .take(level.getGameTime(), plot, requested, SpatialCellAccess.GLOBAL_BLOCK_BUDGET);
    }

    static int search(ServerLevel level, int plot) {
        return SEARCHING.computeIfAbsent(level, ignored -> new Budget()).take(level.getGameTime(), plot,
                SpatialReturnPortalBlockEntity.SEARCH_CANDIDATES_PER_TICK,
                SpatialReturnPortalBlockEntity.GLOBAL_SEARCH_CANDIDATES);
    }

    static boolean chunkReady(ServerLevel level, BlockPos pos) {
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        var requests = CHUNKS.computeIfAbsent(level, ignored -> new ChunkRequests());
        if (requests.tick != level.getGameTime()) {
            requests.tick = level.getGameTime();
            requests.chunks.clear();
        }
        // Adding an expiring ticket schedules loading without blocking the server thread.
        // getChunkFuture would synchronously wait when called on that thread.
        // Refresh ready work chunks too, once per tick, so an in-progress job cannot churn loads.
        if (requests.chunks.add(ChunkPos.asLong(x, z))) {
            var chunk = new ChunkPos(x, z);
            level.getChunkSource().addRegionTicket(WORK_CHUNK, chunk, 0, chunk);
        }
        return level.getChunkSource().getChunkNow(x, z) != null;
    }

    private static final class ChunkRequests {
        private long tick = Long.MIN_VALUE;
        private final LongOpenHashSet chunks = new LongOpenHashSet();
    }

    private static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int remaining;
        private final Map<Integer, Long> lastSeen = new HashMap<>();

        int take(long now, int plot, int requested, int total) {
            if (tick != now) {
                tick = now;
                remaining = total;
                lastSeen.values().removeIf(seen -> seen < now - 1 || seen > now);
            }
            var previous = lastSeen.put(plot, now);
            if (previous != null && previous == now) return -1;
            // Previous-tick participants retain a share even when they tick after other gates.
            int share = Math.max(1, total / lastSeen.size());
            int granted = Math.min(Math.min(requested, share), remaining);
            remaining -= granted;
            return granted;
        }
    }
}
