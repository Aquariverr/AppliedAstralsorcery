package com.appliedastralsorcery.integration.jei;

import java.util.List;
import java.util.stream.IntStream;
import com.appliedastralsorcery.client.StarlightTransmutationScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public final class TransmutationGhostIngredientHandler implements IGhostIngredientHandler<StarlightTransmutationScreen> {
    @Override public <I> List<Target<I>> getTargetsTyped(StarlightTransmutationScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!screen.canEditMarkers() || !(ingredient.getIngredient() instanceof ItemStack)) return List.of();
        return IntStream.range(0, 9).<Target<I>>mapToObj(slot -> new Target<>() {
            @Override public Rect2i getArea() { return screen.getMarkerArea(slot); }
            @Override public void accept(I ingredient) {
                if (ingredient instanceof ItemStack stack) screen.acceptJeiMarker(slot, stack);
            }
        }).toList();
    }
    @Override public void onComplete() {}
}
