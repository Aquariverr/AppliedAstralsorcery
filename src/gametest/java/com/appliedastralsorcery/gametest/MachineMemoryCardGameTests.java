package com.appliedastralsorcery.gametest;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEItems;
import appeng.items.tools.MemoryCardItem;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chisel.AutoChiselBlockEntity;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class MachineMemoryCardGameTests {
    @GameTest(template = "attunement_test")
    public static void chiselCardCopiesConfigurationWithoutContents(GameTestHelper helper) {
        var sourcePos = new BlockPos(4, 2, 4);
        var targetPos = new BlockPos(6, 2, 4);
        helper.setBlock(sourcePos, ModContent.AUTO_CHISEL.get());
        helper.setBlock(targetPos, ModContent.AUTO_CHISEL.get());
        var source = (AutoChiselBlockEntity) helper.getBlockEntity(sourcePos);
        var target = (AutoChiselBlockEntity) helper.getBlockEntity(targetPos);
        source.toggleAutoInput();
        source.toggleAutoOutput();
        source.toggleDroppedItemMode();
        source.toggleRoundRobin();
        source.cycleSide(Direction.NORTH, true);
        source.cycleSide(Direction.UP, false);
        source.getInventory().setStackInSlot(1, new ItemStack(Items.DIAMOND, 9));
        target.getInventory().setStackInSlot(1, new ItemStack(Items.EMERALD, 3));
        source.getLumenHandler().fill(LumenAS.EVORSIO.stack(800), ILumenHandler.Action.EXECUTE);
        target.getLumenHandler().fill(LumenAS.EVORSIO.stack(123), ILumenHandler.Action.EXECUTE);
        helper.assertTrue(target.getItemHandler(Direction.EAST).insertItem(0, ItemsAS.STARMETAL_INGOT.toStack(), true).isEmpty(),
                "Before paste the target must allow valid ingredients through its input side");
        var card = AEItems.MEMORY_CARD.stack();
        use(helper, sourcePos, card, true);
        // The card must remain usable after item serialization, including its custom component.
        card = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (CompoundTag) card.save(helper.getLevel().registryAccess()));
        use(helper, targetPos, card, false);
        helper.assertTrue(source.exportMemoryCardSettings().equals(target.exportMemoryCardSettings()),
                "Chisel card must restore every face and processing toggle");
        helper.assertTrue(target.getInventory().getStackInSlot(1).is(Items.EMERALD)
                        && target.getInventory().getStackInSlot(1).getCount() == 3 && target.getLumenAmount() == 123,
                "Pasting must preserve the target inventory and lumen");
        helper.assertTrue(source.getInventory().getStackInSlot(1).getCount() == 9 && source.getLumenAmount() == 800,
                "Copying must preserve the source contents");
        helper.assertTrue(target.getItemHandler(Direction.EAST).insertItem(0, ItemsAS.STARMETAL_INGOT.toStack(), true).getCount() == 1,
                "Pasted dropped-item mode must immediately disable item-handler insertion");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void arrayCardsPreserveStoredResourcesDuringTypeSwitch(GameTestHelper helper) {
        int z = 4;
        for (var block : new net.minecraft.world.level.block.Block[]{
                ModContent.ME_LUMEN_ARRAY.get(), ModContent.ME_LUMEN_ALCHEMY_ARRAY.get()}) {
            var sourcePos = new BlockPos(4, 2, z);
            var targetPos = new BlockPos(6, 2, z);
            helper.setBlock(sourcePos, block);
            helper.setBlock(targetPos, block);
            var source = (MELumenArrayBlockEntity) helper.getBlockEntity(sourcePos);
            var target = (MELumenArrayBlockEntity) helper.getBlockEntity(targetPos);
            source.setSelectedLumen(LumenAS.AEVITAS.get());
            source.setPullTarget(777);
            source.setExportEnabled(false);
            source.setSupplyItemsEnabled(false);
            source.setInteractWithFilaments(true);
            source.setRedstoneControlEnabled(true);
            target.setSelectedLumen(LumenAS.EVORSIO.get());
            target.getTileData().getLumenHandler().fill(LumenAS.EVORSIO.stack(321), ILumenHandler.Action.EXECUTE);
            target.getTileData().getFluidTank().fill(new FluidStack(FluidsAS.LIQUID_STARLIGHT.getSource().get(), 900),
                    FluidAction.EXECUTE);
            if (target.isAlchemyArray()) {
                var saved = target.saveWithoutMetadata(helper.getLevel().registryAccess());
                var ingredients = new CompoundTag();
                ingredients.putInt(LumenAS.AEVITAS.get().getRegistryKey().orElseThrow().location().toString(), 19);
                saved.put("meAlchemyIngredients", ingredients);
                target.loadWithComponents(saved, helper.getLevel().registryAccess());
            }
            var before = target.saveWithoutMetadata(helper.getLevel().registryAccess());
            var card = AEItems.MEMORY_CARD.stack();
            use(helper, sourcePos, card, true);
            use(helper, targetPos, card, false);
            helper.assertTrue(source.exportMemoryCardSettings().equals(target.exportMemoryCardSettings()),
                    "Both array types must restore lumen selection, reserve and every toggle");
            helper.assertTrue(target.isSwitchPending() && target.getActiveLumen() == LumenAS.EVORSIO.get()
                            && target.getStoredAmount() == 321 && target.getTileData().getContainedFluid().getAmount() == 900,
                    "Pasted lumen changes must use safe deferred switching without deleting old lumen or starlight");
            helper.assertTrue(before.getCompound("meAlchemyIngredients").equals(
                            target.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("meAlchemyIngredients")),
                    "Alchemy ingredients reserved before a paste must remain untouched");
            var blankPos = new BlockPos(8, 2, z);
            helper.setBlock(blankPos, block);
            use(helper, blankPos, card, true);
            use(helper, targetPos, card, false);
            helper.assertTrue(!target.isSwitchPending() && target.getSelectedLumen() == LumenAS.EVORSIO.get()
                            && target.getStoredAmount() == 321,
                    "An unconfigured array must clear pending selection while preserving the target's current binding");
            z += 4;
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void blankMismatchedAndClearedCardsDoNotChangeMachines(GameTestHelper helper) {
        var chiselPos = new BlockPos(4, 2, 4);
        var arrayPos = new BlockPos(6, 2, 4);
        helper.setBlock(chiselPos, ModContent.AUTO_CHISEL.get());
        helper.setBlock(arrayPos, ModContent.ME_LUMEN_ARRAY.get());
        var chisel = (AutoChiselBlockEntity) helper.getBlockEntity(chiselPos);
        var array = (MELumenArrayBlockEntity) helper.getBlockEntity(arrayPos);
        array.setPullTarget(432);
        var settings = array.exportMemoryCardSettings();
        var card = AEItems.MEMORY_CARD.stack();
        use(helper, arrayPos, card, false);
        use(helper, chiselPos, card, true);
        use(helper, arrayPos, card, false);
        helper.assertTrue(settings.equals(array.exportMemoryCardSettings()), "Blank and wrong-machine cards must do nothing");
        use(helper, arrayPos, card, true);
        MemoryCardItem.clearCard(card);
        // A native AE2 export can later replace the source name. Old machine settings must still be gone.
        card.set(AEComponents.EXPORTED_SETTINGS_SOURCE, ModContent.ME_LUMEN_ARRAY.get().getName());
        array.setPullTarget(111);
        use(helper, arrayPos, card, false);
        helper.assertTrue(array.getPullTarget() == 111, "AE2 clearing must remove our exported-settings component");
        helper.assertTrue(!chisel.isDroppedItemMode(), "Copying must not change the source configuration");
        helper.succeed();
    }

    private static void use(GameTestHelper helper, BlockPos relative, ItemStack card, boolean copy) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(relative);
        var player = FakePlayerFactory.getMinecraft(level);
        var previous = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            player.setShiftKeyDown(copy);
            var result = level.getBlockState(pos).useItemOn(card, level, player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
            helper.assertTrue(result == ItemInteractionResult.CONSUME, "Memory-card use must consume block interaction");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Memory-card use must not open the machine menu");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setShiftKeyDown(sneaking);
        }
    }
}
