package com.appliedastralsorcery.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.altar.AltarAutomationBlockEntity;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipeGrid;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AltarCompatibilityGameTests {
    private static final BlockPos ALTAR_POS = new BlockPos(10, 2, 10);

    @GameTest(template = "attunement_test")
    public static void allNativeAltarTiersPlaceAndTick(GameTestHelper helper) {
        var blocks = new Block[]{BlocksAS.ALTAR_ILLUMINATION.get(), BlocksAS.ALTAR_RESONANCE.get(),
                BlocksAS.ALTAR_LUMINANCE.get(), BlocksAS.ALTAR_RADIANCE.get()};
        var types = TileAltar.AltarType.values();
        for (int i = 0; i < blocks.length; i++) {
            helper.setBlock(new BlockPos(4 + i * 4, 2, 10), blocks[i]);
        }
        helper.runAtTickTime(5, () -> {
            for (int i = 0; i < blocks.length; i++) {
                var pos = helper.absolutePos(new BlockPos(4 + i * 4, 2, 10));
                helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof TileAltar,
                        "Placing native altar tier " + types[i] + " must create its block entity");
                var altar = (TileAltar) helper.getLevel().getBlockEntity(pos);
                helper.assertTrue(altar.getTileData().getAltarType() == types[i]
                                && altar.getTileData().getTicksExisted() > 0,
                        "Native altar tier " + types[i] + " must retain its type and tick after placement");
                helper.assertTrue(altar.getTileData().getActiveRecipe().isEmpty(),
                        "An idle native altar must not start an automation job");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void manualIlluminatedCraftingDropsOutput(GameTestHelper helper) {
        completeIlluminatedCraft(helper, false);
    }

    @GameTest(template = "attunement_test")
    public static void automatedIlluminatedCraftingReturnsOutputExactlyOnce(GameTestHelper helper) {
        completeIlluminatedCraft(helper, true);
    }

    private static void completeIlluminatedCraft(GameTestHelper helper, boolean automated) {
        helper.setBlock(ALTAR_POS, BlocksAS.ALTAR_ILLUMINATION.get());
        var altar = (TileAltar) helper.getLevel().getBlockEntity(helper.absolutePos(ALTAR_POS));
        // A cached native recipe keeps this test independent of datapack recipes, sky and progression.
        var grid = AltarRecipeGrid.create(Map.of('D', IngredientBridge.of(Ingredient.of(Items.DIAMOND))),
                List.of("D  ", "   ", "   "), List.of("     ", "     ", "     ", "     ", "     "));
        var recipe = new AltarRecipe(TileAltar.AltarType.ILLUMINATION, grid,
                List.of(new ItemStack(Items.EMERALD, 3)), Optional.empty(), 0, 20, false, false,
                Set.of(), automated ? List.of(LumenAS.VICIO.stack(40)) : List.of(),
                List.of(), List.of(), Set.of(), List.of());
        var holder = new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("appliedas", "test/altar_output"), recipe);
        var job = UUID.randomUUID();
        AltarAutomationBlockEntity automation = null;
        if (automated) {
            var interfacePos = ALTAR_POS.below();
            helper.setBlock(interfacePos, ModContent.ALTAR_AUTOMATION.get());
            automation = (AltarAutomationBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(interfacePos));
            // Restore an accepted job through the same state used when reloading a world.
            var saved = automation.saveWithoutMetadata(helper.getLevel().registryAccess());
            saved.putUUID("job", job);
            saved.putLong("altar", altar.getBlockPos().asLong());
            saved.putString("recipe", holder.id().toString());
            saved.putInt("gridMask", 1);
            automation.loadWithComponents(saved, helper.getLevel().registryAccess());
            var link = new CompoundTag();
            link.putLong("interface", automation.getBlockPos().asLong());
            link.putUUID("job", job);
            altar.getPersistentData().put("appliedas:automation", link);
            automation.getLumenHandler().fill(LumenAS.VICIO.stack(45), ILumenHandler.Action.EXECUTE);
        }
        altar.getTileData().getAltarInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        altar.startCrafting(holder, job);
        // The native intake runs every 40 ticks, before the 20-tick crafting progress can begin.
        for (int tick = 0; tick < recipe.getDuration() + 45; tick++) altar.serverTick(helper.getLevel());
        helper.assertTrue(altar.getTileData().getAltarInventory().getStackInSlot(0).isEmpty(),
                "Native illuminated crafting must consume its input exactly once");
        helper.assertTrue(altar.getTileData().getActiveRecipe().isEmpty(),
                "The native crafting cycle must finish after producing the output");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                entity -> entity.getItem().is(Items.EMERALD)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        if (automated) {
            helper.assertTrue(automation.getLumenHandler().drain(LumenAS.VICIO.stack(1000),
                            ILumenHandler.Action.SIMULATE).getAmount() == 5,
                    "Native crafting must draw exactly its 40 Lm cost from the automation buffer");
            var output = automation.getOutput();
            helper.assertTrue(output.getSlots() == 1 && output.getStackInSlot(0).is(Items.EMERALD)
                            && output.getStackInSlot(0).getCount() == 3 && dropped == 0,
                    "Automated native completion must buffer exactly three emeralds without world drops");
            var restored = new AltarAutomationBlockEntity(automation.getBlockPos(), automation.getBlockState());
            restored.loadWithComponents(automation.saveWithoutMetadata(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(restored.getOutput().extractItem(0, 64, false).getCount() == 3
                            && restored.getOutput().extractItem(0, 64, false).isEmpty(),
                    "Buffered altar output must survive reload and be extractable only once");
        } else {
            helper.assertTrue(dropped == 3, "Manual native completion must still drop exactly three emeralds");
        }
        helper.succeed();
    }
}
