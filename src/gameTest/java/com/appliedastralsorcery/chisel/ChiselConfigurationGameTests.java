package com.appliedastralsorcery.chisel;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ChiselConfigurationGameTests {
    private static final BlockPos POS = new BlockPos(4, 4, 4);

    @GameTest(template = "wand_empty")
    public static void bothDirectionsCycleReversiblyAndAllowPassiveAccess(GameTestHelper helper) {
        var machine = machine(helper);
        var handler = machine.getItemHandler(Direction.UP);
        for (var side : Direction.values()) {
            var original = machine.getSideMode(side);
            machine.cycleSide(side);
            machine.cycleSide(side, true);
            helper.assertTrue(machine.getSideMode(side) == original, "Right-click must undo one left-click on each face");
            for (int i = 0; i < 4; i++) machine.cycleSide(side, true);
            helper.assertTrue(machine.getSideMode(side) == original, "Reverse cycle must contain exactly four states");
        }
        machine.cycleSide(Direction.UP, true); // input -> none
        machine.cycleSide(Direction.UP, true); // none -> input/output
        machine.toggleAutoInput();
        machine.toggleAutoOutput();
        helper.assertTrue(handler.insertItem(0, ItemsAS.STARMETAL_INGOT.toStack(), false).isEmpty(),
                "Turning auto input off must not prevent external insertion on a shared face");
        machine.getInventory().setStackInSlot(1, ItemsAS.STARDUST.toStack());
        helper.assertTrue(!handler.extractItem(1, 1, false).isEmpty(),
                "Turning auto output off must not prevent external extraction on a shared face");
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty()
                && !handler.insertItem(1, ItemsAS.STARDUST.toStack(), false).isEmpty(),
                "Shared faces must still distinguish input and product slots");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void automaticTransferSwitchesWorkIndependently(GameTestHelper helper) {
        var machine = machine(helper);
        helper.setBlock(POS.above(), Blocks.CHEST);
        helper.setBlock(POS.below(), Blocks.CHEST);
        var source = (ChestBlockEntity) helper.getBlockEntity(POS.above());
        var destination = (ChestBlockEntity) helper.getBlockEntity(POS.below());
        source.setItem(0, ItemsAS.STARMETAL_INGOT.toStack());
        machine.getInventory().setStackInSlot(1, ItemsAS.STARDUST.toStack());
        machine.toggleAutoInput();
        machine.toggleAutoOutput();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!source.isEmpty() && destination.isEmpty(), "Both off must stop autonomous transfers");
            machine.toggleAutoInput();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(source.isEmpty() && !machine.getInventory().getStackInSlot(0).isEmpty()
                        && destination.isEmpty(), "Input on must pull without pushing output");
                machine.getInventory().setStackInSlot(0, ItemStack.EMPTY);
                source.setItem(0, ItemsAS.STARMETAL_INGOT.toStack());
                machine.toggleAutoInput();
                machine.toggleAutoOutput();
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(!source.isEmpty() && !destination.isEmpty()
                            && machine.getInventory().getStackInSlot(0).isEmpty(), "Output on must push without pulling input");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "wand_empty")
    public static void savedModesMigrateAndNewSwitchesPersist(GameTestHelper helper) {
        var machine = machine(helper);
        var legacy = new CompoundTag();
        legacy.putIntArray("sides", new int[]{2, 1, 0, 0, 1, 2});
        machine.loadWithComponents(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(machine.getSideMode(Direction.DOWN) == AutoChiselBlockEntity.SideMode.OUTPUT
                && machine.getSideMode(Direction.UP) == AutoChiselBlockEntity.SideMode.INPUT
                && machine.getSideMode(Direction.NORTH) == AutoChiselBlockEntity.SideMode.INPUT,
                "Legacy output/input ordinals must retain their meanings; removed off faces migrate to input");
        helper.assertTrue(machine.isAutoInput() && machine.isAutoOutput(), "Legacy machines keep automatic transfers enabled");
        machine.cycleSide(Direction.UP, true);
        machine.cycleSide(Direction.UP, true);
        machine.cycleSide(Direction.NORTH, true); // none
        machine.toggleAutoInput();
        machine.toggleAutoOutput();
        var restored = new AutoChiselBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.loadWithComponents(machine.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.getSideMode(Direction.UP) == AutoChiselBlockEntity.SideMode.INPUT_OUTPUT
                && restored.getSideMode(Direction.NORTH) == AutoChiselBlockEntity.SideMode.NONE
                && !restored.isAutoInput() && !restored.isAutoOutput(), "Shared faces and both switches must persist");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void menuButtonsValidateAndSyncReverseCyclesAndAutoSwitches(GameTestHelper helper) {
        var machine = machine(helper);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "chisel-config"));
        player.setPos(machine.getBlockPos().getCenter());
        var menu = new AutoChiselMenu(0, player.getInventory(), machine);
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.REVERSE_SIDE_BASE + Direction.UP.ordinal())
                && menu.getSideMode(Direction.UP) == AutoChiselBlockEntity.SideMode.NONE,
                "A reverse face packet must synchronize the new none mode");
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.REVERSE_SIDE_BASE + Direction.UP.ordinal())
                && menu.getSideMode(Direction.UP) == AutoChiselBlockEntity.SideMode.INPUT_OUTPUT,
                "A reverse face packet must be validated and reflected in menu data");
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.AUTO_INPUT_BUTTON) && !menu.isAutoInput()
                && menu.isAutoOutput(), "Input toggle is independent and synchronized");
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.AUTO_OUTPUT_BUTTON) && !menu.isAutoOutput(),
                "Output toggle is independent and synchronized");
        menu.setConfigurationOpen(true);
        helper.assertTrue(menu.slots.stream().noneMatch(slot -> slot.isActive()), "Configuration must hide and disable underlying slots");
        menu.setConfigurationOpen(false);
        helper.assertTrue(menu.slots.stream().allMatch(slot -> slot.isActive()), "Returning must restore all inventory slots");
        player.setPos(machine.getBlockPos().getCenter().add(20, 0, 0));
        helper.assertTrue(!menu.clickMenuButton(player, AutoChiselMenu.REVERSE_SIDE_BASE)
                && !menu.clickMenuButton(player, AutoChiselMenu.AUTO_OUTPUT_BUTTON), "Remote configuration packets must be rejected");
        helper.succeed();
    }

    private static AutoChiselBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.AUTO_CHISEL.get());
        return (AutoChiselBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void noneBlocksCachedHandlersAndAutomaticTransfers(GameTestHelper helper) {
        var machine = machine(helper);
        var top = machine.getItemHandler(Direction.UP);
        var bottom = machine.getItemHandler(Direction.DOWN);
        machine.cycleSide(Direction.UP, true); // input -> none
        machine.cycleSide(Direction.DOWN);
        machine.cycleSide(Direction.DOWN); // output -> both -> none
        helper.setBlock(POS.above(), Blocks.CHEST);
        helper.setBlock(POS.below(), Blocks.CHEST);
        var source = (ChestBlockEntity) helper.getBlockEntity(POS.above());
        var destination = (ChestBlockEntity) helper.getBlockEntity(POS.below());
        source.setItem(0, ItemsAS.STARMETAL_INGOT.toStack());
        machine.getInventory().setStackInSlot(1, ItemsAS.STARDUST.toStack());
        helper.assertTrue(!top.insertItem(0, ItemsAS.STARMETAL_INGOT.toStack(), false).isEmpty()
                && bottom.extractItem(1, 1, false).isEmpty() && bottom.getStackInSlot(1).isEmpty(),
                "Cached capabilities must reject input and hide output on none faces");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!source.isEmpty() && destination.isEmpty()
                    && machine.getInventory().getStackInSlot(0).isEmpty(), "None faces must stop automatic pulling and pushing");
            machine.cycleSide(Direction.UP); // none -> input
            machine.cycleSide(Direction.DOWN, true); // none -> both
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(source.isEmpty() && !destination.isEmpty(), "Re-enabled faces must resume transfers");
                helper.succeed();
            });
        });
    }
}
