package com.appliedastralsorcery.integration;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.items.tools.MemoryCardItem;
import appeng.util.InteractionUtil;
import com.appliedastralsorcery.AppliedAstralsorcery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** AE2 memory-card interaction for machines that inherit Astral Sorcery or vanilla block classes. */
public final class MachineMemoryCard {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, AppliedAstralsorcery.MOD_ID);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> SETTINGS =
            COMPONENTS.registerComponentType("machine_settings", builder -> builder.persistent(CustomData.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG.map(CustomData::of, CustomData::copyTag)));

    private MachineMemoryCard() {}

    public static void registerComponents(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    public static ItemInteractionResult use(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof IMemoryCard card)
                || !(level.getBlockEntity(pos) instanceof MemoryCardSettings machine))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (player.isSpectator()) return ItemInteractionResult.FAIL;
        if (!level.isClientSide()) {
            var source = level.getBlockState(pos).getBlock().getName();
            if (InteractionUtil.isInAlternateUseMode(player)) {
                MemoryCardItem.clearCard(stack);
                stack.set(AEComponents.EXPORTED_SETTINGS_SOURCE, source);
                stack.set(SETTINGS, CustomData.of(machine.exportMemoryCardSettings()));
                card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            } else {
                var settings = stack.get(SETTINGS);
                if (source.equals(stack.get(AEComponents.EXPORTED_SETTINGS_SOURCE)) && settings != null) {
                    machine.importMemoryCardSettings(settings.copyTag());
                    card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                } else {
                    card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
