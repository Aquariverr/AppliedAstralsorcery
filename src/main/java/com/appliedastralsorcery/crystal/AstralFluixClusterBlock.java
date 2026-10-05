package com.appliedastralsorcery.crystal;

import javax.annotation.Nonnull;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.CelestialCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.lib.SoundsAS;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class AstralFluixClusterBlock extends CelestialCrystalClusterBlock {
    public static final MapCodec<AstralFluixClusterBlock> CODEC = simpleCodec(AstralFluixClusterBlock::new);

    public AstralFluixClusterBlock() {
        this(Properties.ofFullCopy(Blocks.GLASS).strength(0.5F, 10.0F).lightLevel(state -> 6)
                .offsetType(OffsetType.XZ).sound(SoundsAS.CRYSTAL_SOUND_TYPE).forceSolidOn().dynamicShape());
    }

    private AstralFluixClusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AstralFluixClusterBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AstralFluixClusterBlockEntity cluster) {
            // Native cluster items start with an empty 0/0 budget. Creative placement must
            // generate usable crystals, while pick-block stacks keep their existing traits.
            var attributes = cluster.getTileData().getCrystalAttributes();
            var properties = AstralFluixCrystalItem.DEFAULT_ATTRIBUTES.getProperties();
            cluster.getTileData().setCrystalAttributes(attributes.setProperties(properties));
            cluster.generateProperties();
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.ASTRAL_FLUIX_CLUSTER_ENTITY.get(), (world, pos, blockState, cluster) -> {
            if (world.isClientSide()) cluster.clientTick(world);
            else if (world instanceof ServerLevel server) cluster.serverTick(server);
        });
    }
}
