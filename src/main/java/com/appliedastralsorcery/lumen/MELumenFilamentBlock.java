package com.appliedastralsorcery.lumen;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import java.util.EnumMap;
import java.util.Map;

import appeng.block.AEBaseEntityBlock;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MELumenFilamentBlock extends AEBaseEntityBlock<MELumenFilamentBlockEntity> {
    public static final DirectionProperty BASE_FACE = DirectionProperty.create("base_face");

    // Native AS filament silhouette, raised two pixels above a compact ME interface base.
    private static final VoxelShape SHAPE = Shapes.or(
            box(3, 0, 3, 13, 2, 13),
            box(4, 2, 4, 12, 4, 12),
            box(5, 4, 5, 11, 6, 11),
            box(2, 2, 6, 6, 4.1, 10),
            box(6, 2, 10, 10, 4.1, 14),
            box(10, 2, 6, 14, 4.1, 10),
            box(6, 2, 2, 10, 4.1, 6));
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public MELumenFilamentBlock() {
        super(metalProps().noOcclusion().strength(2.0F).lightLevel(state -> 6));
        registerDefaultState(stateDefinition.any().setValue(BASE_FACE, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BASE_FACE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BASE_FACE, context.getClickedFace().getOpposite());
    }

    @Override
    @Nonnull
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BASE_FACE, rotation.rotate(state.getValue(BASE_FACE)));
    }

    @Override
    @Nonnull
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BASE_FACE, mirror.mirror(state.getValue(BASE_FACE)));
    }

    @Override
    @Nonnull
    @ParametersAreNonnullByDefault
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BASE_FACE));
    }

    /** The native filament's glowing center, relative to the block origin. */
    public static Vec3 getEffectOffset(BlockState state) {
        return rotatePoint(new Vec3(0.5, 0.325, 0.5), state.getValue(BASE_FACE));
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var face : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (var bounds : SHAPE.toAabbs()) {
                var first = rotatePoint(new Vec3(bounds.minX, bounds.minY, bounds.minZ), face);
                var second = rotatePoint(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ), face);
                shape = Shapes.or(shape, Shapes.box(
                        Math.min(first.x, second.x), Math.min(first.y, second.y), Math.min(first.z, second.z),
                        Math.max(first.x, second.x), Math.max(first.y, second.y), Math.max(first.z, second.z)));
            }
            shapes.put(face, shape.optimize());
        }
        return shapes;
    }

    // Match the negative X/Y rotations used by the blockstate model variants.
    private static Vec3 rotatePoint(Vec3 point, Direction face) {
        return switch (face) {
            case DOWN -> point;
            case UP -> new Vec3(point.x, 1 - point.y, 1 - point.z);
            case NORTH -> new Vec3(point.x, 1 - point.z, point.y);
            case SOUTH -> new Vec3(point.x, point.z, 1 - point.y);
            case WEST -> new Vec3(point.y, point.z, 1 - point.x);
            case EAST -> new Vec3(1 - point.y, point.z, point.x);
        };
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            var filament = getBlockEntity(level, pos);
            if (filament != null) {
                player.openMenu(new SimpleMenuProvider(
                        (id, inventory, owner) -> new MELumenFilamentMenu(id, inventory, filament),
                        Component.translatable("block.appliedas.me_lumen_filament")));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide() && !state.is(next.getBlock())) {
            LumenNetworkHelper.getNode(level, pos).ifPresent(node -> LumenNetworkHelper.removeNode(level, node));
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
