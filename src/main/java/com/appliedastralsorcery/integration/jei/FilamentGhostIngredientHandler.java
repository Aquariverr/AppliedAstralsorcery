package com.appliedastralsorcery.integration.jei;

import java.util.List;

import com.appliedastralsorcery.client.MELumenFilamentScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

public final class FilamentGhostIngredientHandler implements IGhostIngredientHandler<MELumenFilamentScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(MELumenFilamentScreen gui, ITypedIngredient<I> ingredient,
            boolean doStart) {
        var lumen = GhostIngredientResolver.resolveLumen(ingredient.getIngredient());
        if (lumen == null || !gui.getMenu().getAvailableLumen().contains(lumen)) {
            return List.of();
        }
        return gui.getJeiDropAreas().stream().<Target<I>>map(area -> new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                var selected = GhostIngredientResolver.resolveLumen(dropped);
                if (selected != null) {
                    gui.acceptJeiLumen(selected);
                }
            }
        }).toList();
    }

    @Override
    public void onComplete() {}
}
