package com.appliedastralsorcery.integration.jei;

import appeng.api.stacks.GenericStack;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.integration.jei.ingredient.LumenIngredientType;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/** Loaded only when the optional AE2 JEI Integration mod is present. */
public final class LumenIngredientConverter implements IngredientConverter<LumenStack> {
    public static void register() {
        IngredientConverters.register(new LumenIngredientConverter());
    }

    @Override
    public IIngredientType<LumenStack> getIngredientType() {
        return LumenIngredientType.INSTANCE;
    }

    @Override
    @Nullable
    public LumenStack getIngredientFromStack(GenericStack stack) {
        return stack.what() instanceof LumenKey key
                ? key.lumen().stack(Math.clamp(stack.amount(), 1, Integer.MAX_VALUE)) : null;
    }

    @Override
    @Nullable
    public GenericStack getStackFromIngredient(LumenStack ingredient) {
        var lumen = GhostIngredientResolver.resolveLumen(ingredient);
        return lumen == null ? null : new GenericStack(LumenKey.of(lumen), ingredient.getAmount());
    }
}
