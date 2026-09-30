package com.appliedastralsorcery.chisel;

import java.util.List;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ChiselGameTests {
    private static final BlockPos POS = new BlockPos(3, 3, 3);

    @GameTest(template = "wand_empty")
    public static void nativeAndAddonCrystalSplitsPreserveAttributes(GameTestHelper helper) {
        for (var item : List.of(ItemsAS.ROCK_CRYSTAL.get(), ItemsAS.CELESTIAL_CRYSTAL.get(),
                ItemsAS.ATTUNED_ROCK_CRYSTAL.get(), ModContent.ASTRAL_FLUIX_CRYSTAL.get())) {
            var input = crystal(new ItemStack(item), 2, 2, 2);
            var attributes = input.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            for (int seed = 0; seed < 24; seed++) {
                var outputs = ChiselProcessing.process(input, RandomSource.create(seed));
                helper.assertTrue(outputs.size() == 2 && outputs.getFirst().is(item)
                                && outputs.getLast().is(item.getCrystalSplitItem()),
                        "Both halves must follow the native split material rule");
                var first = outputs.getFirst().get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
                var second = outputs.getLast().get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
                helper.assertTrue(first.getTotalTierCount() == 3 && second.getTotalTierCount() >= 1
                        && second.getTotalTierCount() <= 3, "Split sizes and native attribute loss must be retained");
                helper.assertTrue(first.getProperties().equals(attributes.getProperties())
                        && second.getProperties().equals(attributes.getProperties()), "Generation budgets must persist");
                for (var attr : attributes.getAttributes()) helper.assertTrue(
                        first.getAttributeTier(attr) + second.getAttributeTier(attr) <= attr.getTier(),
                        "No attribute tier may be duplicated");
                helper.assertTrue(input.get(DataComponentsAS.CRYSTAL_ATTRIBUTES).equals(attributes), "Planning must not mutate input");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void noLumenAndWrongLumenCannotConsumeInput(GameTestHelper helper) {
        var machine = machine(helper);
        var input = crystal(ModContent.ASTRAL_FLUIX_CRYSTAL.toStack(), 2, 2, 2);
        machine.getInventory().setStackInSlot(0, input.copy());
        helper.assertTrue(machine.getLumenHandler().fill(LumenAS.AEVITAS.stack(500), ILumenHandler.Action.EXECUTE) == 0,
                "The machine must reject the wrong lumen type");
        tick(machine, 80);
        helper.assertTrue(ItemStack.matches(input, machine.getInventory().getStackInSlot(0))
                && machine.getProgress() == 0 && machine.getInventory().getStackInSlot(1).isEmpty(),
                "An unpowered machine must not consume or process input");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void exactLumenCostProducesTwoHalvesOnlyOnce(GameTestHelper helper) {
        var machine = machine(helper);
        machine.getInventory().setStackInSlot(0, crystal(ModContent.ASTRAL_FLUIX_CRYSTAL.toStack(), 2, 2, 2));
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(25), ILumenHandler.Action.EXECUTE);
        tick(machine, 39);
        helper.assertTrue(machine.getLumenAmount() == 25 && !machine.getInventory().getStackInSlot(0).isEmpty(),
                "No material or lumen may be consumed before completion");
        tick(machine, 1);
        helper.assertTrue(machine.getLumenAmount() == 0 && machine.getInventory().getStackInSlot(0).isEmpty(),
                "Exactly one crystal and 25 lumen must be consumed");
        helper.assertTrue(!machine.getInventory().getStackInSlot(1).isEmpty()
                && !machine.getInventory().getStackInSlot(2).isEmpty(), "Both halves must be stored");
        var first = machine.getInventory().getStackInSlot(1).copy();
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(1000), ILumenHandler.Action.EXECUTE);
        tick(machine, 120);
        helper.assertTrue(ItemStack.matches(first, machine.getInventory().getStackInSlot(1))
                && machine.getLumenAmount() == 1000, "Output crystals must not be processed recursively");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void fullOutputAndMinimalCrystalDoNotConsumeAnything(GameTestHelper helper) {
        var machine = machine(helper);
        machine.getInventory().setStackInSlot(0, crystal(ItemsAS.ROCK_CRYSTAL.toStack(), 2, 2, 2));
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(500), ILumenHandler.Action.EXECUTE);
        for (int slot = 1; slot < 9; slot++) machine.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        tick(machine, 80);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.OUTPUT_FULL
                && machine.getLumenAmount() == 500 && machine.getProgress() == 0, "One empty slot is insufficient for a split");
        for (int slot = 1; slot <= 9; slot++) machine.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
        var minimal = crystal(ItemsAS.ROCK_CRYSTAL.toStack(), 1, 0, 0);
        machine.getInventory().setStackInSlot(0, minimal.copy());
        tick(machine, 80);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.UNSPLITTABLE
                && machine.getLumenAmount() == 500 && ItemStack.matches(minimal, machine.getInventory().getStackInSlot(0)),
                "An unsplittable crystal must remain retrievable without spending lumen");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void sidedCapabilitiesRespectModeChangesAndSimulation(GameTestHelper helper) {
        var machine = machine(helper);
        var top = machine.getItemHandler(Direction.UP);
        var bottom = machine.getItemHandler(Direction.DOWN);
        var material = crystal(ItemsAS.ROCK_CRYSTAL.toStack(), 2, 2, 2);
        helper.assertTrue(top.insertItem(0, material, true).isEmpty() && machine.getInventory().getStackInSlot(0).isEmpty(),
                "Simulated insertion must not mutate the inventory");
        helper.assertTrue(!bottom.insertItem(0, material, false).isEmpty(), "Output must reject input");
        helper.assertTrue(!top.insertItem(1, material, false).isEmpty(), "Outputs must reject external insertion");
        top.insertItem(0, material, false);
        helper.assertTrue(top.extractItem(0, 1, false).isEmpty(), "Input faces cannot extract unprocessed material");
        machine.getInventory().setStackInSlot(1, ItemsAS.STARDUST.toStack());
        helper.assertTrue(!bottom.extractItem(1, 1, true).isEmpty()
                && !machine.getInventory().getStackInSlot(1).isEmpty(), "Simulated extraction must not mutate output");
        machine.cycleSide(Direction.DOWN, true); // output -> input
        helper.assertTrue(bottom.extractItem(1, 1, false).isEmpty(), "Cached capability must obey input-only mode immediately");
        machine.cycleSide(Direction.UP); // input -> output
        helper.assertTrue(!top.extractItem(1, 1, false).isEmpty(), "Cached capability must follow new output mode");
        for (var side : Direction.values()) {
            var mode = machine.getSideMode(side);
            for (int i = 0; i < 3; i++) machine.cycleSide(side);
            helper.assertTrue(mode == machine.getSideMode(side), "All six world directions must cycle independently");
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void saveLoadResumesProgressAndKeepsSideSettings(GameTestHelper helper) {
        var machine = machine(helper);
        machine.getInventory().setStackInSlot(0, crystal(ItemsAS.ROCK_CRYSTAL.toStack(), 2, 2, 2));
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(500), ILumenHandler.Action.EXECUTE);
        machine.cycleSide(Direction.EAST);
        tick(machine, 17);
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = new AutoChiselBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.getLumenAmount() == 500 && restored.getProgress() == 17
                && restored.getSideMode(Direction.EAST) == AutoChiselBlockEntity.SideMode.OUTPUT,
                "Reload must preserve energy, progress and side configuration");
        tick(restored, 23);
        helper.assertTrue(restored.getLumenAmount() == 475 && restored.getInventory().getStackInSlot(0).isEmpty()
                && !restored.getInventory().getStackInSlot(2).isEmpty(), "Restored work must complete exactly once");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void configuredFacesPullAndPushAdjacentChests(GameTestHelper helper) {
        var machine = machine(helper);
        helper.setBlock(POS.above(), Blocks.CHEST);
        helper.setBlock(POS.below(), Blocks.CHEST);
        var input = (ChestBlockEntity) helper.getBlockEntity(POS.above());
        var output = (ChestBlockEntity) helper.getBlockEntity(POS.below());
        input.setItem(0, crystal(ItemsAS.ROCK_CRYSTAL.toStack(), 2, 2, 2));
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(25), ILumenHandler.Action.EXECUTE);
        helper.runAfterDelay(55, () -> {
            int count = 0;
            for (int slot = 0; slot < output.getContainerSize(); slot++) count += output.getItem(slot).getCount();
            helper.assertTrue(input.isEmpty() && count == 2 && machine.getLumenAmount() == 0,
                    "The top face must pull one crystal and the bottom face must push both products");
            helper.succeed();
        });
    }

    private static AutoChiselBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.AUTO_CHISEL.get());
        return (AutoChiselBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = "wand_empty")
    public static void menuValidatesButtonsAndPartialShiftClickResetsInput(GameTestHelper helper) {
        var machine = machine(helper);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "chisel-test"));
        var center = machine.getBlockPos().getCenter();
        player.setPos(center.x, center.y, center.z);
        var menu = new AutoChiselMenu(0, player.getInventory(), machine);
        helper.assertTrue(menu.clickMenuButton(player, Direction.EAST.ordinal())
                && machine.getSideMode(Direction.EAST) == AutoChiselBlockEntity.SideMode.OUTPUT,
                "An owner near the machine can configure its sides");
        helper.assertTrue(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 17),
                "Out-of-range configuration packets must be rejected");
        machine.getInventory().setStackInSlot(0, new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 8));
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(100), ILumenHandler.Action.EXECUTE);
        tick(machine, 10);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.getInventory().setItem(8, new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 63));
        helper.assertTrue(!menu.quickMoveStack(player, 0).isEmpty()
                && machine.getInventory().getStackInSlot(0).getCount() == 7 && machine.getProgress() == 0,
                "Partial shift-click must transfer exactly one item and reset the old job");
        player.setPos(center.x + 20, center.y, center.z);
        helper.assertTrue(!menu.clickMenuButton(player, 0) && menu.quickMoveStack(player, 0).isEmpty(),
                "Remote players must not configure or extract from a stale menu");
        helper.assertTrue(!menu.clickMenuButton(player, AutoChiselMenu.MODE_BUTTON), "Remote mode changes must also be rejected");
        player.setPos(center.x, center.y, center.z);
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.MODE_BUTTON) && menu.isDroppedItemMode(),
                "Mode buttons must update server-backed menu data");
        helper.assertTrue(!menu.clickMenuButton(player, 7), "Removed direction packets must be rejected");
        helper.assertTrue(menu.clickMenuButton(player, AutoChiselMenu.ROUND_ROBIN_BUTTON) && menu.isRoundRobin(),
                "Round-robin buttons must update the persisted server setting");
        player.getInventory().clearContent();
        helper.succeed();
    }
    private static void tick(AutoChiselBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) machine.serverTick();
    }
    private static ItemStack crystal(ItemStack stack, int size, int purity, int cut) {
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES)
                .setAttributeTier(CrystalPropertiesAS.SIZE, size)
                .setAttributeTier(CrystalPropertiesAS.PURITY, purity)
                .setAttributeTier(CrystalPropertiesAS.CUT, cut));
        return stack;
    }
}
