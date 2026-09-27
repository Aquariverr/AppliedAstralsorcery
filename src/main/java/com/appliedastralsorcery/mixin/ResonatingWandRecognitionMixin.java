package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.wand.MEResonatingWandItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.block.tile.AltarBlock;
import hellfirepvp.astralsorcery.common.block.tile.InfuserBlock;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** AS tests exact item identity in these blocks instead of accepting WandItem subclasses. */
@Mixin(value = {AltarBlock.class, InfuserBlock.class}, remap = false)
public abstract class ResonatingWandRecognitionMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/Holder;)Z"))
    private static boolean appliedas$recognizeWand(ItemStack stack, Holder<Item> expected, Operation<Boolean> original) {
        return original.call(stack, expected)
                || expected.value() == ItemsAS.WAND.get() && stack.getItem() instanceof MEResonatingWandItem;
    }
}
