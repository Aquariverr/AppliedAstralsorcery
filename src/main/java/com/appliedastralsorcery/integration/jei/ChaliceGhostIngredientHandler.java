package com.appliedastralsorcery.integration.jei;

import java.util.List;

import com.appliedastralsorcery.client.MEChaliceScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

public final class ChaliceGhostIngredientHandler implements IGhostIngredientHandler<MEChaliceScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(MEChaliceScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        var fluid = GhostIngredientResolver.resolveFluid(ingredient.getIngredient());
        if (fluid == null || !gui.getMenu().getTypes().contains(fluid.getFluid())) {
            return List.of();
        }
        return gui.getJeiDropAreas().stream().<Target<I>>map(area -> new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                var selected = GhostIngredientResolver.resolveFluid(dropped);
                if (selected != null) {
                    gui.acceptJeiFluid(selected);
                }
            }
        }).toList();
    }

    @Override
    public void onComplete() {}
}
