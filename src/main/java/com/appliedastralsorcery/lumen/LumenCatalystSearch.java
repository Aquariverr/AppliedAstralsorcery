package com.appliedastralsorcery.lumen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/** Recipe index only: amounts and component variants always come from the current ME inventory. */
final class LumenCatalystSearch {
    private Collection<RecipeHolder<?>> recipes = List.of();
    private Lumen lumen;
    private boolean alchemy;
    private boolean fullScan;
    private List<AEItemKey> itemTypes = List.of();
    private List<Ingredient> inputs = List.of();

    List<AEItemKey> candidates(Level level, Lumen selected, boolean combination, KeyCounter available) {
        var manager = level.getRecipeManager();
        var current = manager.getRecipes();
        if (recipes != current || lumen != selected || alchemy != combination) {
            recipes = current;
            lumen = selected;
            alchemy = combination;
            fullScan = false;
            var types = new LinkedHashSet<AEItemKey>();
            var matchingInputs = new ArrayList<Ingredient>();
            for (var holder : manager.getAllRecipesFor(RecipeTypesAS.LUMEN_GENERATION_TYPE.get())) {
                var recipe = holder.value();
                if (recipe.getProducedLumen() != selected
                        || combination == recipe.getLumenCombinationInputs().isEmpty()) continue;
                var ingredient = recipe.getInput();
                matchingInputs.add(ingredient);
                // Custom ingredients may accept items absent from their display alternatives.
                if (!ingredient.isSimple()) fullScan = true;
                else for (var stack : ingredient.getItems()) types.add(AEItemKey.of(stack.getItem()));
            }
            itemTypes = List.copyOf(types);
            inputs = List.copyOf(matchingInputs);
        }
        var result = new ArrayList<AEItemKey>();
        if (fullScan) {
            for (var entry : available) {
                if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && accepts(key)) result.add(key);
            }
        } else {
            for (var type : itemTypes) {
                // IGNORE_ALL retains all component variants of this item; native insertion checks each one.
                for (var entry : available.findFuzzy(type, FuzzyMode.IGNORE_ALL)) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key) result.add(key);
                }
            }
        }
        return result;
    }

    private boolean accepts(AEItemKey key) {
        var stack = key.toStack(1);
        for (var input : inputs) if (input.test(stack)) return true;
        return false;
    }
}
