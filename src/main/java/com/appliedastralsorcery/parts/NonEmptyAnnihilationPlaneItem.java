package com.appliedastralsorcery.parts;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.List;

import appeng.core.definitions.AEParts;
import appeng.items.parts.PartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class NonEmptyAnnihilationPlaneItem extends PartItem<NonEmptyAnnihilationPlanePart> {
    public NonEmptyAnnihilationPlaneItem() {
        super(new Properties(), NonEmptyAnnihilationPlanePart.class, NonEmptyAnnihilationPlanePart::new);
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isEnchantable(ItemStack stack) {
        return AEParts.ANNIHILATION_PLANE.asItem().isEnchantable(stack);
    }

    @Override
    @ParametersAreNonnullByDefault
    public int getEnchantmentValue(ItemStack stack) {
        return AEParts.ANNIHILATION_PLANE.asItem().getEnchantmentValue(stack);
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return AEParts.ANNIHILATION_PLANE.asItem().isBookEnchantable(stack, book);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        AEParts.ANNIHILATION_PLANE.asItem().appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.appliedas.non_empty_annihilation_plane"));
    }
}
