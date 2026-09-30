package com.appliedastralsorcery.crystal;

import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.block.tile.CelestialCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipeInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class CrystalClusterGameTests {
    private static final BlockPos POOL = new BlockPos(2, 2, 2);

    @GameTest(template = "wand_empty", timeoutTicks = 640)
    public static void thrownIngredientsFormOneClusterAndConsumeOneOfEach(GameTestHelper helper) {
        var pos = pool(helper);
        var dust = drop(helper, pos, ItemsAS.STARDUST.toStack(3));
        var singularity = drop(helper, pos, AEItems.SINGULARITY.stack(3));
        var fluix = drop(helper, pos, AEItems.FLUIX_CRYSTAL.stack(3));
        // Stationary item collision callbacks run once every four ticks; allow the full
        // native 80-120 crafting callbacks without bypassing the actual liquid block.
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModContent.ASTRAL_FLUIX_CLUSTER.get(), POOL);
            helper.assertTrue(dust.getItem().getCount() == 2 && singularity.getItem().getCount() == 2
                    && fluix.getItem().getCount() == 2, "Formation must consume exactly one of each ingredient");
            helper.assertTrue(helper.getLevel().getFluidState(pos).isEmpty(), "The cluster must replace the liquid source");
            var cluster = (AstralFluixClusterBlockEntity) helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(cluster.getTileData().getCrystalAttributes().getTotalTierCount() == 6,
                    "Formation must generate the native initial properties");
        });
    }

    @GameTest(template = "wand_empty")
    public static void formationRequiresAllMaterialsSourceAndSolidSupport(GameTestHelper helper) {
        var pos = pool(helper);
        var dust = drop(helper, pos, ItemsAS.STARDUST.toStack());
        drop(helper, pos, AEItems.SINGULARITY.stack());
        var recipe = recipe(helper);
        helper.assertTrue(!recipe.matches(LiquidStarlightRecipeInput.of(dust), helper.getLevel()), "Missing fluix must not match");
        drop(helper, pos, AEItems.FLUIX_CRYSTAL.stack());
        helper.assertTrue(recipe.matches(LiquidStarlightRecipeInput.of(dust), helper.getLevel()), "All three ingredients must match");
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(!recipe.matches(LiquidStarlightRecipeInput.of(dust), helper.getLevel()), "Unsupported clusters must not consume ingredients");
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        helper.assertTrue(!recipe.matches(LiquidStarlightRecipeInput.of(dust), helper.getLevel()), "Water must not substitute for liquid starlight");
        helper.assertTrue(dust.getItem().getCount() == 1, "Failed matching must not consume ingredients");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void allGrowthStagesKeepTypePropertiesAndOnlyMatureCrystalDrops(GameTestHelper helper) {
        var pos = pool(helper);
        helper.getLevel().setBlockAndUpdate(pos, ModContent.ASTRAL_FLUIX_CLUSTER.get().defaultBlockState());
        var cluster = (AstralFluixClusterBlockEntity) helper.getLevel().getBlockEntity(pos);
        cluster.generateProperties();
        var attributes = cluster.getTileData().getCrystalAttributes();
        for (int stage = 0; stage <= 4; stage++) {
            var state = helper.getLevel().getBlockState(pos);
            helper.assertTrue(state.is(ModContent.ASTRAL_FLUIX_CLUSTER)
                    && state.getValue(CelestialCrystalClusterBlock.STAGE) == stage, "Growth must retain the custom block at each stage");
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == cluster
                    && attributes.equals(cluster.getTileData().getCrystalAttributes()), "Growth must retain the entity and its attributes");
            var drops = Block.getDrops(state, helper.getLevel(), pos, cluster, null, new ItemStack(Items.DIAMOND_PICKAXE));
            if (stage < 4) {
                helper.assertTrue(drops.isEmpty(), "Immature clusters must not yield a crystal");
                cluster.grow(helper.getLevel(), 1);
            } else {
                helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModContent.ASTRAL_FLUIX_CRYSTAL)
                        && drops.getFirst().getCount() == 1, "Mature clusters must drop exactly one crystal");
                helper.assertTrue(attributes.equals(drops.getFirst().get(DataComponentsAS.CRYSTAL_ATTRIBUTES)),
                        "Harvest must transfer the cluster properties to the crystal");
            }
        }
        cluster.grow(helper.getLevel(), 1);
        helper.assertTrue(cluster.getGrowth(helper.getLevel()) == 4, "Mature growth must stop at stage four");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void clusterPropertiesAndStageSurviveSaveLoad(GameTestHelper helper) {
        var pos = pool(helper);
        helper.getLevel().setBlockAndUpdate(pos, ModContent.ASTRAL_FLUIX_CLUSTER.get().defaultBlockState());
        var cluster = (AstralFluixClusterBlockEntity) helper.getLevel().getBlockEntity(pos);
        cluster.generateProperties();
        cluster.setGrowth(helper.getLevel(), 3);
        var restored = BlockEntity.loadStatic(pos, cluster.getBlockState(),
                cluster.saveWithFullMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored instanceof AstralFluixClusterBlockEntity, "Save/load must restore the registered custom tile type");
        var loaded = (AstralFluixClusterBlockEntity) restored;
        helper.assertTrue(loaded.getBlockState().getValue(CelestialCrystalClusterBlock.STAGE) == 3
                && loaded.getTileData().getCrystalAttributes().equals(cluster.getTileData().getCrystalAttributes()),
                "Save/load must preserve stage and every property");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void creativePlacementGeneratesUsablePropertiesAndCloningPreservesThem(GameTestHelper helper) {
        var pos = pool(helper);
        var block = ModContent.ASTRAL_FLUIX_CLUSTER.get();
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), null, ModContent.ASTRAL_FLUIX_CLUSTER_ITEM.toStack());
        var cluster = (AstralFluixClusterBlockEntity) helper.getLevel().getBlockEntity(pos);
        var attributes = cluster.getTileData().getCrystalAttributes();
        helper.assertTrue(attributes.getTotalTierCount() == 6 && attributes.getProperties().maxTierCount() == 14,
                "Creative placement must initialize a usable native crystal budget");
        var clone = ModContent.ASTRAL_FLUIX_CLUSTER_ITEM.toStack();
        clone.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, attributes);
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), null, clone);
        helper.assertTrue(attributes.equals(cluster.getTileData().getCrystalAttributes()), "Placing a clone must not reroll its attributes");
        helper.succeed();
    }

    private static BlockPos pool(GameTestHelper helper) {
        var pos = helper.absolutePos(POOL);
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (var side : Direction.Plane.HORIZONTAL) {
            helper.getLevel().setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        }
        helper.getLevel().setBlockAndUpdate(pos, FluidsAS.LIQUID_STARLIGHT.getSource().get().defaultFluidState().createLegacyBlock());
        return pos;
    }

    private static ItemEntity drop(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        var entity = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack);
        entity.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static LiquidStarlightRecipe recipe(GameTestHelper helper) {
        return (LiquidStarlightRecipe) helper.getLevel().getRecipeManager().byKey(
                ResourceLocation.parse("appliedas:liquid_starlight/form_astral_fluix_cluster")).orElseThrow().value();
    }
}
