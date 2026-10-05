package com.appliedastralsorcery.gateway;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.appliedastralsorcery.ModContent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SpatialReturnPortalBlock extends BaseEntityBlock {
    public static final MapCodec<SpatialReturnPortalBlock> CODEC = simpleCodec(SpatialReturnPortalBlock::new);
    public SpatialReturnPortalBlock() {
        this(Properties.of().strength(-1, 3600000).noLootTable().noOcclusion().lightLevel(state -> 12));
    }
    private SpatialReturnPortalBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SpatialReturnPortalBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModContent.RETURN_PORTAL_ENTITY.get(),
                (world, pos, blockState, portal) -> portal.serverTick((ServerLevel) world));
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof SpatialReturnPortalBlockEntity portal) portal.onTileEntityRemove(level, pos);
        super.onRemove(state, level, pos, next, moved);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1, 0, 1, 15, 1, 15);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SpatialReturnPortalBlockEntity portal)
            portal.returnPlayer(server);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3 + random.nextDouble(),
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.025, 0);
    }
}
