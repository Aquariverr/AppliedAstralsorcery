package com.appliedastralsorcery.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.crystal.FormAstralFluixCluster;

import guideme.color.ConstantColor;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.DefaultStyles;
import guideme.document.LytRect;
import guideme.document.block.AlignItems;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBox;
import guideme.document.block.LytHBox;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlot;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import guideme.layout.LayoutContext;
import guideme.render.RenderContext;
import guideme.document.interaction.GuideTooltip;
import guideme.document.interaction.ItemTooltip;
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.ingredient.IsStableArtifactIngredient;
import hellfirepvp.astralsorcery.common.artifact.ArtifactStability;
import hellfirepvp.astralsorcery.common.item.ArtifactItem;
import hellfirepvp.astralsorcery.common.item.block.CelestialCrystalClusterBlockItem;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.LiquidStarlightRecipe;
import hellfirepvp.astralsorcery.common.recipe.liquid.output.LiquidStarlightOutputGrowSize;
import hellfirepvp.astralsorcery.common.recipe.liquid.output.LiquidStarlightOutputMergeCrystal;
import hellfirepvp.astralsorcery.common.research.tome.TomePage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Adds Astral Sorcery recipes to AE2's guide through GuideME's service loader. */
public final class GuideRecipes implements RecipeTypeMappingSupplier {
    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(RecipeTypesAS.ALTAR_CRAFTING_TYPE.get(), GuideRecipes::altar);
        mappings.add(RecipeTypesAS.LIQUID_STARLIGHT_TYPE.get(), GuideRecipes::liquidStarlight);
    }

    private static LytBlock altar(RecipeHolder<AltarRecipe> holder) {
        var recipe = holder.value();
        var altar = recipe.getRequiredType().getAltarItem();
        var box = LytStandardRecipeBox.builder()
                .title(altar.getHoverName().getString())
                .icon(altar)
                .customBody(new AltarGrid(recipe))
                .addTop(text("guide.appliedas.altar.conditions", recipe.isOnlyNight()
                        ? Component.translatable("guide.appliedas.altar.night")
                        : Component.translatable("guide.appliedas.altar.any_time"), recipe.getDuration() / 20.0));
        if (!recipe.getRequiredAdditionalInputs().isEmpty()) {
            var extra = new LytHBox();
            extra.setAlignItems(AlignItems.CENTER);
            extra.setGap(4);
            extra.append(text("guide.appliedas.altar.extra"));
            for (var input : recipe.getRequiredAdditionalInputs()) {
                // Preserve alternative ingredients and stack counts in the hoverable slots.
                extra.append(new LytSlot(Ingredient.of(displayItems(input.ingredient())
                        .map(stack -> stack.copyWithCount(input.count())))));
            }
            box.addBottom(extra);
        }
        for (var fluid : recipe.getRequiredFluid()) {
            box.addBottom(text("guide.appliedas.altar.fluid", fluid.getAmount(), fluid.getHoverName()));
        }
        for (var lumen : recipe.getRequiredLumen()) {
            box.addBottom(text("guide.appliedas.altar.lumen", lumen.getAmount(), lumen.getLumen().getName()));
        }
        return box.build(holder);
    }

    private static LytBlock liquidStarlight(RecipeHolder<LiquidStarlightRecipe> holder) {
        var recipe = holder.value();
        // These recipes change crystals through modifiers instead of returning a result stack.
        // Leave other recipes to their own handlers rather than inventing an output for them.
        if (!holder.id().getNamespace().equals("appliedas") || recipe.getOutputModifiers().size() != 1) {
            return null;
        }
        var modifier = recipe.getOutputModifiers().getFirst();
        ItemStack result;
        String description;
        switch (holder.id().getPath()) {
            case "liquid_starlight/form_astral_fluix_cluster" -> {
                if (!(modifier instanceof FormAstralFluixCluster)) return null;
                result = ModContent.ASTRAL_FLUIX_CLUSTER_ITEM.toStack();
                CelestialCrystalClusterBlockItem.setStage(result, 0);
                description = "guide.appliedas.liquid_starlight.form";
            }
            case "liquid_starlight/grow_astral_fluix_crystal" -> {
                if (!(modifier instanceof LiquidStarlightOutputGrowSize)) return null;
                result = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
                description = "guide.appliedas.liquid_starlight.grow";
            }
            case "liquid_starlight/merge_astral_fluix_crystals" -> {
                if (!(modifier instanceof LiquidStarlightOutputMergeCrystal)) return null;
                result = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
                description = "guide.appliedas.liquid_starlight.merge";
            }
            default -> {
                return null;
            }
        }

        // Display blank crystals: generating gameplay attributes requires server config.
        var inputs = Stream.concat(Stream.of(recipe.getInput()), recipe.getOtherInputs().stream())
                .map(input -> Ingredient.of(displayItems(input.ingredient())
                        .map(stack -> stack.copyWithCount(input.count()))))
                .toList();
        return LytStandardRecipeBox.builder()
                .title(Component.translatable("fluid_type.astralsorcery.liquid_starlight").getString())
                .icon(FluidsAS.LIQUID_STARLIGHT.getBucket())
                .input(LytSlotGrid.row(inputs, false))
                .output(result)
                .addTop(text("guide.appliedas.liquid_starlight.source"))
                .addBottom(text(description))
                .build(holder);
    }

    private static Stream<ItemStack> displayItems(Ingredient ingredient) {
        if (ingredient.getCustomIngredient() instanceof IsStableArtifactIngredient) {
            // The native ingredient generates gameplay artifacts using server config, which is
            // unavailable in the title-screen guide. Blank display artifacts need no server state.
            return RegistriesAS.REGISTRY_ARTIFACT_TYPES.stream().map(ArtifactItem::createForDisplay)
                    .peek(stack -> {
                        stack.set(DataComponentsAS.ARTIFACT, java.util.Objects.requireNonNull(
                                stack.get(DataComponentsAS.ARTIFACT), "Display artifacts must have artifact data")
                                .changeStability(ArtifactStability.STABLE));
                        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                                Component.translatable("ingredient.astralsorcery.stable_artifact.description")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                    });
        }
        return java.util.Arrays.stream(ingredient.getItems());
    }

    private static LytParagraph text(String key, Object... args) {
        var paragraph = new LytParagraph();
        paragraph.setStyle(DefaultStyles.CRAFTING_RECIPE_TYPE);
        paragraph.appendText(Component.translatable(key, args).getString());
        return paragraph;
    }

    /** Use the tome's background and coordinates, with GuideME's ingredient tooltips. */
    private static final class AltarGrid extends LytBox {
        private record PositionedSlot(LytSlot slot, int x, int y) {}
        private final List<PositionedSlot> slots = new ArrayList<>();
        private final ResourceLocation background;

        private AltarGrid(AltarRecipe recipe) {
            background = switch (recipe.getRequiredType()) {
                case ILLUMINATION -> TexturesAS.SCREEN_TOME_PAGE_GRID_ALTAR_T1.getKey();
                case RESONANCE -> {
                    boolean expanded = false;
                    for (int i = 0; i < 25; i++) {
                        if ((i / 5 == 0 || i / 5 == 4 || i % 5 == 0 || i % 5 == 4)
                                && !recipe.getGrid().getRelayInputs().get(i).isEmpty()) expanded = true;
                    }
                    yield (expanded ? TexturesAS.SCREEN_TOME_PAGE_GRID_ALTAR_T2_EXPANDED
                            : TexturesAS.SCREEN_TOME_PAGE_GRID_ALTAR_T2).getKey();
                }
                case LUMINANCE -> TexturesAS.SCREEN_TOME_PAGE_GRID_ALTAR_T3.getKey();
                case RADIANCE -> TexturesAS.SCREEN_TOME_PAGE_GRID_ALTAR_T4.getKey();
            };
            for (int i = 0; i < 9; i++) {
                add(recipe.getGrid().getInputs().get(i), 61 + (i % 3) * 18, 118 + (i / 3) * 18);
            }
            int[] positions = {0, 17, 54, 91, 108};
            for (int i = 0; i < 25; i++) {
                if (i == 12) continue;
                add(recipe.getGrid().getRelayInputs().get(i), 25 + positions[i % 5], 82 + positions[i / 5]);
            }
            for (int i = 0; i < recipe.getOutputs().size(); i++) {
                add(new TomeSlot(Ingredient.of(recipe.getOutputs().get(i))),
                        i == 0 ? TomePage.DEFAULT_WIDTH / 2 - 8 : 117,
                        i == 0 ? 40 : 18 + (i - 1) * 18);
            }
        }

        private void add(IngredientBridge ingredient, int x, int y) {
            add(new TomeSlot(ingredient.isEmpty() ? Ingredient.EMPTY : Ingredient.of(ingredient.getItems())), x, y);
        }

        private void add(LytSlot slot, int x, int y) {
            slots.add(new PositionedSlot(slot, x, y));
            append(slot);
        }

        @Override
        protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
            for (var slot : slots) {
                slot.slot().layout(context, x + slot.x(), y + slot.y(), availableWidth);
            }
            return new LytRect(x, y, TomePage.DEFAULT_WIDTH, TomePage.DEFAULT_HEIGHT);
        }

        @Override
        public void render(RenderContext context) {
            context.fillRect(bounds, new ConstantColor(0xFFF0E3C3));
            context.fillTexturedRect(bounds, background);
            super.render(context);
        }
    }

    /** The tome supplies the slot artwork; only draw the ingredient on top. */
    private static final class TomeSlot extends LytSlot {
        private final ItemStack[] stacks;

        private TomeSlot(Ingredient ingredient) {
            super(ingredient);
            stacks = ingredient.getItems();
        }

        private ItemStack displayed() {
            return stacks.length == 0 ? ItemStack.EMPTY
                    : stacks[(int) ((System.nanoTime() / 2_000_000_000L) % stacks.length)];
        }

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            return new LytRect(x, y, 16, 16);
        }

        @Override
        public void render(RenderContext context) {
            var stack = displayed();
            if (!stack.isEmpty()) context.renderItem(stack, bounds.x(), bounds.y(), 1, 16, 16);
        }

        @Override
        public Optional<GuideTooltip> getTooltip(float x, float y) {
            var stack = displayed();
            return stack.isEmpty() ? Optional.empty() : Optional.of(new ItemTooltip(stack));
        }
    }
}
