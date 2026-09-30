package com.appliedastralsorcery.altar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.altar.output.AltarOutputSetBlock;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Plans against copies: rejection must never take ownership of a provider's inputs. */
public record AltarRecipePlan(RecipeHolder<AltarRecipe> recipe, List<ItemStack> grid,
        List<ItemStack> relays, List<ItemStack> additional, List<GenericStack> resources) {
    private record Requirement(int index, int count, Predicate<ItemStack> matches) {}

    public static AltarRecipePlan create(TileAltar altar, RecipeHolder<AltarRecipe> holder,
            IPatternDetails pattern, KeyCounter[] supplied) {
        var level = altar.getLevel();
        if (level == null) return null;
        var recipe = holder.value();
        if (!altar.getTileData().getAltarType().isThisLaterOrEqual(recipe.getRequiredType())
                || recipe.getOutputModifiers().stream().anyMatch(AltarOutputSetBlock.class::isInstance)
                || recipe.getFocusConstellation().isPresent()
                && !recipe.getFocusConstellation().equals(altar.getTileData().getFocusedConstellation())) return null;

        var counts = new KeyCounter();
        for (var counter : supplied) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey || entry.getKey() instanceof AEFluidKey
                        || entry.getKey() instanceof LumenKey) || entry.getLongValue() <= 0
                        || entry.getLongValue() > Integer.MAX_VALUE) return null;
                counts.add(entry.getKey(), entry.getLongValue());
            }
        }
        var requiredResources = new KeyCounter();
        for (var fluid : recipe.getRequiredFluid()) {
            var key = AEFluidKey.of(fluid);
            if (key != null) requiredResources.add(key, fluid.getAmount());
        }
        for (var lumen : recipe.getRequiredLumen()) {
            if (!lumen.isEmpty()) requiredResources.add(LumenKey.of(lumen.getLumen()), lumen.getAmount());
        }
        List<GenericStack> resources = new ArrayList<>();
        List<ItemStack> pool = new ArrayList<>();
        long total = 0;
        for (var entry : counts) {
            if (entry.getLongValue() > Integer.MAX_VALUE) return null;
            if (!(entry.getKey() instanceof AEItemKey item)) {
                // Old item-only patterns can still use resources supplied separately to the altar or interface.
                if (entry.getLongValue() > requiredResources.get(entry.getKey())) return null;
                resources.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                continue;
            }
            total += entry.getLongValue();
            if (total > 4096) return null;
            pool.add(item.toStack((int) entry.getLongValue()));
        }
        List<Requirement> requirements = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            var ingredient = recipe.getGrid().getInputs().get(i);
            if (!ingredient.isEmpty()) requirements.add(new Requirement(i, 1, ingredient::test));
        }
        for (int i = 0; i < 25; i++) {
            var ingredient = recipe.getGrid().getRelayInputs().get(i);
            if (!ingredient.isEmpty()) requirements.add(new Requirement(9 + i, 1, ingredient::test));
        }
        for (int i = 0; i < recipe.getRequiredAdditionalInputs().size(); i++) {
            var ingredient = recipe.getRequiredAdditionalInputs().get(i);
            requirements.add(new Requirement(34 + i, ingredient.count(), ingredient.ingredient()));
        }
        if (requirements.stream().mapToInt(Requirement::count).sum() != total) return null;
        // Specific ingredients go first; backtracking handles overlapping tags without greedy misallocation.
        requirements.sort(Comparator.comparingLong(r -> pool.stream().filter(r.matches()).count()));
        List<ItemStack> assigned = new ArrayList<>();
        for (int i = 0; i < 34 + recipe.getRequiredAdditionalInputs().size(); i++) assigned.add(ItemStack.EMPTY);
        if (!assign(requirements, 0, pool, assigned, new int[]{10000})) return null;
        var plan = new AltarRecipePlan(holder, List.copyOf(assigned.subList(0, 9)),
                List.copyOf(assigned.subList(9, 34)), List.copyOf(assigned.subList(34, assigned.size())),
                List.copyOf(resources));
        var display = AltarCraftingInput.createDisplay(altar.getTileData().getFocusedConstellation().orElse(null),
                plan.grid(), plan.relays());
        var outputs = new KeyCounter();
        for (var stack : recipe.getOutputsForDisplay(display, level.registryAccess())) {
            var key = AEItemKey.of(stack);
            if (key != null) outputs.add(key, stack.getCount());
        }
        // Container returns may also be listed on a processing pattern.
        for (var stack : assigned.subList(0, 34)) {
            if (!stack.isEmpty()) {
                var remainder = stack.getCraftingRemainingItem();
                var key = AEItemKey.of(remainder);
                if (key != null) outputs.add(key, remainder.getCount());
            }
        }
        if (pattern.getOutputs().isEmpty()) return null;
        for (var output : pattern.getOutputs()) {
            if (!(output.what() instanceof AEItemKey) || output.amount() <= 0
                    || outputs.get(output.what()) < output.amount()) return null;
            outputs.remove(output.what(), output.amount());
        }
        return plan;
    }

    private static boolean assign(List<Requirement> requirements, int index, List<ItemStack> pool,
            List<ItemStack> assigned, int[] budget) {
        if (--budget[0] < 0) return false;
        if (index == requirements.size()) return true;
        var requirement = requirements.get(index);
        for (var stack : pool) {
            if (stack.getCount() < requirement.count() || !requirement.matches().test(stack)) continue;
            assigned.set(requirement.index(), stack.copyWithCount(requirement.count()));
            stack.shrink(requirement.count());
            if (assign(requirements, index + 1, pool, assigned, budget)) return true;
            stack.grow(requirement.count());
        }
        assigned.set(requirement.index(), ItemStack.EMPTY);
        return false;
    }
}
