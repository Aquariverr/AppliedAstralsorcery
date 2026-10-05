package com.appliedastralsorcery.lumen;

import java.util.List;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.network.chat.Component;

public final class LumenStorage extends ExternalStorageFacade {
    private final ILumenHandler handler;
    private final List<Lumen> types;

    public LumenStorage(ILumenHandler handler) {
        this.handler = handler;
        // Stable slots matter when draining the last of a type changes the handler's contents list.
        this.types = RegistriesAS.REGISTRY_LUMEN.stream().filter(lumen -> lumen != LumenAS.NONE.get()).toList();
    }

    public static ILumenHandler.Action action(Actionable mode) {
        return mode == Actionable.SIMULATE ? ILumenHandler.Action.SIMULATE : ILumenHandler.Action.EXECUTE;
    }

    public static int clampAmount(long amount) {
        return Math.clamp(amount, 0, Integer.MAX_VALUE);
    }

    @Override
    public int getSlots() {
        return types.size();
    }

    @Override
    public GenericStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= types.size()) {
            return null;
        }
        var stack = handler.getContainedLumen(types.get(slot)).orElse(null);
        return stack == null || stack.isEmpty() ? null : new GenericStack(LumenKey.of(stack.getLumen()), stack.getAmount());
    }

    @Override
    public LumenKeyType getKeyType() {
        return LumenKeyType.INSTANCE;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("storage.appliedas.lumen");
    }

    @Override
    public int insertExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof LumenKey key) || amount <= 0) {
            return 0;
        }
        return handler.fill(key.lumen().stack(amount), action(mode));
    }

    @Override
    public int extractExternal(AEKey what, int amount, Actionable mode) {
        if (!(what instanceof LumenKey key) || amount <= 0) {
            return 0;
        }
        return handler.drain(key.lumen(), amount, action(mode)).getAmount();
    }

    @Override
    public boolean containsAnyFuzzy(Set<AEKey> keys) {
        return handler.getContainedLumen().stream().filter(stack -> !stack.isEmpty())
                .anyMatch(stack -> keys.contains(LumenKey.of(stack.getLumen())));
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var stack : handler.getContainedLumen()) {
            if (!stack.isEmpty() && (!extractableOnly
                    || !handler.drain(stack.copy(), ILumenHandler.Action.SIMULATE).isEmpty())) {
                out.add(LumenKey.of(stack.getLumen()), stack.getAmount());
            }
        }
    }
}
