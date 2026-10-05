package com.appliedastralsorcery.crystallizer;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.Map;
import java.util.List;

import appeng.block.AEBaseEntityBlock;
import appeng.util.SettingsFrom;
import com.appliedastralsorcery.ModContent;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MELumenCrystallizerBlock extends AEBaseEntityBlock<MELumenCrystallizerBlockEntity> {
    public static final DirectionProperty BASE_FACE = DirectionProperty.create("base_face");
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.DOWN, box(1, 0, 1, 15, 2, 15),
            Direction.UP, box(1, 14, 1, 15, 16, 15),
            Direction.NORTH, box(1, 1, 0, 15, 15, 2),
            Direction.SOUTH, box(1, 1, 14, 15, 15, 16),
            Direction.WEST, box(0, 1, 1, 2, 15, 15),
            Direction.EAST, box(14, 1, 1, 16, 15, 15));

    public MELumenCrystallizerBlock() {
        super(metalProps().noOcclusion().strength(2).lightLevel(state -> 4));
        registerDefaultState(stateDefinition.any().setValue(BASE_FACE, Direction.DOWN));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BASE_FACE);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BASE_FACE, context.getClickedFace().getOpposite());
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BASE_FACE, rotation.rotate(state.getValue(BASE_FACE)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(BASE_FACE, mirror.mirror(state.getValue(BASE_FACE)));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BASE_FACE));
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        var drops = super.getDrops(state, builder);
        var context = builder.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
        if (context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof MELumenCrystallizerBlockEntity panel) {
            var entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
            var player = entity instanceof Player p ? p : null;
            for (var drop : drops) {
                if (drop.is(ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get())) {
                    // AE's default export only handles BlockItem drops; this legacy block now drops a cable part.
                    drop.applyComponents(panel.exportSettings(SettingsFrom.DISMANTLE_ITEM, player));
                    break;
                }
            }
        }
        return drops;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var panel = getBlockEntity(level, pos);
        if (panel == null || panel.findRecipe(stack) == null)
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide()) {
            var type = panel.markCatalyst(stack);
            if (type != null) player.displayClientMessage(Component.translatable(
                    "message.appliedas.crystallizer.marked", type.getHoverName(), stack.getHoverName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSpectator() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        var panel = getBlockEntity(level, pos);
        if (panel == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                panel.clearMarker();
                player.displayClientMessage(Component.translatable("message.appliedas.crystallizer.cleared"), true);
            } else if (!panel.getMarker().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.appliedas.crystallizer.catalyst",
                        panel.getMarker().getHoverName()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
