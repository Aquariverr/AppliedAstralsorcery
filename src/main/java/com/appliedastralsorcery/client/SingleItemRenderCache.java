package com.appliedastralsorcery.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;

/** Count-one render snapshots; weak keys release entries once inventory stacks are replaced. */
public final class SingleItemRenderCache {
    private final Map<ItemStack, ItemStack> copies = new WeakHashMap<>();

    public ItemStack get(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var copy = copies.get(stack);
        if (copy == null || copy.getCount() != 1 || !ItemStack.isSameItemSameComponents(stack, copy)) {
            copy = stack.copyWithCount(1);
            copies.put(stack, copy);
        }
        return copy;
    }
}
