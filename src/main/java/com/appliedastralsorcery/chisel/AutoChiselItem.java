package com.appliedastralsorcery.chisel;

import javax.annotation.ParametersAreNonnullByDefault;

import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class AutoChiselItem extends BlockItem {
    public AutoChiselItem(AutoChiselBlock block) {
        super(block, new Properties());
    }

    @Override public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @ParametersAreNonnullByDefault
    @Override public int getEnchantmentValue(ItemStack stack) {
        return ItemsAS.CHISEL.toStack().getEnchantmentValue();
    }

    @ParametersAreNonnullByDefault
    @Override public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ItemsAS.CHISEL.toStack().supportsEnchantment(enchantment);
    }

    @ParametersAreNonnullByDefault
    @Override public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return ItemsAS.CHISEL.toStack().isPrimaryItemFor(enchantment);
    }
}
