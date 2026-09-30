package com.appliedastralsorcery.altar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.JsonOps;
import hellfirepvp.astralsorcery.common.artifact.ArtifactStability;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.item.ArtifactItem;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class RecipeProgressionGameTests {
    private static final int[] CRYSTAL_SLOTS = {0, 1, 3, 4, 5, 9, 15, 19, 20, 21, 23, 24};

    @GameTest(template = "wand_empty")
    public static void meDevicesLoadAsNativeResonanceUpgrades(GameTestHelper helper) {
        verifyUpgrade(helper, "lumen_array", "me_lumen_array", Map.of(
                3, ModContent.LUMEN_PROCESSOR.toStack(), 4, new ItemStack(BlocksAS.LUMEN_ARRAY.get()),
                5, ModContent.LUMEN_PROCESSOR.toStack(), 26, AEBlocks.INTERFACE.stack()));
        verifyUpgrade(helper, "lumen_filament", "me_lumen_filament", Map.of(
                1, ModContent.LUMEN_PROCESSOR.toStack(), 4, new ItemStack(BlocksAS.LUMEN_FILAMENT.get()),
                31, AEBlocks.FLUIX_BLOCK.stack()));
        verifyUpgrade(helper, "chalice", "me_chalice", Map.of(
                1, AEBlocks.INTERFACE.stack(), 4, new ItemStack(BlocksAS.CHALICE.get()),
                26, AEBlocks.FLUIX_BLOCK.stack()));
        var component = altar(helper, "appliedas:lumen_storage_component_1k");
        helper.assertTrue(component.getGrid().getInputs().get(4).test(ModContent.LUMEN_PROCESSOR.toStack())
                        && !component.getGrid().getInputs().get(4).test(AEItems.CELL_COMPONENT_1K.stack()),
                "The altar component recipe must replace the AE2 component with a Lumen Processor");
        var alternative = (ShapedRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "appliedas:lumen_storage_component_1k_from_astral_processor")).orElseThrow().value();
        var nativeComponent = (ShapedRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                "ae2:network/cells/item_storage_components_cell_1k_part")).orElseThrow().value();
        var inputs = new ArrayList<>(nativeComponent.getIngredients().stream().map(i -> i.getItems()[0].copy()).toList());
        helper.assertTrue(!alternative.matches(CraftingInput.of(3, 3, inputs), helper.getLevel()),
                "The ordinary Logic Processor must not craft a lumen component");
        inputs.set(4, ModContent.ASTRAL_PROCESSOR.toStack());
        helper.assertTrue(alternative.matches(CraftingInput.of(3, 3, inputs), helper.getLevel())
                        && alternative.assemble(CraftingInput.of(3, 3, inputs), helper.getLevel().registryAccess())
                                .is(ModContent.LUMEN_COMPONENT),
                "AE2's native layout with an Aberrant Processor must craft a lumen component");
        helper.succeed();
    }

    private static void verifyUpgrade(GameTestHelper helper, String nativeId, String meId,
            Map<Integer, ItemStack> replacements) {
        var original = altar(helper, "astralsorcery:altar/" + nativeId);
        var recipe = altar(helper, "appliedas:" + meId);
        helper.assertTrue(recipe.getRequiredType() == TileAltar.AltarType.RESONANCE
                        && recipe.getOutputs().getFirst().getCount() == 1,
                "ME devices must load as resonance recipes producing one device");
        var originalInputs = new ArrayList<>(original.getGrid().getInputs());
        originalInputs.addAll(original.getGrid().getRelayInputs());
        var inputs = new ArrayList<>(recipe.getGrid().getInputs());
        inputs.addAll(recipe.getGrid().getRelayInputs());
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (int i = 0; i < inputs.size(); i++) {
            if (replacements.containsKey(i)) {
                helper.assertTrue(inputs.get(i).test(replacements.get(i)), "Wrong replacement in " + meId + " slot " + i);
            } else if (originalInputs.get(i).isEmpty()) {
                helper.assertTrue(inputs.get(i).isEmpty(), "Originally empty slot must remain empty: " + meId + " " + i);
            } else {
                helper.assertTrue(IngredientBridge.CODEC.codec().encodeStart(ops, inputs.get(i)).getOrThrow().equals(
                                IngredientBridge.CODEC.codec().encodeStart(ops, originalInputs.get(i)).getOrThrow()),
                        "Unchanged native ingredient must retain its exact match rules: " + meId + " " + i);
            }
        }
    }

    @GameTest(template = "wand_empty")
    public static void twelveDistinctConstellationsSurviveRecipeReloadAndNetworkSync(GameTestHelper helper) {
        var recipe = altar(helper, "appliedas:constellation_core");
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var reloaded = AltarRecipe.CODEC.codec().parse(ops,
                AltarRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow()).getOrThrow();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            AltarRecipe.STREAM_CODEC.encode(buffer, recipe);
            var synced = AltarRecipe.STREAM_CODEC.decode(buffer);
            for (var restored : List.of(recipe, reloaded, synced)) {
                var grid = restored.getGrid();
                var inputs = display(grid.getInputs());
                var relays = display(grid.getRelayInputs());
                var constellations = new HashSet<>();
                for (int slot : CRYSTAL_SLOTS) {
                    var ingredient = grid.getRelayInputs().get(slot);
                    var variants = ingredient.getItems().toList();
                    helper.assertTrue(variants.size() == 2 && !ingredient.isSimple(),
                            "Each constellation must display both crystal variants and retain component matching");
                    for (var stack : variants) {
                        var constellation = stack.get(DataComponentsAS.ATTUNED_CONSTELLATION);
                        helper.assertTrue(constellation != null && constellation.getConstellation().isPresent(),
                                "Recipe viewers must show the required attunement");
                        constellations.add(constellation.getConstellation().orElseThrow());
                        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, CrystalAttributesComponent.empty(6, 14)
                                .setAttributeTier(CrystalPropertiesAS.SIZE, 63)
                                .setAttributeTier(CrystalPropertiesAS.PURITY, 3));
                        helper.assertTrue(ingredient.test(stack), "Crystal quality must not affect acceptance");
                        relays.set(slot, stack);
                        helper.assertTrue(grid.matches(inputs, relays), "Either crystal material must work in the full layout");
                        var blank = stack.copy();
                        blank.remove(DataComponentsAS.ATTUNED_CONSTELLATION);
                        helper.assertTrue(!ingredient.test(blank), "Unattuned crystals must be rejected");
                    }
                }
                helper.assertTrue(constellations.size() == 12, "The twelve corner inputs must require distinct constellations");
                var duplicate = relays.get(CRYSTAL_SLOTS[0]).copy();
                relays.set(CRYSTAL_SLOTS[1], duplicate);
                helper.assertTrue(!grid.matches(inputs, relays), "A repeated constellation must not replace the missing one");
                var fake = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
                fake.set(DataComponentsAS.ATTUNED_CONSTELLATION, duplicate.get(DataComponentsAS.ATTUNED_CONSTELLATION));
                helper.assertTrue(!grid.getRelayInputs().get(CRYSTAL_SLOTS[0]).test(fake),
                        "Only native attuned rock/celestial crystals may occupy the outer ring");
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void advancedRecipesRequireResourcesAndAStableArtifact(GameTestHelper helper) {
        var core = altar(helper, "appliedas:constellation_core");
        var automation = altar(helper, "appliedas:altar_automation_interface");
        for (var recipe : List.of(core, automation)) {
            helper.assertTrue(recipe.getRequiredType() == TileAltar.AltarType.RADIANCE
                            && recipe.getRequiredFluid().stream().allMatch(f -> f.getFluid() == FluidsAS.LIQUID_STARLIGHT.getSource().get())
                            && recipe.getRequiredFluid().stream().mapToInt(f -> f.getAmount()).sum() == 2000
                            && recipe.getRequiredLumen().size() == 1
                            && recipe.getRequiredLumen().getFirst().getAmount() == 600,
                    "Advanced recipes must require an iridescent altar, 2 B liquid starlight and 600 lumen");
        }
        helper.assertTrue(core.getRequiredLumen().getFirst().getLumen() == LumenAS.AION.get()
                        && core.getRequiredAdditionalInputs().stream().mapToInt(i -> i.count()).sum() == 2
                        && core.getRequiredAdditionalInputs().stream().allMatch(i -> i.ingredient().test(ItemsAS.STARDUST.toStack())),
                "Core must require Aion and two additional Stardust");
        helper.assertTrue(automation.getRequiredLumen().getFirst().getLumen() == LumenAS.PRISMATIC.get()
                        && automation.getRequiredAdditionalInputs().size() == 1
                        && automation.getRequiredAdditionalInputs().getFirst().count() == 1
                        && automation.getGrid().getInputs().get(4).test(ModContent.CONSTELLATION_CORE.toStack()),
                "Automation must use the core, Prismatic and one stable artifact");
        var ingredient = automation.getRequiredAdditionalInputs().getFirst().ingredient();
        RegistriesAS.REGISTRY_ARTIFACT_TYPES.forEach(type -> {
            var artifact = ArtifactItem.createForDisplay(type);
            var data = artifact.get(DataComponentsAS.ARTIFACT);
            for (var stability : ArtifactStability.values()) {
                artifact.set(DataComponentsAS.ARTIFACT, data.changeStability(stability));
                helper.assertTrue(ingredient.test(artifact) == (stability == ArtifactStability.STABLE),
                        "Only stable artifacts may pay the automation recipe cost");
            }
        });
        helper.succeed();
    }

    private static ArrayList<ItemStack> display(List<IngredientBridge> ingredients) {
        return new ArrayList<>(ingredients.stream().map(i -> i.getItems().findFirst().orElse(ItemStack.EMPTY).copy()).toList());
    }

    private static AltarRecipe altar(GameTestHelper helper, String id) {
        return (AltarRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow().value();
    }
}
