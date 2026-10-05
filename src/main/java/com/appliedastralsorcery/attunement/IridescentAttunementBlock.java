package com.appliedastralsorcery.attunement;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class IridescentAttunementBlock extends BaseEntityBlock {
    public static final MapCodec<IridescentAttunementBlock> CODEC = simpleCodec(IridescentAttunementBlock::new);
    private static final VoxelShape CRYSTAL = box(6, 6.5, 6, 10, 15.5, 10);
    // The slab overhangs the block like the AS attunement altar's; it only collides within the block.
    private static final VoxelShape SHAPE = Shapes.or(box(-2, 0, -2, 18, 6, 18), CRYSTAL);
    private static final VoxelShape COLLISION_SHAPE = Shapes.or(box(0, 0, 0, 16, 6, 16), CRYSTAL);
    private java.util.function.Function<IridescentAttunementBlockEntity, IridescentAttunementBlockEntity.ClientEffects>
            clientEffectsFactory = altar -> IridescentAttunementBlockEntity.ClientEffects.NONE;

    public void setClientEffectsFactory(java.util.function.Function<IridescentAttunementBlockEntity,
            IridescentAttunementBlockEntity.ClientEffects> factory) {
        clientEffectsFactory = factory;
    }

    IridescentAttunementBlockEntity.ClientEffects createClientEffects(IridescentAttunementBlockEntity altar) {
        return clientEffectsFactory.apply(altar);
    }

    public IridescentAttunementBlock() {
        this(Properties.of().mapColor(MapColor.QUARTZ).strength(4).sound(SoundType.STONE)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 8));
    }
    private IridescentAttunementBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new IridescentAttunementBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.IRIDESCENT_ATTUNEMENT_ENTITY.get(), (world, pos, blockState, entity) -> {
            if (world instanceof ServerLevel server) entity.serverTick(server); else entity.clientTick(world);
        });
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof IridescentAttunementBlockEntity altar)
            altar.onTileEntityRemove(level, pos);
        super.onRemove(state, level, pos, next, moved);
    }
}
