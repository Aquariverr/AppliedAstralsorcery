package com.appliedastralsorcery.wand;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import java.util.List;
import com.mojang.serialization.Codec;

import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.stacks.GenericStack;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.item.WandItem;
import hellfirepvp.astralsorcery.common.tile.base.TileEntityTick;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MEResonatingWandItem extends WandItem {
    public static final int USE_ME_ITEMS = 1, BUILD_FLUIDS = 2, REPLACE_BLOCKS = 4;
    public static final int DEFAULT_OPTIONS = USE_ME_ITEMS | BUILD_FLUIDS;
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, AppliedAstralsorcery.MOD_ID);
    // Failed refunds remain on the actual wand, including fluid amounts smaller than a bucket.
    static final DeferredHolder<DataComponentType<?>, DataComponentType<List<GenericStack>>> RETURN_BUFFER =
            COMPONENTS.registerComponentType("wand_return_buffer", builder -> builder
                    .persistent(GenericStack.CODEC.listOf())
                    .networkSynchronized(GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list())));
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OPTIONS =
            COMPONENTS.registerComponentType("wand_options", builder -> builder
                    .persistent(Codec.intRange(0, 7)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static int options(ItemStack wand) { return wand.getOrDefault(OPTIONS.get(), DEFAULT_OPTIONS); }
    public static boolean enabled(ItemStack wand, int option) { return (options(wand) & option) != 0; }
    public static void setOptions(ItemStack wand, int options) { wand.set(OPTIONS, options & 7); }

    @Override
    @Nonnull
    @ParametersAreNonnullByDefault
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var wand = player.getItemInHand(hand);
        if (player.isSpectator() || getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(wand);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new MEResonatingWandMenu(id, inventory, hand, wand),
                    Component.translatable("gui.appliedas.wand.title")));
        }
        return InteractionResultHolder.sidedSuccess(wand, level.isClientSide());
    }

    public static void registerComponents(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    public static void registerLinking() {
        GridLinkables.register(ModContent.ME_RESONATING_WAND, new IGridLinkableHandler() {
            @Override public boolean canLink(ItemStack stack) {
                return stack.getItem() instanceof MEResonatingWandItem;
            }
            @Override public void link(ItemStack stack, GlobalPos pos) {
                stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
            }
            @Override public void unlink(ItemStack stack) {
                stack.remove(AEComponents.WIRELESS_LINK_TARGET);
            }
        });
    }

    @Override
    @SuppressWarnings("resource")
    public boolean doBlockInteract(LogicalSide side, Player player, InteractionHand hand, BlockPos pos,
            BlockHitResult hitResult, Direction blockFace) {
        var level = player.level();
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            var tile = level.getBlockEntity(pos);
            if (tile instanceof IWirelessAccessPoint) {
                if (player instanceof ServerPlayer serverPlayer) {
                    stack.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), pos));
                    message(serverPlayer, "linked");
                }
                return true;
            }
            if (tile instanceof TileEntityTick<?> astralTile && astralTile.getRequiredObserver() != null) {
                if (player instanceof ServerPlayer serverPlayer) {
                    WandStructureBuilder.build(serverPlayer, stack, astralTile, blockFace);
                }
                return true;
            }
        }
        return super.doBlockInteract(side, player, hand, pos, hitResult, blockFace);
    }

    static void message(ServerPlayer player, String suffix, Object... args) {
        player.displayClientMessage(Component.translatable("message.appliedas.wand." + suffix, args), true);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var target = stack.get(AEComponents.WIRELESS_LINK_TARGET);
        if (target == null) {
            lines.add(Component.translatable("tooltip.appliedas.wand.unlinked").withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("tooltip.appliedas.wand.linked", target.dimension().location().toString(),
                    target.pos().getX(), target.pos().getY(), target.pos().getZ()).withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("tooltip.appliedas.wand.bind").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.appliedas.wand.configure").withStyle(ChatFormatting.AQUA));
    }
}
