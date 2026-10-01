package com.appliedastralsorcery.lumen;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MELumenAlchemyArrayBlock extends MELumenArrayBlock {
    public static final MapCodec<MELumenAlchemyArrayBlock> CODEC = simpleCodec(MELumenAlchemyArrayBlock::new);
    private static final VoxelShape SHAPE = Block.box(-1, 0, -1, 17, 17, 17);

    public MELumenAlchemyArrayBlock() { this(Properties.of().strength(2.0F).sound(SoundType.STONE).noOcclusion()); }
    private MELumenAlchemyArrayBlock(Properties properties) {
        super(properties, new TileRegistryObject<>(ModContent.ALCHEMY_ARRAY_ENTITY));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
