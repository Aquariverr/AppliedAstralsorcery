package com.appliedastralsorcery.gametest;

import appeng.api.stacks.AEFluidKey;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chalice.MEChaliceBlockEntity;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AdditionalMemoryCardGameTests {
    @GameTest(template = "attunement_test")
    public static void chaliceCardCopiesFluidComponentsAndKeepsTankContents(GameTestHelper helper) {
        var first = new BlockPos(5, 2, 5);
        var second = new BlockPos(7, 2, 5);
        helper.setBlock(first, ModContent.ME_CHALICE.get());
        helper.setBlock(second, ModContent.ME_CHALICE.get());
        var source = (MEChaliceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(first));
        var target = (MEChaliceBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(second));
        var selected = new FluidStack(Fluids.WATER, 1000);
        selected.set(DataComponents.CUSTOM_NAME, Component.literal("configured fluid variant"));
        source.setSelectedKey(AEFluidKey.of(selected));
        source.setPullTarget(54_321);
        source.setPullEnabled(false);
        source.setExportEnabled(false);
        helper.assertTrue(target.getTankView().fill(new FluidStack(Fluids.WATER, 1234), FluidAction.EXECUTE) == 1234,
                "The target tank must contain a real fluid reserve before applying settings");
        var before = target.getContainedFluid().copy();
        copyCard(helper, helper.absolutePos(first), helper.absolutePos(second));
        helper.assertTrue(source.getSelectedKey().equals(target.getSelectedKey())
                        && target.getPullTarget() == 54_321 && !target.isPullEnabled() && !target.isExportEnabled(),
                "The chalice memory card must retain the exact fluid components, target and transfer switches");
        helper.assertTrue(FluidStack.matches(before, target.getContainedFluid()),
                "Pasting chalice settings must neither replace nor duplicate stored fluid");
        source.setSelectedKey(null);
        copyCard(helper, helper.absolutePos(first), helper.absolutePos(second));
        helper.assertTrue(target.getSelectedKey() == null && FluidStack.matches(before, target.getContainedFluid()),
                "An unconfigured source must clear the target selection without draining its tank");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void chamberCardCopiesGhostFiltersWithoutCopyingInventory(GameTestHelper helper) {
        var first = new BlockPos(5, 2, 5);
        var second = new BlockPos(7, 2, 5);
        helper.setBlock(first, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(second, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        var source = (StarlightTransmutationBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(first));
        var target = (StarlightTransmutationBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(second));
        var sample = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .map(RecipeHolder::value).filter(recipe -> !recipe.getInputs().isEmpty()
                        && recipe.getInputs().getFirst().isSimple()
                        && recipe.getInputs().getFirst().getItems().length > 0
                        && recipe.getInputs().getFirst().getItems()[0].getMaxStackSize() >= 8).findFirst().orElseThrow();
        var marker = sample.getInputs().getFirst().getItems()[0].copyWithCount(8);
        marker.set(DataComponents.CUSTOM_NAME, Component.literal("configured item variant"));
        helper.assertTrue(source.setPullMarker(0, marker) && target.setPullMarker(8, marker),
                "The marker fixture must be accepted by actual chamber recipes");
        source.toggleAutoPull();
        source.toggleLumenOverclock();
        source.getInventory().setStackInSlot(0, marker.copyWithCount(2));
        target.getInventory().setStackInSlot(0, marker.copyWithCount(3));
        target.getInventory().setStackInSlot(9, new ItemStack(Items.DIAMOND, 4));
        copyCard(helper, helper.absolutePos(first), helper.absolutePos(second));
        helper.assertTrue(target.isAutoPull() && target.isLumenOverclock()
                        && ItemStack.matches(marker, target.getPullMarkers().getStackInSlot(0))
                        && target.getPullMarkers().getStackInSlot(8).isEmpty(),
                "The chamber card must copy switches and exact ghost marker counts/components, clearing unused slots");
        helper.assertTrue(source.getInventory().getStackInSlot(0).getCount() == 2
                        && target.getInventory().getStackInSlot(0).getCount() == 3
                        && target.getInventory().getStackInSlot(9).getCount() == 4,
                "Copy/paste must preserve both machines' real input and output inventories");
        helper.succeed();
    }

    private static void copyCard(GameTestHelper helper, BlockPos source, BlockPos target) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var previous = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        try {
            var card = AEItems.MEMORY_CARD.stack();
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            player.setShiftKeyDown(true);
            var copied = level.getBlockState(source).useItemOn(card, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(source.getCenter(), Direction.UP, source, false));
            player.setShiftKeyDown(false);
            var pasted = level.getBlockState(target).useItemOn(card, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(target.getCenter(), Direction.UP, target, false));
            helper.assertTrue(copied == ItemInteractionResult.CONSUME && pasted == ItemInteractionResult.CONSUME,
                    "The actual block interaction must handle memory cards on both save and load");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Memory card use must not open a menu");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setShiftKeyDown(sneaking);
        }
    }
}
