package com.appliedastralsorcery.crystal;

import java.util.List;

import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.MapCodec;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipeInput;
import hellfirepvp.astralsorcery.common.recipe.liquid.output.LiquidStarlightOutputFormCrystalCluster;
import hellfirepvp.astralsorcery.common.recipe.liquid.output.LiquidStarlightRecipeOutputModifier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** A native liquid-starlight recipe output, so timing and ingredient handling stay shared. */
public final class FormAstralFluixCluster extends LiquidStarlightRecipeOutputModifier {
    private static final FormAstralFluixCluster INSTANCE = new FormAstralFluixCluster();
    public static final Type<FormAstralFluixCluster> TYPE = new Type<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    private FormAstralFluixCluster() {}

    @Override
    public Type<?> getType() {
        return ModContent.FORM_ASTRAL_FLUIX_CLUSTER.get();
    }

    @Override
    public boolean isValidInputForOutput(LiquidStarlightRecipeInput input, List<ItemEntity> otherInputs) {
        var trigger = input.getTriggerEntity();
        var level = trigger.level();
        var pos = trigger.blockPosition();
        return FluidsAS.LIQUID_STARLIGHT.isSource(level.getFluidState(pos))
                && ModContent.ASTRAL_FLUIX_CLUSTER.get().defaultBlockState().canSurvive(level, pos);
    }

    @Override
    public boolean isOutput(LiquidStarlightRecipeInput input, ItemEntity output) {
        return false;
    }

    @Override
    @SuppressWarnings("resource") // Minecraft manages the trigger entity's world lifetime.
    public void createOutput(LiquidStarlightRecipe recipe, LiquidStarlightRecipeInput input) {
        var trigger = input.getTriggerEntity();
        var level = trigger.level();
        var pos = trigger.blockPosition();
        // Replace the source only after all inputs and support have been checked. Inputs are
        // consumed here, after successful placement, rather than by the recipe beforehand.
        if (!level.isClientSide() && isValidInputForOutput(input, input.getOtherEntities())
                && level.setBlockAndUpdate(pos, ModContent.ASTRAL_FLUIX_CLUSTER.get().defaultBlockState())
                && level.getBlockEntity(pos) instanceof AstralFluixClusterBlockEntity cluster) {
            cluster.generateProperties();
            recipe.consumeItemInputs(input);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void playCraftingEffects(LiquidStarlightRecipe recipe, LiquidStarlightRecipeInput input, RandomSource rand, int tick) {
        LiquidStarlightOutputFormCrystalCluster.getInstance().playCraftingEffects(recipe, input, rand, tick);
    }
}
