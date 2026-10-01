package com.appliedastralsorcery.mixin;

import com.appliedastralsorcery.lumen.MELumenAlchemyArrayBlockEntity;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerView;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileLumenArray.class, remap = false)
public abstract class LumenArrayCraftingMixin {
    @Shadow private int getLumenGenerationAttempts(float multiplier) { throw new AssertionError(); }

    @Redirect(method = "doCraftingCycle", at = @At(value = "INVOKE", target =
            "Lhellfirepvp/astralsorcery/common/lumen/capability/LumenHandlerView;fill(Lhellfirepvp/astralsorcery/common/lumen/LumenStack;Lhellfirepvp/astralsorcery/common/lumen/ILumenHandler$Action;)I"))
    private int appliedas$exportGeneratedOverflow(LumenHandlerView handler, LumenStack stack, ILumenHandler.Action action) {
        return (Object) this instanceof MELumenArrayBlockEntity array
                ? array.fillGeneratedLumen(stack, action) : handler.fill(stack, action);
    }

    @Inject(method = "doCraftingCycle", at = @At("HEAD"), cancellable = true)
    private void appliedas$meCraftingControl(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof MELumenArrayBlockEntity array && !array.isWorkAllowed()) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof MELumenAlchemyArrayBlockEntity array) {
            array.craftFromNetwork(level, recipe -> getLumenGenerationAttempts(recipe.getProductionAttemptMultiplier()));
            ci.cancel();
        }
    }
}
