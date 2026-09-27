package com.appliedastralsorcery.lumen;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.LumenArrayBlock;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class MELumenArrayBlock extends LumenArrayBlock {
    public static final MapCodec<MELumenArrayBlock> CODEC = simpleCodec(MELumenArrayBlock::new);
    public MELumenArrayBlock() { this(Properties.of().strength(2.0F).sound(SoundType.STONE).noOcclusion()); }
    private MELumenArrayBlock(Properties properties) {
        super(properties, new TileRegistryObject<>(ModContent.ARRAY_ENTITY));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MELumenArrayBlockEntity array) {
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MELumenArrayMenu(id, inventory, array),
                    Component.translatable("block.appliedas.me_lumen_array")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MELumenArrayBlockEntity array) {
            array.getMainNode().setOwningPlayer(player);
        }
    }
}
