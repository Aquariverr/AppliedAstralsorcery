package com.appliedastralsorcery.mixin;

import hellfirepvp.astralsorcery.client.helper.GatewayInterfaceRenderHelper;
import hellfirepvp.astralsorcery.common.data.sync.client.CelestialGatewayClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CelestialGatewayClientData.class, remap = false)
public abstract class GatewayDestinationSyncMixin {
    @Inject(method = {"receiveAll", "receiveChanges", "clear"}, at = @At("RETURN"))
    private void appliedas$refreshStars(CallbackInfo ci) {
        ((GatewayUiAccessor) (Object) GatewayInterfaceRenderHelper.getInstance()).appliedas$setCurrentUI(null);
    }
}
