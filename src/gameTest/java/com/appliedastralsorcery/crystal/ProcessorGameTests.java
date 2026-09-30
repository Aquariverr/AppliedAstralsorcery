package com.appliedastralsorcery.crystal;

import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.blockentity.misc.InscriberRecipes;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.recipes.handlers.InscriberProcessType;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.JsonOps;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.item.LumenCrystalItem;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.recipe.focal.place.FocalTransmutationCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.focal.place.FocalTransmutationRecipe;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ProcessorGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "wand_empty")
    public static void sizeRecipesAreDisjointCappedAndIgnoreOtherAttributes(GameTestHelper helper) {
        var press = ModContent.ASTRAL_PROCESSOR_PRESS.toStack();
        var sizes = java.util.stream.IntStream.concat(java.util.stream.IntStream.rangeClosed(0, 65),
                java.util.stream.IntStream.of(Integer.MAX_VALUE)).toArray();
        for (int size : sizes) {
            for (int purity : new int[]{0, 3}) {
                for (int cut : new int[]{0, 2}) {
                    var crystal = crystal(size, purity, cut);
                    long matches = 0;
                    for (var holder : InscriberRecipes.getRecipes(helper.getLevel())) {
                        var recipe = holder.value();
                        if (recipe.getTopOptional().test(press) && recipe.getMiddleInput().test(crystal)) matches++;
                    }
                    helper.assertTrue(matches == 1, "Exactly one recipe must match Size " + size);
                    var recipe = InscriberRecipes.findRecipe(helper.getLevel(), crystal, press, ItemStack.EMPTY, false);
                    var flipped = InscriberRecipes.findRecipe(helper.getLevel(), crystal, ItemStack.EMPTY, press, false);
                    int expected = Math.min(size, 63) + 1;
                    helper.assertTrue(recipe != null && recipe == flipped, "Press must work in either outer slot");
                    helper.assertTrue(recipe.getResultItem().is(ModContent.PRINTED_ASTRAL_PROCESSOR)
                                    && recipe.getResultItem().getCount() == expected,
                            "Wrong circuit count for Size " + size + "; expected " + expected);
                    helper.assertTrue(recipe.getProcessType() == InscriberProcessType.INSCRIBE, "Press must be reusable");
                }
            }
        }
        for (var noSize : new ItemStack[]{crystal(0, 3), ModContent.ASTRAL_FLUIX_CRYSTAL.toStack()}) {
            var attributes = noSize.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            helper.assertTrue(attributes != null && attributes.getAttribute(CrystalPropertiesAS.SIZE.get()).isEmpty(),
                    "Size zero must be represented by a present component with no Size attribute");
            var recipe = InscriberRecipes.findRecipe(helper.getLevel(), noSize, press, ItemStack.EMPTY, false);
            helper.assertTrue(recipe != null && recipe.getResultItem().getCount() == 1,
                    "A crystal without a Size attribute must still produce one board");
        }
        var missingComponent = crystal(1, 3);
        missingComponent.remove(DataComponentsAS.CRYSTAL_ATTRIBUTES);
        var celestialCrystal = ItemsAS.CELESTIAL_CRYSTAL.toStack();
        celestialCrystal.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, crystal(63, 3).get(DataComponentsAS.CRYSTAL_ATTRIBUTES));
        for (var invalid : new ItemStack[]{missingComponent, celestialCrystal, AEItems.FLUIX_CRYSTAL.stack(), ItemStack.EMPTY}) {
            helper.assertTrue(InscriberRecipes.findRecipe(helper.getLevel(), invalid, press, ItemStack.EMPTY, false) == null,
                    "Missing crystal component or wrong crystal material must not match");
        }
        var nativeRecipe = InscriberRecipes.findRecipe(helper.getLevel(), new ItemStack(Items.DIAMOND),
                AEItems.ENGINEERING_PROCESSOR_PRESS.stack(), ItemStack.EMPTY, false);
        helper.assertTrue(nativeRecipe != null && nativeRecipe.getResultItem().is(AEItems.ENGINEERING_PROCESSOR_PRINT.asItem())
                        && nativeRecipe.getResultItem().getCount() == 1
                        && nativeRecipe.getProcessType() == InscriberProcessType.INSCRIBE,
                "Native AE2 recipes must remain intact");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void sizeIngredientsSurviveDataAndNetworkSerialization(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (int size = 0; size <= 63; size++) {
            var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                    "appliedas", "inscriber/astral_processor_print_size_" + size)).orElseThrow();
            var ingredient = ((appeng.recipes.handlers.InscriberRecipe) holder.value()).getMiddleInput();
            var json = Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
            var decoded = Ingredient.CODEC.parse(ops, json).getOrThrow();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
                var network = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                for (var restored : new Ingredient[]{decoded, network}) {
                    helper.assertTrue(!restored.isSimple() && restored.test(crystal(size, 3)),
                            "Reloaded and synced ingredients must retain attribute matching for Size " + size);
                    helper.assertTrue(!restored.test(crystal(size == 63 ? 62 : size + 1, 3)),
                            "Serialization must not broaden the match to a neighboring size");
                    for (int cappedSize : new int[]{64, 65, Integer.MAX_VALUE}) {
                        helper.assertTrue(restored.test(crystal(cappedSize, 3)) == (size == 63),
                                "Only the Size 63 ingredient may match Size " + cappedSize);
                    }
                    var display = restored.getItems();
                    helper.assertTrue(display.length == 1 && restored.test(display[0]),
                            "Recipe display must show one matching crystal for Size " + size);
                    var missingComponent = crystal(size, 3);
                    missingComponent.remove(DataComponentsAS.CRYSTAL_ATTRIBUTES);
                    helper.assertTrue(!restored.test(missingComponent),
                            "Serialization must still reject crystals without their attribute component");
                    if (size == 0) {
                        helper.assertTrue(restored.test(ModContent.ASTRAL_FLUIX_CRYSTAL.toStack()),
                                "Size zero must accept an empty but present crystal component after serialization");
                    }
                }
            } finally {
                buffer.release();
            }
        }
        for (int invalidSize : new int[]{-1, 64, Integer.MAX_VALUE}) {
            var invalidJson = new com.google.gson.JsonObject();
            invalidJson.addProperty("type", "appliedas:crystal_size");
            invalidJson.addProperty("size", invalidSize);
            helper.assertTrue(Ingredient.CODEC.parse(ops, invalidJson).error().isPresent(),
                    "Ingredient JSON must reject size buckets outside 0..63: " + invalidSize);
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void bothCubesTransmuteAndAlwaysDropBothPresses(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(POS);
        var recipe = (FocalTransmutationRecipe) level.getRecipeManager().byKey(ResourceLocation.parse(
                "appliedas:focal_transmutation/starlight_mysterious_cube")).orElseThrow().value();
        for (var source : new Block[]{AEBlocks.MYSTERIOUS_CUBE.block(), AEBlocks.NOT_SO_MYSTERIOUS_CUBE.block()}) {
            level.setBlockAndUpdate(pos, source.defaultBlockState());
            var input = new FocalTransmutationCraftingInput(null, level, pos, false);
            helper.assertTrue(recipe.matches(input, level), "Both cube variants must accept ordinary starlight");
            recipe.createOutput(input, level.registryAccess());
            helper.assertBlockPresent(ModContent.STARLIGHT_MYSTERIOUS_CUBE.get(), POS);
            helper.assertTrue(level.getBlockEntity(pos) == null, "Transmutation must remove the native cube's block entity");
            for (var enchantment : java.util.List.of(Enchantments.SILK_TOUCH, Enchantments.FORTUNE)) {
                var tool = new ItemStack(Items.DIAMOND_PICKAXE);
                tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), 3);
                var drops = Block.getDrops(level.getBlockState(pos), level, pos, null, null, tool);
                helper.assertTrue(drops.size() == 2 && drops.stream().allMatch(stack -> stack.getCount() == 1)
                                && drops.stream().anyMatch(stack -> stack.is(ModContent.ASTRAL_PROCESSOR_PRESS))
                                && drops.stream().anyMatch(stack -> stack.is(ModContent.LUMEN_PROCESSOR_PRESS)),
                        "Harvest must yield one of each press, even with Silk Touch or Fortune");
            }
        }
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!recipe.matches(new FocalTransmutationCraftingInput(null, level, pos, false), level),
                "Unrelated blocks must not transmute");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 650)
    public static void inscriberWaitsForFullBatchThenConsumesCrystalAndKeepsPress(GameTestHelper helper) {
        var machine = machine(helper);
        var inv = machine.getInternalInventory();
        helper.assertTrue(inv.insertItem(1, ModContent.ASTRAL_PROCESSOR_PRESS.toStack(), false).isEmpty(), "Bottom slot must accept press");
        helper.assertTrue(inv.insertItem(2, crystal(63, 3), false).isEmpty(), "Middle slot must accept crystal");
        inv.setItemDirect(3, ModContent.PRINTED_ASTRAL_PROCESSOR.toStack());
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(inv.getStackInSlot(2).getCount() == 1 && inv.getStackInSlot(3).getCount() == 1,
                    "A batch of 64 must wait when only 63 spaces are free");
            inv.extractItem(3, 64, false);
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(inv.getStackInSlot(3).getCount() == 64, "Size 63 must produce a full stack");
                helper.assertTrue(inv.getStackInSlot(2).isEmpty(), "One crystal must be consumed");
                helper.assertTrue(inv.getStackInSlot(1).is(ModContent.ASTRAL_PROCESSOR_PRESS)
                        && inv.getStackInSlot(1).getCount() == 1, "The press must remain");
            }).thenExecute(() -> {
                inv.extractItem(3, 64, false);
                inv.insertItem(2, crystal(2, 1), false);
            }).thenWaitUntil(() -> {
                helper.assertTrue(inv.getStackInSlot(3).is(ModContent.PRINTED_ASTRAL_PROCESSOR)
                                && inv.getStackInSlot(3).getCount() == 3 && inv.getStackInSlot(2).isEmpty(),
                        "Size 2 must produce three boards after replacing the cached Size 63 recipe");
                helper.assertTrue(inv.getStackInSlot(1).is(ModContent.ASTRAL_PROCESSOR_PRESS)
                        && inv.getStackInSlot(1).getCount() == 1, "The second batch must reuse the same press");
            }).thenExecute(() -> {
                inv.extractItem(3, 64, false);
                helper.assertTrue(inv.insertItem(2, crystal(0, 0, 0), false).isEmpty(),
                        "A Size 0 crystal with an empty attribute component must insert");
            }).thenWaitUntil(() -> {
                helper.assertTrue(inv.getStackInSlot(3).is(ModContent.PRINTED_ASTRAL_PROCESSOR)
                                && inv.getStackInSlot(3).getCount() == 1 && inv.getStackInSlot(2).isEmpty(),
                        "Size 0 must produce one board after replacing the cached Size 2 recipe");
                helper.assertTrue(inv.getStackInSlot(1).is(ModContent.ASTRAL_PROCESSOR_PRESS)
                        && inv.getStackInSlot(1).getCount() == 1, "All three batches must reuse the same press");
            }).thenSucceed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 300)
    public static void processorConsumesOneBoardRedstoneAndPrintedSilicon(GameTestHelper helper) {
        var machine = machine(helper);
        var inv = machine.getInternalInventory();
        inv.insertItem(0, AEItems.SILICON_PRINT.stack(), false);
        inv.insertItem(1, ModContent.PRINTED_ASTRAL_PROCESSOR.toStack(), false);
        helper.assertTrue(inv.insertItem(2, new ItemStack(Items.REDSTONE), false).isEmpty(), "Valid assembly must accept redstone");
        helper.succeedWhen(() -> {
            helper.assertTrue(inv.getStackInSlot(3).is(ModContent.ASTRAL_PROCESSOR) && inv.getStackInSlot(3).getCount() == 1,
                    "Pressing must produce exactly one processor");
            helper.assertTrue(inv.getStackInSlot(0).isEmpty() && inv.getStackInSlot(1).isEmpty() && inv.getStackInSlot(2).isEmpty(),
                    "Processor assembly must consume all three inputs");
        });
    }

    private static InscriberBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS, AEBlocks.INSCRIBER.block());
        return (InscriberBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = "wand_empty")
    public static void everyLumenCrystalMatchesAndDisplaysAfterNetworkSync(GameTestHelper helper) {
        var press = ModContent.LUMEN_PROCESSOR_PRESS.toStack();
        var recipe = (appeng.recipes.handlers.InscriberRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("appliedas:inscriber/lumen_processor_print")).orElseThrow().value();
        var ingredient = recipe.getMiddleInput();
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var decoded = Ingredient.CODEC.parse(ops, Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow()).getOrThrow();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
            var synced = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            var types = RegistriesAS.REGISTRY_LUMEN.holders().filter(lumen -> !lumen.is(LumenAS.NONE)).toList();
            helper.assertTrue(types.size() >= 14, "All native lumen variants must be available");
            helper.assertTrue(synced.getItems().length == types.size(), "Recipe viewers must list every colored crystal");
            for (var lumen : types) {
                var crystal = LumenCrystalItem.getCrystal(lumen);
                helper.assertTrue(decoded.test(crystal) && synced.test(crystal), "Every lumen must survive recipe serialization");
                var found = InscriberRecipes.findRecipe(helper.getLevel(), crystal, press, ItemStack.EMPTY, false);
                var flipped = InscriberRecipes.findRecipe(helper.getLevel(), crystal, ItemStack.EMPTY, press, false);
                helper.assertTrue(found == recipe && flipped == recipe, "Every lumen must work with the press in either slot");
                helper.assertTrue(found.getResultItem().is(ModContent.PRINTED_LUMEN_PROCESSOR)
                                && found.getResultItem().getCount() == 1 && found.getProcessType() == InscriberProcessType.INSCRIBE,
                        "Each crystal must yield one common board and retain the press");
                helper.assertTrue(InscriberRecipes.findRecipe(helper.getLevel(), crystal,
                        ModContent.ASTRAL_PROCESSOR_PRESS.toStack(), ItemStack.EMPTY, false) == null,
                        "Aberrant press must not substitute for lumen press");
            }
            var missingComponent = ItemsAS.LUMEN_CRYSTAL.toStack();
            missingComponent.remove(DataComponentsAS.LUMEN);
            for (var invalid : new ItemStack[]{ItemsAS.LUMEN_CRYSTAL.toStack(), missingComponent,
                    ModContent.ASTRAL_FLUIX_CRYSTAL.toStack(), ItemsAS.CELESTIAL_CRYSTAL.toStack()}) {
                helper.assertTrue(!synced.test(invalid), "Blank and unrelated crystals must not match");
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 650)
    public static void lumenCrystalsPrintAndAssembleInRealInscriber(GameTestHelper helper) {
        var machine = machine(helper);
        var inv = machine.getInternalInventory();
        var crystals = LumenCrystalItem.getCrystal(LumenAS.PRISMATIC).copyWithCount(3);
        helper.assertTrue(inv.insertItem(0, ModContent.LUMEN_PROCESSOR_PRESS.toStack(), false).isEmpty(), "Press must insert");
        helper.assertTrue(inv.insertItem(2, crystals, false).isEmpty(), "Stacked crystals must insert");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(inv.getStackInSlot(3).is(ModContent.PRINTED_LUMEN_PROCESSOR)
                    && inv.getStackInSlot(3).getCount() == 3 && inv.getStackInSlot(2).isEmpty(),
                    "Three crystals must produce exactly three boards");
            helper.assertTrue(inv.getStackInSlot(0).is(ModContent.LUMEN_PROCESSOR_PRESS)
                    && inv.getStackInSlot(0).getCount() == 1, "All operations must retain the press");
        }).thenExecute(() -> {
            inv.extractItem(3, 64, false);
            inv.extractItem(0, 1, false);
            inv.insertItem(0, AEItems.SILICON_PRINT.stack(), false);
            inv.insertItem(1, ModContent.PRINTED_LUMEN_PROCESSOR.toStack(), false);
            inv.insertItem(2, new ItemStack(Items.REDSTONE), false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(inv.getStackInSlot(3).is(ModContent.LUMEN_PROCESSOR) && inv.getStackInSlot(3).getCount() == 1,
                    "Lumen board must assemble into one lumen processor");
            helper.assertTrue(inv.getStackInSlot(0).isEmpty() && inv.getStackInSlot(1).isEmpty() && inv.getStackInSlot(2).isEmpty(),
                    "Assembly must consume one board, redstone and printed silicon");
        }).thenSucceed();
    }

    private static ItemStack crystal(int size, int purity) {
        return crystal(size, purity, 2);
    }

    private static ItemStack crystal(int size, int purity, int cut) {
        var stack = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, CrystalAttributesComponent.empty(0, 14)
                .setAttributeTier(CrystalPropertiesAS.SIZE, size)
                .setAttributeTier(CrystalPropertiesAS.PURITY, purity)
                .setAttributeTier(CrystalPropertiesAS.CUT, cut));
        return stack;
    }
}
