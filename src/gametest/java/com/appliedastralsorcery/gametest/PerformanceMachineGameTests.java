package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceMachineGameTests {
    @GameTest(template = "attunement_test")
    public static void transmutationKeepsRecipePriorityAndObservesReloads(GameTestHelper helper)
            throws ReflectiveOperationException {
        var server = helper.getLevel();
        var manager = server.getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        var sample = manager.getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).getFirst().value();
        var first = new CountingRecipe(sample, Ingredient.of(Items.DIRT));
        var second = new CountingRecipe(sample, Ingredient.of(Items.DIRT));
        var firstId = ResourceLocation.fromNamespaceAndPath("appliedas", "performance_a");
        var secondId = ResourceLocation.fromNamespaceAndPath("appliedas", "performance_b");
        var pos = new BlockPos(10, 2, 10);
        helper.setBlock(pos, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        var chamber = (StarlightTransmutationBlockEntity) server.getBlockEntity(helper.absolutePos(pos));
        var process = StarlightTransmutationBlockEntity.class.getDeclaredMethod("process", ServerLevel.class);
        process.setAccessible(true);
        var selected = StarlightTransmutationBlockEntity.class.getDeclaredField("recipeId");
        selected.setAccessible(true);
        try {
            // Deliberately reverse insertion order: an idle chamber must still choose the lowest ID.
            manager.replaceRecipes(List.of(new RecipeHolder<>(secondId, second), new RecipeHolder<>(firstId, first)));
            chamber.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT));
            chamber.receiveStarlight(server, new StarlightTransmissionPacket(
                    RegistriesAS.REGISTRY_CONSTELLATIONS.byId(0), 1));
            process.invoke(chamber, server);
            helper.assertTrue(firstId.equals(selected.get(chamber)), "New jobs must retain lexical recipe priority");

            selected.set(chamber, secondId);
            first.checks = second.checks = 0;
            process.invoke(chamber, server);
            helper.assertTrue(secondId.equals(selected.get(chamber)) && first.checks == 0 && second.checks == 1,
                    "An existing matching job must check only its current recipe");

            var replaced = new CountingRecipe(sample, Ingredient.of(Items.STONE));
            manager.replaceRecipes(List.of(new RecipeHolder<>(secondId, replaced), new RecipeHolder<>(firstId, first)));
            process.invoke(chamber, server);
            helper.assertTrue(firstId.equals(selected.get(chamber)) && replaced.checks == 1,
                    "Replacing a recipe under the same ID must immediately change matching");

            manager.replaceRecipes(List.of(new RecipeHolder<>(secondId, replaced)));
            process.invoke(chamber, server);
            helper.assertTrue(selected.get(chamber) == null
                            && chamber.getStatus() == StarlightTransmutationBlockEntity.Status.MISSING_INPUTS,
                    "Removing the current recipe must discard the stale job");
            replaced.checks = 0;
            chamber.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            process.invoke(chamber, server);
            helper.assertTrue(replaced.checks == 0
                            && chamber.getStatus() == StarlightTransmutationBlockEntity.Status.IDLE,
                    "An empty chamber must not inspect any recipe inputs");
        } finally {
            manager.replaceRecipes(original);
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void transmutationPlansOverlappingIngredientsAndIndependentOutputs(GameTestHelper helper)
            throws ReflectiveOperationException {
        var plan = Class.forName("com.appliedastralsorcery.transmutation.TransmutationPlan");
        var match = plan.getDeclaredMethod("match", List.class, ItemStackHandler.class);
        var output = plan.getDeclaredMethod("output", List.class, ItemStackHandler.class);
        match.setAccessible(true);
        output.setAccessible(true);
        var inventory = new ItemStackHandler(18);
        inventory.setStackInSlot(0, new ItemStack(Items.DIRT));
        inventory.setStackInSlot(1, new ItemStack(Items.STONE));
        var ingredients = List.of(Ingredient.of(Items.DIRT, Items.STONE), Ingredient.of(Items.DIRT));
        var consumed = (int[]) match.invoke(null, ingredients, inventory);
        helper.assertTrue(consumed != null && consumed[0] == 1 && consumed[1] == 1,
                "Overlapping ingredients must reserve the narrow match and use the alternative for the other input");
        inventory.setStackInSlot(1, ItemStack.EMPTY);
        helper.assertTrue(match.invoke(null, ingredients, inventory) == null,
                "A single item cannot pay for two matching ingredients");
        inventory.setStackInSlot(10, new ItemStack(Items.DIAMOND, 60));
        var product = new ItemStack(Items.DIAMOND, 8);
        var result = (ItemStack[]) output.invoke(null, List.of(product), inventory);
        helper.assertTrue(result != null && result[1].getCount() == 64 && result[0].getCount() == 4,
                "Outputs must fill an existing stack before using an earlier empty slot");
        result[1].shrink(1);
        result[0].shrink(1);
        helper.assertTrue(inventory.getStackInSlot(10).getCount() == 60 && product.getCount() == 8,
                "Planned outputs must not share mutable stacks with inventory or recipe products");
        helper.succeed();
    }

    private static final class CountingRecipe extends FocalCombineRecipe {
        int checks;

        CountingRecipe(FocalCombineRecipe sample, Ingredient input) {
            super(20, sample.getColor(), List.of(input), List.of(new ItemStack(Items.DIAMOND)), Optional.empty());
        }

        @Override public List<Ingredient> getInputs() {
            checks++;
            return super.getInputs();
        }
    }
}
