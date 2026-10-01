package com.appliedastralsorcery.chisel;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AutoChiselBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final MapCodec<AutoChiselBlock> CODEC = simpleCodec(AutoChiselBlock::new);
    // Plinth, feet and work plate; pillars and capitals; lintel; the chisel hanging from it.
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            box(0, 0, 0, 16, 2, 16), box(1, 2, 1, 15, 3, 15),
            box(1, 3, 5, 6, 4, 11), box(10, 3, 5, 15, 4, 11), box(6, 3, 6, 10, 4, 10),
            box(2, 4, 6, 5, 12, 10), box(11, 4, 6, 14, 12, 10), box(1, 12, 5, 6, 13, 11), box(10, 12, 5, 15, 13, 11),
            box(0, 13, 5, 16, 16, 11),
            box(6, 12, 6, 10, 13, 10), box(6.5, 5, 6.5, 9.5, 12, 9.5));

    public AutoChiselBlock() {
        this(Properties.of().mapColor(MapColor.QUARTZ).strength(3.5F).sound(SoundType.METAL)
                .requiresCorrectToolForDrops().noOcclusion());
    }
    private AutoChiselBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(LIT, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, LIT);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AutoChiselBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModContent.AUTO_CHISEL_ENTITY.get(),
                (world, pos, blockState, entity) -> entity.serverTick());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AutoChiselBlockEntity entity) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new AutoChiselMenu(id, inventory, entity),
                    Component.translatable("block.appliedas.auto_starmetal_chisel")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof AutoChiselBlockEntity entity) entity.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }
}
