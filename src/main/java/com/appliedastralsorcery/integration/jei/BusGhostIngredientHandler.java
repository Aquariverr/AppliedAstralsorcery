package com.appliedastralsorcery.integration.jei;

import java.util.List;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.integration.modules.itemlists.DropTargets;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class BusGhostIngredientHandler<T extends AEBaseScreen<?>> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T gui, ITypedIngredient<I> ingredient, boolean doStart) {
        var stack = toStack(ingredient.getIngredient());
        if (stack == null) return List.of();
        return DropTargets.getTargets(gui).stream()
                .filter(target -> target.canDrop(stack))
                .<Target<I>>map(target -> new Target<>() {
                    @Override public Rect2i getArea() { return target.area(); }

                    @Override public void accept(I dropped) {
                        var selected = toStack(dropped);
                        if (selected == null || Minecraft.getInstance().screen != gui) return;
                        // Recheck active slots: capacity upgrades or the screen may change during a drag.
                        for (var current : DropTargets.getTargets(gui)) {
                            if (current.area().getX() == target.area().getX()
                                    && current.area().getY() == target.area().getY() && current.canDrop(selected)) {
                                current.drop(selected);
                                return;
                            }
                        }
                    }
                }).toList();
    }

    @Nullable
    private static GenericStack toStack(Object ingredient) {
        return switch (ingredient) {
            case LumenStack lumenStack -> {
                var lumen = GhostIngredientResolver.resolveLumen(lumenStack);
                yield lumen == null ? null : new GenericStack(LumenKey.of(lumen), lumenStack.getAmount());
            }
            case ItemStack itemStack -> GenericStack.fromItemStack(itemStack);
            case FluidStack fluidStack -> GenericStack.fromFluidStack(fluidStack);
            default -> null;
        };
    }

    @Override public void onComplete() {}
}
