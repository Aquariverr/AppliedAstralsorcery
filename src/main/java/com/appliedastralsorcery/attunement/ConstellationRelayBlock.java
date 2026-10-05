package com.appliedastralsorcery.attunement;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemHandlerHelper;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ConstellationRelayBlock extends BaseEntityBlock {
    public static final MapCodec<ConstellationRelayBlock> CODEC = simpleCodec(ConstellationRelayBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 3, 14),
            box(6.5, 0, 0.5, 9.5, 3.5, 15.5), box(0.5, 0, 6.5, 15.5, 3.5, 9.5));
    private java.util.function.Function<ConstellationRelayBlockEntity, ConstellationRelayBlockEntity.ClientEffects>
            clientEffectsFactory = relay -> ConstellationRelayBlockEntity.ClientEffects.NONE;

    public void setClientEffectsFactory(java.util.function.Function<ConstellationRelayBlockEntity,
            ConstellationRelayBlockEntity.ClientEffects> factory) {
        clientEffectsFactory = factory;
    }

    ConstellationRelayBlockEntity.ClientEffects createClientEffects(ConstellationRelayBlockEntity relay) {
        return clientEffectsFactory.apply(relay);
    }

    public ConstellationRelayBlock() {
        this(Properties.of().mapColor(MapColor.QUARTZ).strength(3).sound(SoundType.GLASS)
                .requiresCorrectToolForDrops().noOcclusion());
    }
    private ConstellationRelayBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ConstellationRelayBlockEntity(pos, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModContent.CONSTELLATION_RELAY_ENTITY.get(),
                (world, pos, blockState, entity) -> {
                    if (world.isClientSide) entity.clientTick();
                    else entity.serverTick();
                });
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !AttunementProcessing.accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ConstellationRelayBlockEntity relay) {
            var remainder = relay.getInventory().insertItem(0, stack.copyWithCount(1), false);
            if (remainder.isEmpty() && !player.isCreative()) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ConstellationRelayBlockEntity relay) {
            if (player.getMainHandItem().isEmpty() && player.isShiftKeyDown()) {
                ItemHandlerHelper.giveItemToPlayer(player, relay.getInventory().extractItem(0, 64, false));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ConstellationRelayBlockEntity relay) relay.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }
}
