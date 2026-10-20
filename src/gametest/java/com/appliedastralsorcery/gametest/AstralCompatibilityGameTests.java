package com.appliedastralsorcery.gametest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.altar.AltarRecipePlan;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import com.google.gson.JsonParser;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipe;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenGenerationRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AstralCompatibilityGameTests {
    @GameTest(template = "attunement_test")
    public static void allAstralRecipesLoadAndExposeIngredients(GameTestHelper helper) throws IOException {
        var manager = helper.getLevel().getRecipeManager();
        var resources = helper.getLevel().getServer().getResourceManager()
                .listResources("recipe", id -> id.getNamespace().equals("appliedas")
                        && id.getPath().endsWith(".json"));
        int altars = 0;
        int liquids = 0;
        for (var entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var type = json.get("type").getAsString();
                if (!type.equals("astralsorcery:altar_crafting")
                        && !type.equals("astralsorcery:liquid_starlight")) continue;
                var path = entry.getKey().getPath();
                var id = ResourceLocation.fromNamespaceAndPath("appliedas",
                        path.substring("recipe/".length(), path.length() - ".json".length()));
                var recipe = manager.byKey(id).orElseThrow(() -> new AssertionError("Recipe failed to load: " + id)).value();
                if (recipe instanceof AltarRecipe altar) {
                    altars++;
                    for (var input : altar.getRequiredAdditionalInputs()) {
                        helper.assertTrue(input.count() > 0 && !input.ingredient().isEmpty(),
                                "Additional altar ingredients must be available through the current API: " + id);
                    }
                } else if (recipe instanceof LiquidStarlightRecipe liquid) {
                    liquids++;
                    helper.assertTrue(liquid.getInput().count() == 1 && !liquid.getInput().ingredient().isEmpty(),
                            "Liquid Starlight input must be available through the current API: " + id);
                    helper.assertFalse(liquid.consumesInputs(), "Crystal recipes must preserve modifier-owned inputs: " + id);
                    helper.assertTrue(liquid.consumesLiquid() == !id.getPath().endsWith("form_astral_fluix_cluster"),
                            "Crystal growth and merging consume starlight; cluster formation preserves it: " + id);
                    helper.assertTrue(liquid.getOutputModifiers().size() == 1, "Crystal output modifier must load: " + id);
                } else {
                    helper.fail("Unexpected recipe type: " + id);
                }
            }
        }
        helper.assertTrue(altars == 16 && liquids == 3, "All 19 migrated Astral Sorcery recipes must load");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void altarAutomationPlansAdditionalIngredients(GameTestHelper helper) {
        var pos = new BlockPos(10, 2, 10);
        helper.setBlock(pos, BlocksAS.ALTAR_RESONANCE.get());
        var altar = (TileAltar) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var id = ResourceLocation.fromNamespaceAndPath("appliedas", "lumen_storage_component_1k");
        var recipe = (AltarRecipe) helper.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
        var holder = new RecipeHolder<>(id, recipe);
        var supplied = new KeyCounter();
        Stream.concat(recipe.getGrid().getInputs().stream(), recipe.getGrid().getRelayInputs().stream())
                .filter(input -> !input.isEmpty())
                .forEach(input -> supplied.add(AEItemKey.of(input.getItems().findFirst().orElseThrow()), 1));
        helper.assertTrue(AltarRecipePlan.create(altar, holder, new KeyCounter[]{supplied}) == null,
                "A pattern missing the additional aquamarine must be rejected");
        for (var input : recipe.getRequiredAdditionalInputs()) {
            supplied.add(AEItemKey.of(input.ingredient().getItems()[0]), input.count());
        }
        var plan = AltarRecipePlan.create(altar, holder, new KeyCounter[]{supplied});
        helper.assertTrue(plan != null && plan.additional().size() == 2
                        && plan.additional().stream().mapToInt(ItemStack::getCount).sum() == 2,
                "Altar automation must plan both additional aquamarine inputs without a linkage error");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void arraysResolveCatalystsInTheirWorld(GameTestHelper helper) throws ReflectiveOperationException {
        // Exercise the hook declared by the loaded AS version, including virtual dispatch on alchemy arrays.
        var nativeLookup = Arrays.stream(TileLumenArray.Data.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("findMatchingRecipe"))
                .findFirst().orElseThrow();
        nativeLookup.setAccessible(true);
        for (boolean alchemy : new boolean[]{false, true}) {
            var pos = new BlockPos(alchemy ? 13 : 7, 2, 10);
            helper.setBlock(pos, alchemy ? ModContent.ME_LUMEN_ALCHEMY_ARRAY.get() : ModContent.ME_LUMEN_ARRAY.get());
            var array = (MELumenArrayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.LUMEN_GENERATION_TYPE.get())
                    .stream().map(RecipeHolder::value)
                    .filter(candidate -> alchemy != candidate.getLumenCombinationInputs().isEmpty()
                            && candidate.getInput().getItems().length > 0)
                    .findFirst().orElseThrow();
            array.setSelectedLumen(recipe.getProducedLumen());
            array.getTileData().getFluidTank().fill(new FluidStack(FluidsAS.LIQUID_STARLIGHT.getSource().get(), 1000),
                    FluidAction.EXECUTE);
            var catalyst = recipe.getInput().getItems()[0].copyWithCount(1);
            var matched = (Optional<?>) (nativeLookup.getParameterCount() == 1
                    ? nativeLookup.invoke(array.getTileData(), catalyst)
                    : nativeLookup.invoke(array.getTileData(), helper.getLevel(), catalyst));
            var nativeRecipe = (LumenGenerationRecipe) ((RecipeHolder<?>) matched.orElseThrow()).value();
            helper.assertTrue(nativeRecipe.getProducedLumen().equals(recipe.getProducedLumen())
                            && alchemy != nativeRecipe.getLumenCombinationInputs().isEmpty(),
                    "Native AS lookup must dispatch to the correct array recipe type (alchemy=" + alchemy + ")");
            var inventory = array.getTileData().getInventory();
            helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.BARRIER), true).getCount() == 1,
                    "Array must reject a non-catalyst");
            helper.assertTrue(inventory.insertItem(0, catalyst, false).isEmpty(),
                    "Array must resolve and accept its catalyst using the current world's recipes (alchemy=" + alchemy + ")");
        }
        helper.succeed();
    }
}
