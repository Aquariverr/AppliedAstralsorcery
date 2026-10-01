package com.appliedastralsorcery.transmutation;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.block.tile.base.BaseTickTileBlock;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class StarlightTransmutationBlock extends BaseTickTileBlock<StarlightTransmutationBlockEntity> {
    public static final MapCodec<StarlightTransmutationBlock> CODEC = simpleCodec(StarlightTransmutationBlock::new);
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 3, 16),
            box(1, 3, 1, 15, 14, 15), box(0, 14, 0, 16, 16, 16));

    public StarlightTransmutationBlock() {
        this(Properties.of().mapColor(MapColor.QUARTZ).strength(4.0F).sound(SoundType.GLASS)
                .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> state.getValue(WORKING) ? 10 : 0));
    }
    private StarlightTransmutationBlock(Properties properties) {
        super(properties, new TileRegistryObject<>(ModContent.TRANSMUTATION_ENTITY));
        registerDefaultState(stateDefinition.any().setValue(WORKING, false).setValue(LIT, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(WORKING, LIT); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected BlockEntityTicker<StarlightTransmutationBlockEntity> createTicker() { return ticker(); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StarlightTransmutationBlockEntity entity)
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new StarlightTransmutationMenu(id, inventory, entity),
                    Component.translatable("block.appliedas.starlight_transmutation_chamber")));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof StarlightTransmutationBlockEntity entity)
            entity.getMainNode().setOwningPlayer(player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof StarlightTransmutationBlockEntity entity) {
            entity.dropContents();
            entity.onTileEntityRemove(level, pos);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    // The Astral Sorcery base drops item capabilities even on a property-only state change.
    @Override protected void dropTileContents(BlockState state, Level level, BlockPos pos) {}
}
