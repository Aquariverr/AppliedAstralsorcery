package com.appliedastralsorcery.gateway;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.CelestialGatewayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MECelestialGatewayBlock extends CelestialGatewayBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final MapCodec<MECelestialGatewayBlock> CODEC = simpleCodec(MECelestialGatewayBlock::new);
    public MECelestialGatewayBlock() { this(Properties.of().strength(2.5F).sound(SoundType.STONE).noOcclusion()); }
    private MECelestialGatewayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MECelestialGatewayBlockEntity(pos, state);
    }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.GATEWAY_ENTITY.get(), (world, pos, blockState, gate) -> {
            if (world instanceof ServerLevel server) gate.serverTick(server); else gate.clientTick(world);
        });
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Let native wands, aquamarine locking and other item interactions run normally.
        if (!stack.isEmpty() && !(stack.getItem() instanceof appeng.items.storage.SpatialStorageCellItem))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MECelestialGatewayBlockEntity gate
                && gate.canUse(player)) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MECelestialGatewayMenu(id, inventory, gate),
                    gate.getName()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void dropTileContents(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MECelestialGatewayBlockEntity gate) {
            var stack = gate.getInventory().getStackInSlot(0);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
                gate.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            }
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) super.onRemove(state, level, pos, next, moved);
    }
}
