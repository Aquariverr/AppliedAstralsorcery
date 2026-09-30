package com.appliedastralsorcery.crystal;

import java.util.List;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.entity.item.ItemEntityCrystal;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.EntitiesAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.constants.TagsAS;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class CrystalItemGameTests {
    private static final BlockPos ITEM_POS = new BlockPos(2, 2, 2);

    @GameTest(template = "wand_empty")
    public static void droppedGlowRestoresAfterEntityReload(GameTestHelper helper) {
        ItemStack stack = crystalWithProperties(2, 1, 1);
        ItemEntityCrystal original = spawnCrystal(helper, stack);
        var saved = original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        original.discard();
        saved.remove("UUID");

        var restored = new ItemEntityCrystal(EntitiesAS.ITEM_CRYSTAL.get(), helper.getLevel());
        restored.load(saved);
        helper.getLevel().addFreshEntity(restored);
        helper.assertTrue(restored.getColor().map(color -> color.getColor() == AstralFluixCrystalItem.COLOR.getColor())
                        .orElse(false),
                "Loading a dropped Aberrant Crystal must restore its matching glow");
        helper.assertTrue(attributes(stack).equals(attributes(restored.getItem())),
                "Restoring the visual color must preserve crystal attributes");

        ItemStack celestial = ItemsAS.CELESTIAL_CRYSTAL.toStack();
        ItemEntityCrystal nativeCrystal = spawnCrystal(helper, celestial);
        int nativeColor = ItemsAS.CELESTIAL_CRYSTAL.get().getItemEntityColor(celestial).getColor();
        helper.assertTrue(nativeCrystal.getColor().map(color -> color.getColor() == nativeColor).orElse(false),
                "Native celestial crystals must keep their own glow");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void nativeAttributesGenerateOnceAndSurviveSaveLoad(GameTestHelper helper) {
        ItemStack stack = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
        var item = ModContent.ASTRAL_FLUIX_CRYSTAL.get();
        var carrier = new ItemEntity(helper.getLevel(), 0, 0, 0, stack);
        item.inventoryTick(stack, helper.getLevel(), carrier, 0, false);
        var attributes = attributes(stack);
        helper.assertTrue(attributes.getTotalTierCount() == 6, "New crystals must receive six native attribute tiers");
        helper.assertTrue(attributes.getProperties().maxTierCount() == 14, "Crystals must retain the celestial growth limit");
        helper.assertTrue(attributes.getAttributes().stream().allMatch(attr ->
                attr.getProperty() == CrystalPropertiesAS.SIZE.get()
                        || attr.getProperty() == CrystalPropertiesAS.PURITY.get()
                        || attr.getProperty() == CrystalPropertiesAS.CUT.get()),
                "Generated properties must be the native size, purity and cutting properties");
        item.inventoryTick(stack, helper.getLevel(), carrier, 0, false);
        helper.assertTrue(attributes.equals(attributes(stack)), "Inventory ticks must not reroll existing properties");
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (net.minecraft.nbt.CompoundTag) stack.save(helper.getLevel().registryAccess()));
        helper.assertTrue(restored.is(item) && attributes.equals(attributes(restored)),
                "Crystal identity and properties must survive serialization");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void liquidGrowthPreservesOtherPropertiesAndMaterial(GameTestHelper helper) {
        ItemEntityCrystal entity = spawnCrystal(helper, crystalWithProperties(1, 1, 1));
        var input = LiquidStarlightRecipeInput.of(entity);
        var recipe = recipe(helper, "appliedas:liquid_starlight/grow_astral_fluix_crystal");
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "The dedicated growth recipe must accept one crystal");
        recipe.createOutput(input, helper.getLevel().registryAccess());
        helper.assertTrue(entity.getItem().isEmpty(), "Growth must consume the original crystal exactly once");
        // NeoForge queues custom item-entity replacement on the server work queue.
        helper.runAfterDelay(2, () -> {
            var grown = attributes(onlyCrystal(helper));
            helper.assertTrue(grown.getAttributeTier(CrystalPropertiesAS.SIZE) == 2,
                    "A crystal below the initial tier budget must grow by one size tier");
            helper.assertTrue(grown.getAttributeTier(CrystalPropertiesAS.PURITY) == 1
                            && grown.getAttributeTier(CrystalPropertiesAS.CUT) == 1,
                    "Size growth must preserve purity and cutting");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void nativeMergeCombinesPropertiesWithoutChangingMaterial(GameTestHelper helper) {
        ItemEntityCrystal first = spawnCrystal(helper, crystalWithProperties(2, 0, 0));
        ItemEntityCrystal second = spawnCrystal(helper, crystalWithProperties(0, 1, 0));
        var input = LiquidStarlightRecipeInput.of(first);
        var recipe = recipe(helper, "appliedas:liquid_starlight/merge_astral_fluix_crystals");
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Two matching crystals must be mergeable");
        recipe.createOutput(input, helper.getLevel().registryAccess());
        helper.assertTrue(first.getItem().isEmpty() && second.getItem().isEmpty(), "Merging must consume both input crystals");
        helper.runAfterDelay(2, () -> {
            var merged = attributes(onlyCrystal(helper));
            helper.assertTrue(merged.getAttributeTier(CrystalPropertiesAS.SIZE) == 2
                            && merged.getAttributeTier(CrystalPropertiesAS.PURITY) == 1,
                    "The guaranteed first merged tier must preserve the receiver's existing properties");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void nativeChiselSplitRetainsBothCrystalsAndTheirProperties(GameTestHelper helper) {
        ItemStack stack = crystalWithProperties(2, 2, 2);
        var originalAttributes = attributes(stack);
        BlockPos pos = helper.absolutePos(ITEM_POS);
        var entity = new SplitAccess(helper.getLevel(), stack);
        entity.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        helper.assertTrue(entity.split(), "A crystal with six attribute tiers must split");
        helper.runAfterDelay(2, () -> {
            var split = onlyCrystal(helper);
            var remainder = attributes(entity.getItem());
            helper.assertTrue(entity.getItem().is(ModContent.ASTRAL_FLUIX_CRYSTAL), "The original half must retain its material");
            helper.assertTrue(remainder.getTotalTierCount() == 3, "The original half must lose exactly half its attribute tiers");
            helper.assertTrue(attributes(split).getTotalTierCount() >= 1 && attributes(split).getTotalTierCount() <= 3,
                    "The split half must follow Astral Sorcery's native attribute-loss rules");
            helper.assertTrue(remainder.getProperties().equals(originalAttributes.getProperties())
                            && attributes(split).getProperties().equals(originalAttributes.getProperties()),
                    "Both halves must preserve the new crystal's property budget");
            for (var attribute : originalAttributes.getAttributes()) {
                helper.assertTrue(remainder.getAttributeTier(attribute) + attributes(split).getAttributeTier(attribute)
                                <= attribute.getTier(), "Splitting must not duplicate attribute tiers");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void originalCrystalRecipesCannotConvertTheNewMaterial(GameTestHelper helper) {
        ItemStack stack = crystalWithProperties(1, 1, 1);
        helper.assertTrue(!stack.is(TagsAS.Items.CRYSTAL), "The new material must stay out of conversion-prone native tags");
        ItemEntityCrystal crystal = spawnCrystal(helper, stack);
        BlockPos pos = helper.absolutePos(ITEM_POS);
        var dust = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, ItemsAS.STARDUST.toStack());
        helper.getLevel().addFreshEntity(dust);
        var input = LiquidStarlightRecipeInput.of(dust);
        helper.assertTrue(!recipe(helper, "astralsorcery:liquid_starlight/form_crystal_cluster").matches(input, helper.getLevel()),
                "Stardust must not turn the new crystal into a celestial crystal cluster");
        dust.discard();
        ItemStack nativeCrystal = ItemsAS.CELESTIAL_CRYSTAL.toStack();
        var other = new ItemEntityCrystal(EntitiesAS.ITEM_CRYSTAL.get(), helper.getLevel(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, nativeCrystal);
        helper.getLevel().addFreshEntity(other);
        helper.assertTrue(!recipe(helper, "appliedas:liquid_starlight/merge_astral_fluix_crystals")
                        .matches(LiquidStarlightRecipeInput.of(crystal), helper.getLevel()),
                "Merging different crystal materials must not silently consume the new material");
        helper.succeed();
    }

    private static ItemStack crystalWithProperties(int size, int purity, int cut) {
        ItemStack stack = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, attributes(stack)
                .setAttributeTier(CrystalPropertiesAS.SIZE, size)
                .setAttributeTier(CrystalPropertiesAS.PURITY, purity)
                .setAttributeTier(CrystalPropertiesAS.CUT, cut));
        return stack;
    }

    private static CrystalAttributesComponent attributes(ItemStack stack) {
        return stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
    }

    private static ItemEntityCrystal spawnCrystal(GameTestHelper helper, ItemStack stack) {
        BlockPos pos = helper.absolutePos(ITEM_POS);
        var original = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        var custom = stack.getItem().createEntity(helper.getLevel(), original, stack);
        helper.assertTrue(custom instanceof ItemEntityCrystal, "The material must use the native chisel-attackable crystal entity");
        helper.getLevel().addFreshEntity(custom);
        return (ItemEntityCrystal) custom;
    }

    private static ItemStack onlyCrystal(GameTestHelper helper) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(ITEM_POS)).inflate(1), entity -> !entity.getItem().isEmpty());
        helper.assertTrue(drops.size() == 1, "The operation must produce exactly one item entity, found "
                + drops.size() + ": " + drops.stream().map(entity -> entity.getType() + " " + entity.getItem()
                + " at " + entity.position()).toList());
        helper.assertTrue(drops.getFirst() instanceof ItemEntityCrystal,
                "The output must complete the native crystal item-entity replacement");
        ItemStack stack = drops.getFirst().getItem();
        helper.assertTrue(stack.is(ModContent.ASTRAL_FLUIX_CRYSTAL) && stack.getCount() == 1,
                "The output must be exactly one Aberrant Crystal");
        return stack;
    }

    private static LiquidStarlightRecipe recipe(GameTestHelper helper, String id) {
        return (LiquidStarlightRecipe) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse(id)).orElseThrow().value();
    }

    private static final class SplitAccess extends ItemEntityCrystal {
        private SplitAccess(Level level, ItemStack stack) {
            super(EntitiesAS.ITEM_CRYSTAL.get(), level);
            setItem(stack);
        }

        private boolean split() {
            return splitCrystal(ModContent.ASTRAL_FLUIX_CRYSTAL.get(), attributes(getItem()), 0);
        }
    }
}
