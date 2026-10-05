package com.appliedastralsorcery.tree;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.TreeBeaconBlock;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class METreeBeaconBlock extends TreeBeaconBlock {
    public static final MapCodec<METreeBeaconBlock> CODEC = simpleCodec(METreeBeaconBlock::new);

    public METreeBeaconBlock() {
        this(Properties.of().strength(2.0F).sound(SoundType.STONE).noOcclusion());
    }

    private METreeBeaconBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new METreeBeaconBlockEntity(pos, state);
    }

    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.TREE_BEACON_ENTITY.get(), (world, pos, blockState, beacon) -> {
            if (world instanceof ServerLevel server) beacon.serverTick(server);
            else beacon.clientTick(world);
        });
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof METreeBeaconBlockEntity beacon)
            beacon.getMainNode().setOwningPlayer(player);
    }
}
