package com.appliedastralsorcery.integration.jei;

import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.client.MEChaliceScreen;
import com.appliedastralsorcery.client.MELumenFilamentScreen;
import com.appliedastralsorcery.client.MELumenArrayScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class AppliedAstralJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(MEChaliceScreen.class, new ChaliceGhostIngredientHandler());
        registration.addGhostIngredientHandler(MELumenFilamentScreen.class, new FilamentGhostIngredientHandler());
        registration.addGhostIngredientHandler(MELumenArrayScreen.class, new ArrayGhostIngredientHandler());
    }
}
