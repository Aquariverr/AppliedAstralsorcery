package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.altar.AltarAutomationBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileAltar.class, remap = false)
public abstract class AltarAutomationMixin {
    @Inject(method = "doCraftingCycle", at = @At("HEAD"), cancellable = true)
    private void appliedas$pauseUnloadedInterface(ServerLevel level, CallbackInfo ci) {
        if (AltarAutomationBlockEntity.shouldPause((TileAltar) (Object) this)) ci.cancel();
    }

    @WrapOperation(method = "lambda$doCraftingCycle$1", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/recipe/altar/AltarRecipe;createOutput(Lhellfirepvp/astralsorcery/common/recipe/altar/AltarCraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void appliedas$returnOutputs(AltarRecipe recipe, AltarCraftingInput input,
            HolderLookup.Provider registries, Operation<Void> original) {
        if (!AltarAutomationBlockEntity.collectCraftedOutput(recipe, input, registries)) {
            original.call(recipe, input, registries);
        }
    }
}
