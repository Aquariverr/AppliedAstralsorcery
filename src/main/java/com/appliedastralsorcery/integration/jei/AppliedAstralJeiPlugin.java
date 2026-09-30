package com.appliedastralsorcery.integration.jei;

import java.util.List;

import appeng.client.gui.implementations.IOBusScreen;
import appeng.client.gui.implementations.StorageBusScreen;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.client.AutoChiselScreen;
import com.appliedastralsorcery.client.MEChaliceScreen;
import com.appliedastralsorcery.client.MELumenFilamentScreen;
import com.appliedastralsorcery.client.MELumenArrayScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

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
        // Keep JEI's ingredient list clear of the chisel's configuration tab.
        registration.addGuiContainerHandler(AutoChiselScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(AutoChiselScreen screen) {
                return screen.getExtraAreas();
            }
        });
        if (ModList.get().isLoaded("ae2jeiintegration")) {
            // Extend the existing handler so other addons' ingredient types keep working too.
            LumenIngredientConverter.register();
        } else {
            registration.addGhostIngredientHandler(IOBusScreen.class, new BusGhostIngredientHandler<>());
            registration.addGhostIngredientHandler(StorageBusScreen.class, new BusGhostIngredientHandler<>());
        }
    }
}
