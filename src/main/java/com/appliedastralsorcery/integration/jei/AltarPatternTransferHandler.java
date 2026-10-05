package com.appliedastralsorcery.integration.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ItemModText;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.integration.modules.itemlists.TransferHelper;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.integration.jei.category.AltarRecipeCategory;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class AltarPatternTransferHandler
        implements IRecipeTransferHandler<PatternEncodingTermMenu, AltarRecipe> {
    // Match AE2's preference for craftable resources, undamaged items, and larger stored amounts.
    private static final Comparator<GridInventoryEntry> INGREDIENT_PRIORITY = Comparator
            .comparing(GridInventoryEntry::isCraftable)
            .thenComparing(entry -> !(entry.getWhat() instanceof AEItemKey item) || !item.isDamaged())
            .thenComparing(GridInventoryEntry::getStoredAmount);

    private final IRecipeTransferHandlerHelper helper;

    public AltarPatternTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.of(PatternEncodingTermMenu.TYPE);
    }

    @Override
    public RecipeType<AltarRecipe> getRecipeType() {
        return AltarRecipeCategory.RECIPE_TYPE;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(IRecipeTransferContext<AltarRecipe, PatternEncodingTermMenu> context,
            boolean doTransfer) {
        var error = transferRecipe(context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                context.getPlayer(), context.isMaxTransfer(), doTransfer);
        if (doTransfer) {
            context.completeRecipeTransfer(error == null ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
        }
        return error;
    }

    @Override
    @SuppressWarnings("removal")
    @Nullable
    public IRecipeTransferError transferRecipe(PatternEncodingTermMenu menu, AltarRecipe recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        // AE2 attaches its client repository when the terminal screen is created.
        var clientRepo = menu.getClientRepo();
        if (clientRepo == null) {
            return helper.createInternalError();
        }
        var priorities = EncodingHelper.getIngredientPriorities(menu, INGREDIENT_PRIORITY);
        var inputs = getInputs(recipe, recipeSlots).stream()
                .map(choices -> List.of(choices.stream()
                        .max(Comparator.comparingInt(stack -> priorities.getOrDefault(stack.what(), Integer.MIN_VALUE)))
                        .orElseThrow()))
                .toList();
        var outputs = getOutputs(recipeSlots);

        // EncodingHelper merges identical keys, but silently drops keys beyond the terminal's slot limit.
        if (inputs.stream().flatMap(List::stream).map(GenericStack::what).distinct().count()
                > menu.getProcessingInputSlots().length
                || outputs.stream().map(GenericStack::what).distinct().count()
                > menu.getProcessingOutputSlots().length) {
            return helper.createUserErrorWithTooltip(ItemModText.RECIPE_TOO_LARGE.text());
        }

        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
            return null;
        }

        boolean anyCraftable = clientRepo.getAllEntries().stream()
                .filter(GridInventoryEntry::isCraftable)
                .anyMatch(entry -> inputs.stream().flatMap(List::stream)
                        .anyMatch(stack -> stack.what().equals(entry.getWhat())));
        return new IRecipeTransferError() {
            @Override
            public Type getType() {
                return Type.COSMETIC;
            }

            @Override
            public void getTooltip(ITooltipBuilder tooltip) {
                tooltip.addAll(TransferHelper.createEncodingTooltip(anyCraftable, true));
            }
        };
    }

    public static List<List<GenericStack>> getInputs(AltarRecipe recipe, IRecipeSlotsView recipeSlots) {
        var inputs = new ArrayList<List<GenericStack>>();
        for (var slot : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            // IngredientBridge fluid grid/relay slots display filled containers and still require items.
            var items = slot.getItemStacks().map(GenericStack::fromItemStack).filter(Objects::nonNull).toList();
            if (!items.isEmpty()) {
                inputs.add(items);
            }
        }
        // These are additional resource inputs, separate from the filled containers in the altar grid.
        for (var fluid : recipe.getRequiredFluid()) {
            var key = AEFluidKey.of(fluid);
            if (key != null) {
                inputs.add(List.of(new GenericStack(key, fluid.getAmount())));
            }
        }
        for (var lumen : recipe.getRequiredLumen()) {
            var type = GhostIngredientResolver.resolveLumen(lumen);
            if (type != null) {
                inputs.add(List.of(new GenericStack(LumenKey.of(type), lumen.getAmount())));
            }
        }
        return inputs;
    }

    public static List<GenericStack> getOutputs(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getItemStacks().findFirst().orElse(ItemStack.EMPTY))
                .map(GenericStack::fromItemStack)
                .filter(Objects::nonNull)
                .toList();
    }
}
