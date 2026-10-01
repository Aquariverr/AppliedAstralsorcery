package com.appliedastralsorcery.transmutation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Matches quantities across slots, including overlapping ingredients and repeated inputs. */
final class TransmutationPlan {
    private TransmutationPlan() {}

    static int[] match(List<Ingredient> ingredients, ItemStackHandler inventory, int slots) {
        if (ingredients.isEmpty()) return null;
        var candidates = new ArrayList<int[]>();
        for (var ingredient : ingredients) {
            var matches = new ArrayList<Integer>();
            for (int slot = 0; slot < slots; slot++) {
                if (!inventory.getStackInSlot(slot).isEmpty() && ingredient.test(inventory.getStackInSlot(slot)))
                    matches.add(slot);
            }
            if (matches.isEmpty()) return null;
            candidates.add(matches.stream().mapToInt(Integer::intValue).toArray());
        }
        candidates.sort(Comparator.comparingInt(a -> a.length));
        int[] available = new int[slots], consumed = new int[slots];
        for (int slot = 0; slot < slots; slot++) available[slot] = inventory.getStackInSlot(slot).getCount();
        return assign(candidates, 0, available, consumed) ? consumed : null;
    }

    private static boolean assign(List<int[]> candidates, int index, int[] available, int[] consumed) {
        if (index == candidates.size()) return true;
        for (int slot : candidates.get(index)) {
            if (available[slot] <= 0) continue;
            available[slot]--;
            consumed[slot]++;
            if (assign(candidates, index + 1, available, consumed)) return true;
            available[slot]++;
            consumed[slot]--;
        }
        return false;
    }

    /** Build the complete output transaction before consuming any input. */
    static ItemStack[] output(List<ItemStack> outputs, ItemStackHandler inventory, int start, int slots) {
        var buffer = new ItemStackHandler(slots);
        for (int slot = 0; slot < slots; slot++) buffer.setStackInSlot(slot, inventory.getStackInSlot(start + slot).copy());
        for (var output : outputs) {
            var remaining = output.copy();
            // Fill existing stacks first so an empty slot does not strand usable stack space.
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
                    if (buffer.getStackInSlot(slot).isEmpty() == (pass == 1))
                        remaining = buffer.insertItem(slot, remaining, false);
                }
            }
            if (!remaining.isEmpty()) return null;
        }
        var result = new ItemStack[slots];
        for (int slot = 0; slot < slots; slot++) result[slot] = buffer.getStackInSlot(slot).copy();
        return result;
    }
}
