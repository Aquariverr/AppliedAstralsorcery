package com.appliedastralsorcery.mixin;

import hellfirepvp.astralsorcery.client.helper.GatewayInterfaceRenderHelper;
import hellfirepvp.astralsorcery.client.helper.GatewayUserInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GatewayInterfaceRenderHelper.class, remap = false)
public interface GatewayUiAccessor {
    @Accessor("currentUI") void appliedas$setCurrentUI(GatewayUserInterface ui);
}
