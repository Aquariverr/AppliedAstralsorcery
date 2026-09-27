package com.appliedastralsorcery.lumen;

import java.util.function.Consumer;
import java.util.function.Supplier;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Enables container transfers in terminals and selecting lumen in ghost filter slots. */
public final class LumenContainerStrategy implements ContainerItemStrategy<LumenKey, LumenContainerStrategy.Context> {
    @Override
    public GenericStack getContainedStack(ItemStack stack) {
        var handler = stack.getCapability(ILumenHandler.ITEM);
        if (handler != null) {
            for (var content : handler.getContainedLumen()) {
                if (!content.isEmpty()) {
                    return new GenericStack(LumenKey.of(content.getLumen()), content.getAmount());
                }
            }
        }
        // Crystals identify a lumen for filters; they are not converted into stored lumen.
        var component = stack.get(DataComponentsAS.LUMEN);
        if (component != null && component.lumen().value() != LumenAS.NONE.get()) {
            return new GenericStack(LumenKey.of(component.lumen().value()), 1);
        }
        return null;
    }

    @Override
    public Context findCarriedContext(Player player, AbstractContainerMenu menu) {
        return menu.getCarried().getCapability(ILumenHandler.ITEM) == null ? null
                : new Context(player, menu::getCarried, menu::setCarried, menu::broadcastChanges);
    }

    @Override
    public Context findPlayerSlotContext(Player player, int slot) {
        var inventory = player.getInventory();
        return inventory.getItem(slot).getCapability(ILumenHandler.ITEM) == null ? null
                : new Context(player, () -> inventory.getItem(slot), stack -> inventory.setItem(slot, stack),
                        inventory::setChanged);
    }

    @Override
    public long extract(Context context, LumenKey what, long amount, Actionable mode) {
        return transfer(context, what, amount, mode, false);
    }

    @Override
    public long insert(Context context, LumenKey what, long amount, Actionable mode) {
        return transfer(context, what, amount, mode, true);
    }

    private long transfer(Context context, LumenKey what, long amount, Actionable mode, boolean insert) {
        if (amount <= 0) {
            return 0;
        }
        var original = context.getStack().get();
        var copy = original.copyWithCount(1);
        var handler = copy.getCapability(ILumenHandler.ITEM);
        if (handler == null) {
            return 0;
        }
        int requested = LumenStorage.clampAmount(amount);
        int transferred = insert ? handler.fill(what.lumen().stack(requested), LumenStorage.action(mode))
                : handler.drain(what.lumen(), requested, LumenStorage.action(mode)).getAmount();
        if (transferred > 0 && mode == Actionable.MODULATE) {
            if (original.getCount() == 1) {
                context.setStack().accept(copy);
            } else {
                original.shrink(1);
                context.player().getInventory().placeItemBackInInventory(copy);
            }
            context.onChanged().run();
        }
        return transferred;
    }

    @Override
    public GenericStack getExtractableContent(Context context) {
        var handler = context.getStack().get().getCapability(ILumenHandler.ITEM);
        if (handler != null) {
            for (var stack : handler.getContainedLumen()) {
                if (!stack.isEmpty()) {
                    var available = handler.drain(stack.copy(), ILumenHandler.Action.SIMULATE);
                    if (!available.isEmpty()) {
                        return new GenericStack(LumenKey.of(available.getLumen()), available.getAmount());
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void playFillSound(Player player, LumenKey what) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.5F, 1);
    }

    @Override
    public void playEmptySound(Player player, LumenKey what) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.5F, 1);
    }

    public record Context(Player player, Supplier<ItemStack> getStack, Consumer<ItemStack> setStack, Runnable onChanged) {}
}
