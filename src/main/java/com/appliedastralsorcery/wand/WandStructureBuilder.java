package com.appliedastralsorcery.wand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.networking.IGrid;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.structure.observer.CompoundObserverProviderStructure;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import hellfirepvp.astralsorcery.common.tile.base.TileEntityTick;
import hellfirepvp.observerlib.api.structure.MatchableStructure;
import hellfirepvp.observerlib.common.change.ObserverProviderStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

final class WandStructureBuilder {
    private WandStructureBuilder() {}

    static void build(ServerPlayer player, ItemStack wand, TileEntityTick<?> tile, Direction face) {
        if (!player.mayBuild() || player.isSpectator()) return;
        boolean needsGrid = MEResonatingWandItem.enabled(wand, MEResonatingWandItem.REPLACE_BLOCKS)
                || !player.isCreative() && (MEResonatingWandItem.enabled(wand, MEResonatingWandItem.USE_ME_ITEMS)
                        || tile instanceof TileInfuser && MEResonatingWandItem.enabled(wand, MEResonatingWandItem.BUILD_FLUIDS));
        var grid = needsGrid ? WandNetwork.findGrid(player, wand) : null;
        if (needsGrid && grid == null) return;
        build(player, wand, tile, face, grid);
    }

    static void build(ServerPlayer player, ItemStack wand, TileEntityTick<?> tile, Direction face, IGrid grid) {
        if (!player.mayBuild() || player.isSpectator()) return;
        var buffer = new WandMaterialBuffer(wand, grid, player);
        buffer.refund();
        var level = player.serverLevel();
        var center = tile.getBlockPos();
        var structure = findStructure(tile, level);
        var plan = new ArrayList<Placement>();
        if (structure != null) {
            for (var entry : structure.getContents().entrySet()) {
                var pos = center.offset(entry.getKey());
                if (!level.hasChunkAt(pos)) {
                    blocked(player, pos);
                    return;
                }
                if (structure.matchesSingleBlock(level, center, entry.getKey())) continue;
                var target = entry.getValue().getDescriptiveState(0L);
                if (!addPlacement(plan, player, wand, face, pos, target)) return;
            }
        }
        // AS keeps these pools outside PatternInfuser: they are recipe inputs, not observer blocks.
        if (tile instanceof TileInfuser && MEResonatingWandItem.enabled(wand, MEResonatingWandItem.BUILD_FLUIDS)) {
            var fluid = FluidsAS.LIQUID_STARLIGHT.getSource().get();
            BlockState target = fluid.defaultFluidState().createLegacyBlock();
            if (!(target.getBlock() instanceof LiquidBlock) || !target.getFluidState().isSource()
                    || level.dimensionType().ultraWarm() && fluid.getFluidType().isVaporizedOnPlacement(level,
                            center, new net.neoforged.neoforge.fluids.FluidStack(fluid, FluidType.BUCKET_VOLUME))) {
                MEResonatingWandItem.message(player, "invalid_fluid");
                return;
            }
            for (var offset : TileInfuser.getLiquidOffsets()) {
                var pos = center.offset(offset);
                if (level.hasChunkAt(pos) && level.getFluidState(pos).isSource()
                        && level.getFluidState(pos).getType().isSame(fluid)) continue;
                if (!addPlacement(plan, player, wand, face, pos, target)) return;
            }
        }
        // Place foundations before decorations, and all solid blocks before fluid sources.
        plan.sort(Comparator.comparing((Placement p) -> p.key() instanceof AEFluidKey)
                .thenComparingInt(p -> p.pos().getY())
                .thenComparingInt(p -> p.pos().getX()).thenComparingInt(p -> p.pos().getZ()));
        try {
            if (plan.isEmpty()) {
                MEResonatingWandItem.message(player, "complete");
                return;
            }
            Map<AEKey, Long> required = new LinkedHashMap<>();
            for (var placement : plan) required.merge(placement.key(), placement.amount(), Long::sum);
            if (!buffer.reserve(required, player)) return;
            int placed = 0;
            for (var placement : plan) {
                if (!level.getBlockState(placement.pos()).equals(placement.previous())
                        || !canPlace(player, wand, face, placement.pos(), placement.state())
                        || !placement.state().canSurvive(level, placement.pos())) {
                    blocked(player, placement.pos());
                    return;
                }
                if (!buffer.canRecover(placement.recovered())) {
                    MEResonatingWandItem.message(player, "recovery_full");
                    return;
                }
                if (!placement.previous().isAir() && MEResonatingWandItem.enabled(wand, MEResonatingWandItem.REPLACE_BLOCKS)
                        && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, placement.pos(), placement.previous(), player)).isCanceled()) {
                    blocked(player, placement.pos());
                    return;
                }
                var snapshot = BlockSnapshot.create(level.dimension(), level, placement.pos());
                // Delay neighbor updates until claim/protection handlers accept the placement.
                if (!level.setBlock(placement.pos(), placement.state(), 2 | 16)) {
                    MEResonatingWandItem.message(player, "interrupted");
                    return;
                }
                if (EventHooks.onBlockPlace(player, snapshot, face)) {
                    snapshot.restore();
                    blocked(player, placement.pos());
                    return;
                }
                buffer.consume(placement.key(), placement.amount());
                boolean recovered = buffer.recover(placement.recovered());
                if (placement.key() instanceof AEItemKey item) {
                    placement.state().getBlock().setPlacedBy(level, placement.pos(), placement.state(), player, item.toStack(1));
                }
                level.markAndNotifyBlock(placement.pos(), level.getChunkAt(placement.pos()), placement.previous(),
                        placement.state(), 3, 512);
                level.gameEvent(player, GameEvent.BLOCK_PLACE, placement.pos());
                placed++;
                if (!recovered) {
                    MEResonatingWandItem.message(player, "recovery_pending");
                    return;
                }
            }
            MEResonatingWandItem.message(player, "built", placed);
        } finally {
            buffer.refund();
        }
    }

    private static MatchableStructure findStructure(TileEntityTick<?> tile, ServerLevel level) {
        var provider = tile.getRequiredObserver().observer().get();
        if (provider instanceof ObserverProviderStructure single) return single.getStructure();
        if (provider instanceof CompoundObserverProviderStructure compound) {
            // Same progression as the native wand: the base altar, then its expanded form.
            for (var structure : compound.getStructures()) {
                if (!structure.matches(level, tile.getBlockPos())) return structure;
            }
        }
        return null;
    }

    private static boolean addPlacement(List<Placement> plan, ServerPlayer player, ItemStack wand, Direction face,
            BlockPos pos, BlockState target) {
        if (target.isAir() || !canPlace(player, wand, face, pos, target)) {
            blocked(player, pos);
            return false;
        }
        AEKey key;
        long amount;
        if (target.getBlock() instanceof LiquidBlock) {
            key = AEFluidKey.of(target.getFluidState().getType());
            amount = FluidType.BUCKET_VOLUME;
        } else {
            if (target.getBlock().asItem() == Items.AIR) {
                blocked(player, pos);
                return false;
            }
            key = AEItemKey.of(target.getBlock().asItem());
            amount = target.hasProperty(SlabBlock.TYPE) && target.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
        }
        var previous = player.level().getBlockState(pos);
        Map<AEKey, Long> recovered = new LinkedHashMap<>();
        if (!previous.isAir() && MEResonatingWandItem.enabled(wand, MEResonatingWandItem.REPLACE_BLOCKS)) {
            // Silk-touch loot preserves ordinary building blocks (stone, glass, ores, slabs).
            // Block entities and multi-block objects are excluded in canPlace to protect their data and partners.
            var tool = new ItemStack(Items.DIAMOND_PICKAXE);
            tool.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
            if (!(previous.getBlock() instanceof LiquidBlock)) {
                for (var drop : Block.getDrops(previous, player.serverLevel(), pos, null, player, tool)) {
                    if (!drop.isEmpty()) recovered.merge(AEItemKey.of(drop), (long) drop.getCount(), Long::sum);
                }
            }
            var oldFluid = previous.getFluidState();
            if (oldFluid.isSource()) recovered.merge(AEFluidKey.of(oldFluid.getType()), (long) FluidType.BUCKET_VOLUME, Long::sum);
        }
        plan.add(new Placement(pos.immutable(), target, previous, key, amount, recovered));
        return true;
    }

    private static boolean canPlace(ServerPlayer player, ItemStack wand, Direction face, BlockPos pos, BlockState target) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, wand)) return false;
        var old = level.getBlockState(pos);
        boolean replace = MEResonatingWandItem.enabled(wand, MEResonatingWandItem.REPLACE_BLOCKS);
        if (level.getBlockEntity(pos) != null || old.getDestroySpeed(level, pos) < 0
                || old.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || old.getBlock() instanceof PistonHeadBlock
                || old.hasProperty(BlockStateProperties.EXTENDED) && old.getValue(BlockStateProperties.EXTENDED)
                || !replace && !old.canBeReplaced()) return false;
        var fluid = old.getFluidState();
        if (!replace && !fluid.isEmpty() && (fluid.isSource() || !(target.getBlock() instanceof LiquidBlock)
                || !fluid.getType().isSame(target.getFluidState().getType()))) return false;
        return level.isUnobstructed(target, pos, CollisionContext.empty());
    }

    private static void blocked(ServerPlayer player, BlockPos pos) {
        MEResonatingWandItem.message(player, "blocked", pos.getX(), pos.getY(), pos.getZ());
    }

    private record Placement(BlockPos pos, BlockState state, BlockState previous, AEKey key, long amount,
            Map<AEKey, Long> recovered) {}
}
