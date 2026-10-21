package com.appliedastralsorcery.transmutation;

import java.util.Arrays;

import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Reuses both successful and blocked plans while every output slot is unchanged. */
final class TransmutationOutputCache {
    private FocalCombineRecipe recipe;
    private final ItemStack[] snapshot = new ItemStack[StarlightTransmutationBlockEntity.OUTPUT_SLOTS];
    private ItemStack[] output;

    TransmutationOutputCache() { Arrays.fill(snapshot, ItemStack.EMPTY); }

    void invalidate() { recipe = null; }

    ItemStack[] plan(FocalCombineRecipe selected, ItemStackHandler inventory) {
        boolean changed = recipe != selected;
        for (int slot = 0; !changed && slot < snapshot.length; slot++) {
            changed = !ItemStack.matches(snapshot[slot],
                    inventory.getStackInSlot(StarlightTransmutationBlockEntity.INPUT_SLOTS + slot));
        }
        if (changed) {
            recipe = selected;
            output = TransmutationPlan.output(selected.getOutputs(), inventory);
            for (int slot = 0; slot < snapshot.length; slot++) {
                snapshot[slot] = inventory.getStackInSlot(StarlightTransmutationBlockEntity.INPUT_SLOTS + slot).copy();
            }
        }
        return output;
    }
}
