package com.appliedastralsorcery.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.integration.jei.ingredient.LumenIngredientType;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipeGrid;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.util.IngredientUtil;
import hellfirepvp.astralsorcery.common.util.data.CountIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AltarPatternTransferGameTests {
    @GameTest(template = "wand_empty")
    public static void patternInputsIncludeResourcesOnceAndKeepGridContainers(GameTestHelper helper) {
        var fluidIngredient = SizedFluidIngredient.of(Fluids.WATER, 1000);
        var container = IngredientUtil.getRandomDisplayStack(fluidIngredient, 0);
        var namedIron = new ItemStack(Items.IRON_INGOT);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("Selected altar input"));
        var additional = new CountIngredient(Ingredient.of(Items.DIAMOND, Items.EMERALD), 3);
        var grid = AltarRecipeGrid.create(Map.of(
                'A', IngredientBridge.of(fluidIngredient),
                'B', IngredientBridge.of(Ingredient.of(Items.IRON_INGOT))),
                List.of("AB ", "   ", "   "), List.of("     ", "     ", "     ", "     ", "     "));
        var fluids = List.of(new FluidStack(Fluids.WATER, 600), new FluidStack(Fluids.WATER, 400),
                new FluidStack(Fluids.LAVA, 250), FluidStack.EMPTY);
        var lumen = List.of(LumenAS.AEVITAS.stack(123), LumenAS.AEVITAS.stack(77),
                LumenAS.EVORSIO.stack(55), LumenAS.NONE.stack(0));
        var recipe = new AltarRecipe(TileAltar.AltarType.ILLUMINATION, grid,
                List.of(new ItemStack(Items.STICK)), Optional.empty(), 0, 20, false, false, Set.of(),
                lumen, fluids, List.of(additional), Set.of(), List.of());
        IRecipeSlotsView slots = () -> List.of(
                slot(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, List.of(container)),
                slot(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, List.of(namedIron)),
                slot(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, additional.getItems()),
                slot(RecipeIngredientRole.INPUT, NeoForgeTypes.FLUID_STACK, fluids),
                slot(RecipeIngredientRole.INPUT, LumenIngredientType.INSTANCE, lumen),
                slot(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.BLAZE_ROD))));

        var inputs = AltarPatternTransferHandler.getInputs(recipe, slots);
        helper.assertTrue(grid.getInputs().getFirst().test(container),
                "The fixture must use a real filled container accepted by the altar's grid ingredient");
        helper.assertTrue(inputs.size() == 9 && amount(inputs, AEItemKey.of(container)) == 1,
                "The fluid grid slot must remain a container item separate from additional raw fluid");
        helper.assertTrue(amount(inputs, AEItemKey.of(namedIron)) == 1,
                "Selected item components must survive pattern extraction");
        helper.assertTrue(inputs.get(2).size() == 2 && inputs.get(2).stream().allMatch(stack -> stack.amount() == 3),
                "Additional item alternatives must retain their CountIngredient quantity");
        helper.assertTrue(amount(inputs, AEFluidKey.of(Fluids.WATER)) == 1000
                        && amount(inputs, AEFluidKey.of(Fluids.LAVA)) == 250,
                "Duplicate fluid costs must preserve exact mB without counting displayed fluid slots twice");
        helper.assertTrue(amount(inputs, LumenKey.of(LumenAS.AEVITAS.get())) == 200
                        && amount(inputs, LumenKey.of(LumenAS.EVORSIO.get())) == 55,
                "Duplicate lumen costs must preserve exact units without counting displayed lumen slots twice");
        helper.assertTrue(amount(inputs, AEItemKey.of(Items.BLAZE_ROD)) == 0,
                "JEI catalysts must not become consumed pattern inputs");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void patternOutputsPreserveCountsComponentsAndSlotOrder(GameTestHelper helper) {
        var primary = new ItemStack(Items.STICK, 4);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Altar result"));
        var secondary = new ItemStack(Items.GOLD_INGOT, 2);
        IRecipeSlotsView slots = () -> List.of(
                slot(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, List.of(new ItemStack(Items.DIAMOND))),
                slot(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, List.of(primary, new ItemStack(Items.EMERALD))),
                slot(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, List.of(secondary)),
                slot(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, List.of(ItemStack.EMPTY)));
        var outputs = AltarPatternTransferHandler.getOutputs(slots);
        helper.assertTrue(outputs.size() == 2 && outputs.getFirst().what().equals(AEItemKey.of(primary))
                        && outputs.getFirst().amount() == 4
                        && outputs.get(1).what().equals(AEItemKey.of(secondary)) && outputs.get(1).amount() == 2,
                "Each output slot must encode its first candidate with exact components and quantity in slot order");
        helper.assertTrue(primary.getCount() == 4
                        && primary.get(DataComponents.CUSTOM_NAME).equals(Component.literal("Altar result")),
                "Reading pattern outputs must not mutate the JEI ingredient");
        helper.succeed();
    }

    private static long amount(List<List<GenericStack>> inputs, AEKey key) {
        return inputs.stream().flatMap(List::stream).filter(stack -> stack.what().equals(key))
                .mapToLong(GenericStack::amount).sum();
    }

    private static <T> IRecipeSlotView slot(RecipeIngredientRole role, IIngredientType<T> type, List<T> values) {
        var ingredients = new ArrayList<ITypedIngredient<?>>();
        for (var value : values) ingredients.add(new TypedIngredient<>(type, value));
        return new SlotView(role, ingredients);
    }

    private record TypedIngredient<T>(IIngredientType<T> type, T ingredient) implements ITypedIngredient<T> {
        @Override public IIngredientType<T> getType() { return type; }
        @Override public T getIngredient() { return ingredient; }
        @Override public ITypedIngredient<T> normalize(IIngredientHelper<T> helper) { return this; }
    }

    // A concrete stub avoids Proxy's eager reflection over client-only drawing method signatures on the server.
    private record SlotView(RecipeIngredientRole role, List<ITypedIngredient<?>> ingredients) implements IRecipeSlotView {
        @Override public Stream<ITypedIngredient<?>> getAllIngredients() { return ingredients.stream(); }
        @Override public List<ITypedIngredient<?>> getAllIngredientsList() { return ingredients; }
        @Override public Optional<ITypedIngredient<?>> getDisplayedIngredient() { return ingredients.stream().findFirst(); }
        @Override public Stream<ITypedIngredient<?>> getDisplayedIngredients() { return ingredients.stream(); }
        @Override public Optional<TagKey<?>> getTagKey() { return Optional.empty(); }
        @Override public RecipeIngredientRole getRole() { return role; }
        @Override public Optional<String> getSlotName() { return Optional.empty(); }
        @Override public void drawHighlight(GuiGraphics graphics, int color) { throw new UnsupportedOperationException(); }
    }
}
