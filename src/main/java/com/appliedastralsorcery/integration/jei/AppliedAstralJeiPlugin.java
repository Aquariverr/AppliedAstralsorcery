package com.appliedastralsorcery.integration.jei;

import java.util.List;

import appeng.client.gui.implementations.IOBusScreen;
import appeng.client.gui.implementations.StorageBusScreen;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.client.StarlightTransmutationScreen;
import com.appliedastralsorcery.transmutation.StarlightTransmutationMenu;
import com.appliedastralsorcery.client.AutoChiselScreen;
import com.appliedastralsorcery.client.MEChaliceScreen;
import com.appliedastralsorcery.client.MELumenFilamentScreen;
import com.appliedastralsorcery.client.MELumenArrayScreen;
import hellfirepvp.astralsorcery.common.integration.jei.category.AltarRecipeCategory;
import hellfirepvp.astralsorcery.common.integration.jei.category.FocalCombinationRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

@JeiPlugin
@SuppressWarnings("unused") // JEI discovers this entry point by scanning @JeiPlugin annotations.
public final class AppliedAstralJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(AppliedAstralsorcery.MOD_ID, "jei");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        // A category-specific handler takes precedence over AE2 JEI Integration's universal handler.
        registration.addRecipeTransferHandler(new AltarPatternTransferHandler(registration.getTransferHelper()),
                AltarRecipeCategory.RECIPE_TYPE);
        registration.addRecipeTransferHandler(StarlightTransmutationMenu.class, ModContent.TRANSMUTATION_MENU.get(),
                FocalCombinationRecipeCategory.RECIPE_TYPE, 0, 9, 18, 36);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModContent.STARLIGHT_TRANSMUTATION_CHAMBER_ITEM.toStack(),
                FocalCombinationRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(StarlightTransmutationScreen.class,
                StarlightTransmutationScreen.RECIPE_X, StarlightTransmutationScreen.RECIPE_Y,
                StarlightTransmutationScreen.RECIPE_WIDTH, StarlightTransmutationScreen.RECIPE_HEIGHT,
                FocalCombinationRecipeCategory.RECIPE_TYPE);
        registration.addGhostIngredientHandler(StarlightTransmutationScreen.class, new TransmutationGhostIngredientHandler());
        registration.addGhostIngredientHandler(MEChaliceScreen.class, new ChaliceGhostIngredientHandler());
        registration.addGhostIngredientHandler(MELumenFilamentScreen.class, new FilamentGhostIngredientHandler());
        registration.addGhostIngredientHandler(MELumenArrayScreen.class, new ArrayGhostIngredientHandler());
        registration.addGuiContainerHandler(AutoChiselScreen.class, new IGuiContainerHandler<>() {
            @Override public List<Rect2i> getGuiExtraAreas(AutoChiselScreen screen) {
                return screen.getExtraAreas();
            }
        });
        if (ModList.get().isLoaded("ae2jeiintegration")) {
            LumenIngredientConverter.register();
        } else {
            registration.addGhostIngredientHandler(IOBusScreen.class, new BusGhostIngredientHandler<>());
            registration.addGhostIngredientHandler(StorageBusScreen.class, new BusGhostIngredientHandler<>());
        }
    }
}
