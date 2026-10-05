package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.infuser.MEStarlightInfuserBlockEntity;
import hellfirepvp.astralsorcery.common.recipe.infusion.ActiveInfusionRecipe;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ActiveInfusionRecipe.class, remap = false)
public abstract class ActiveInfusionRecipeResourceMixin {
    @Inject(method = "consumeInputs", at = @At("HEAD"), cancellable = true)
    private void appliedas$preferMEToPool(TileInfuser tile, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (tile instanceof MEStarlightInfuserBlockEntity infuser) {
            var active = (ActiveInfusionRecipe) (Object) this;
            cir.setReturnValue(active.getRecipe(level)
                    .map(recipe -> infuser.consumeInfusionInputs(active, recipe, level)).orElse(false));
        }
    }
}
