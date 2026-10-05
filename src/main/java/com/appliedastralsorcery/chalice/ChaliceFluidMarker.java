package com.appliedastralsorcery.chalice;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public final class ChaliceFluidMarker {
    private ChaliceFluidMarker() {}

    @Nullable
    public static AEFluidKey direct(Object ingredient) {
        if (ingredient instanceof FluidStack fluid) return AEFluidKey.of(fluid);
        if (ingredient instanceof AEFluidKey fluid) return fluid;
        if (ingredient instanceof ItemStack item) ingredient = GenericStack.unwrapItemStack(item);
        return ingredient instanceof GenericStack stack && stack.what() instanceof AEFluidKey fluid ? fluid : null;
    }

    @Nullable
    @SuppressWarnings("UnstableApiUsage") // AE2 exposes container contents through its experimental strategy API.
    public static AEFluidKey carried(ItemStack stack, int button) {
        if (button == 1 && !stack.isEmpty()) {
            var action = ContainerItemStrategies.getEmptyingAction(stack.copyWithCount(1));
            if (action != null && action.what() instanceof AEFluidKey fluid) return fluid;
        }
        return direct(stack);
    }
}
