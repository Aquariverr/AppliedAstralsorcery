package com.appliedastralsorcery.chalice;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.ChaliceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class MEChaliceBlock extends ChaliceBlock {
    public static final MapCodec<MEChaliceBlock> CODEC = simpleCodec(MEChaliceBlock::new);
    public static final BooleanProperty TOP_CONNECTED = BooleanProperty.create("top_connected");
    public static final BooleanProperty BOTTOM_CONNECTED = BooleanProperty.create("bottom_connected");
    private static final VoxelShape NATIVE_SHAPE = box(2, 0, 2, 14, 14, 14);
    private static final VoxelShape BOTTOM_SHAPE = box(1.5, 0, 1.5, 14.5, 1.3, 14.5);
    private static final VoxelShape TOP_SHAPE = Shapes.or(box(7, 14, 7, 9, 16, 9),
            box(1.5, 14, 1.5, 14.5, 16, 3.75), box(1.5, 14, 12.25, 14.5, 16, 14.5),
            box(1.5, 14, 3.75, 3.75, 16, 12.25), box(12.25, 14, 3.75, 14.5, 16, 12.25));
    private static final VoxelShape[] SHAPES = {
            NATIVE_SHAPE, Shapes.or(NATIVE_SHAPE, BOTTOM_SHAPE),
            Shapes.or(NATIVE_SHAPE, TOP_SHAPE), Shapes.or(NATIVE_SHAPE, BOTTOM_SHAPE, TOP_SHAPE)
    };

    public MEChaliceBlock() { this(Properties.of().strength(2.0F).sound(SoundType.STONE).noOcclusion()); }
    private MEChaliceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP_CONNECTED, false).setValue(BOTTOM_CONNECTED, false));
    }
    @ParametersAreNonnullByDefault
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TOP_CONNECTED, BOTTOM_CONNECTED);
    }
    @Nonnull
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MEChaliceBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.CHALICE_ENTITY.get(), (world, pos, blockState, chalice) -> {
            if (world.isClientSide()) chalice.clientTick(world);
            else if (world instanceof ServerLevel server) chalice.serverTick(server);
        });
    }
    @Nonnull
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = (state.getValue(TOP_CONNECTED) ? 2 : 0) | (state.getValue(BOTTOM_CONNECTED) ? 1 : 0);
        return SHAPES[mask];
    }
    @Nonnull
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getCapability(Capabilities.FluidHandler.ITEM) != null)
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Nonnull
    @ParametersAreNonnullByDefault
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MEChaliceBlockEntity chalice) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MEChaliceMenu(id, inventory, chalice),
                    Component.translatable("block.appliedas.me_chalice")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MEChaliceBlockEntity chalice)
            chalice.getMainNode().setOwningPlayer(player);
    }
}
