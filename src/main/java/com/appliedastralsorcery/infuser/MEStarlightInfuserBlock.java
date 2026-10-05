package com.appliedastralsorcery.infuser;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.InfuserBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MEStarlightInfuserBlock extends InfuserBlock {
    public static final MapCodec<MEStarlightInfuserBlock> CODEC = simpleCodec(MEStarlightInfuserBlock::new);
    public MEStarlightInfuserBlock() { this(Properties.of().strength(2.0F).sound(SoundType.STONE).noOcclusion()); }
    private MEStarlightInfuserBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MEStarlightInfuserBlockEntity(pos, state);
    }
    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.STARLIGHT_INFUSER_ENTITY.get(), (world, pos, blockState, infuser) -> {
            if (world instanceof ServerLevel server) infuser.serverTick(server); else infuser.clientTick(world);
        });
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MEStarlightInfuserBlockEntity infuser)
            infuser.getMainNode().setOwningPlayer(player);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (level.getBlockEntity(pos) instanceof MEStarlightInfuserBlockEntity infuser) {
            if (!level.isClientSide()) {
                var inventory = infuser.getInventory();
                int slot = inventory.getStackInSlot(1).isEmpty() ? 0 : 1;
                var extracted = inventory.extractItem(slot, 64, false);
                if (!extracted.isEmpty() && !player.getInventory().add(extracted)) player.drop(extracted, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
    @Override protected void dropTileContents(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MEStarlightInfuserBlockEntity infuser) infuser.dropContents();
    }
}
