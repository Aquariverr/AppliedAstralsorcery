package com.appliedastralsorcery.chisel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.entity.item.ItemEntityCrystal;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ChiselDropGameTests {
    private static final BlockPos POS = new BlockPos(5, 4, 5);

    @GameTest(template = "wand_empty")
    public static void crystalDropsBypassFullInventoryAndKeepNativeEntities(GameTestHelper helper) {
        var machine = machine(helper);
        for (int slot = 0; slot <= 9; slot++) machine.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
        var input = drop(helper, POS.above(), crystal(), 1);
        tick(machine, 40);
        var outputs = drops(helper, POS.below());
        helper.assertTrue(!input.isAlive() && machine.getLumenAmount() == 975 && outputs.size() == 2,
                "One successful split must consume one crystal and 25 lumen, spawning both halves at the output");
        for (var output : outputs) helper.assertTrue(output instanceof ItemEntityCrystal
                && output.getItem().is(ModContent.ASTRAL_FLUIX_CRYSTAL)
                && !output.getItem().get(DataComponentsAS.CRYSTAL_ATTRIBUTES).isEmpty(),
                "Both halves must retain native crystal entities, material and split attributes");
        for (int slot = 0; slot <= 9; slot++) helper.assertTrue(machine.getInventory().getStackInSlot(slot).is(Items.STONE)
                && machine.getInventory().getStackInSlot(slot).getCount() == 64, "Dropped-item processing must leave all internal slots untouched");
        helper.runAfterDelay(2, () -> {
            var area = new AABB(helper.absolutePos(POS.below())).inflate(0.25);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntityCrystal.class, area).size() == 2,
                    "Both products must survive custom-entity replacement ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void strictRangeAndInputFacesExcludeOtherDrops(GameTestHelper helper) {
        var machine = machine(helper);
        machine.cycleSide(Direction.EAST); // output-only cannot be a target
        var far = drop(helper, POS.above(2), crystal(), 1);
        var wrongSide = drop(helper, POS.east(), crystal(), 2);
        var boundary = drop(helper, POS.above(), crystal(), 3);
        boundary.setPos(boundary.position().add(0.51, 0, 0));
        tick(machine, 80);
        helper.assertTrue(machine.getLumenAmount() == 1000 && machine.getProgress() == 0
                && far.isAlive() && wrongSide.isAlive() && boundary.isAlive(), "Only centers within the selected one-block volume qualify");
        machine.cycleSide(Direction.EAST); // shared input/output can be a target
        tick(machine, 40);
        helper.assertTrue(!wrongSide.isAlive() && far.isAlive() && boundary.isAlive()
                && machine.getLumenAmount() == 975, "Changing face permissions must change the exact target volumes");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void roundRobinAlternatesEntitiesInsteadOfExhaustingFirstStack(GameTestHelper helper) {
        var machine = machine(helper);
        var first = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 5), 1);
        var second = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 5), 2);
        tick(machine, 80);
        helper.assertTrue(first.getItem().getCount() == 3 && second.getItem().getCount() == 5,
                "Without round-robin, keep processing the first stack");
        var newcomer = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 5), 0);
        tick(machine, 40);
        helper.assertTrue(first.getItem().getCount() == 2 && newcomer.getItem().getCount() == 5,
                "A newly arrived entity with an earlier sort key must not preempt the active stack");
        newcomer.discard();
        machine.toggleRoundRobin();
        tick(machine, 40);
        helper.assertTrue(first.getItem().getCount() == 1 && second.getItem().getCount() == 5, "Round-robin starts with the first eligible entity");
        tick(machine, 40);
        helper.assertTrue(first.getItem().getCount() == 1 && second.getItem().getCount() == 4, "The next operation must visit the second entity");
        tick(machine, 40);
        helper.assertTrue(!first.isAlive() && second.getItem().getCount() == 4, "The cursor must wrap fairly");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void noOutputBlockedOutputAndNoLumenKeepTargetUntouched(GameTestHelper helper) {
        var machine = machine(helper);
        var target = drop(helper, POS.above(), crystal(), 1);
        var original = target.getItem().copy();
        machine.cycleSide(Direction.DOWN, true); // make the only output input-only
        tick(machine, 50);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.NO_OUTPUT && machine.getLumenAmount() == 1000,
                "A missing output face must pause without spending energy");
        machine.cycleSide(Direction.DOWN);
        helper.setBlock(POS.below(), Blocks.STONE);
        tick(machine, 50);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.OUTPUT_BLOCKED && machine.getLumenAmount() == 1000,
                "A solid output block must pause work");
        helper.setBlock(POS.below(), Blocks.AIR);
        machine.getLumenHandler().drain(LumenAS.EVORSIO.get(), 1000, ILumenHandler.Action.EXECUTE);
        tick(machine, 50);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.NO_LUMEN && ItemStack.matches(original, target.getItem())
                && drops(helper, POS.below()).isEmpty(), "No source item or product may change without power");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void disappearedOrChangedTargetCannotTransferItsProgress(GameTestHelper helper) {
        var machine = machine(helper);
        var first = drop(helper, POS.above(), crystal(), 1);
        tick(machine, 30);
        first.discard();
        var second = drop(helper, POS.above(), crystal(), 2);
        tick(machine, 10);
        helper.assertTrue(second.isAlive() && machine.getProgress() == 10 && machine.getLumenAmount() == 1000,
                "A new entity must start a new job rather than inherit 30 ticks");
        second.setItem(new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 3));
        tick(machine, 10);
        helper.assertTrue(machine.getProgress() == 10 && second.getItem().getCount() == 3, "Changing the same entity's stack resets its job");
        second.setPos(helper.absolutePos(POS.east(2)).getCenter());
        tick(machine, 40);
        helper.assertTrue(machine.getProgress() == 0 && machine.getLumenAmount() == 1000, "Targets leaving range must not be processed remotely");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void saveLoadPreservesDropJobAndPollingCursor(GameTestHelper helper) {
        var machine = machine(helper);
        machine.toggleRoundRobin();
        var first = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 1);
        var second = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 2);
        tick(machine, 40);
        var restored = reload(helper, machine);
        helper.assertTrue(restored.isDroppedItemMode() && restored.isRoundRobin()
                        && restored.getSideMode(Direction.UP).allowsInput(),
                "Mode, input faces and round-robin selection must survive reload");
        tick(restored, 17);
        var resumed = reload(helper, restored);
        tick(resumed, 23);
        helper.assertTrue(first.getItem().getCount() == 3 && second.getItem().getCount() == 3 && resumed.getLumenAmount() == 950,
                "Polling cursor and target-specific partial progress must survive reload without duplicating operations");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void multipleOutputsKeepPriorityAndSkipBlockedFaces(GameTestHelper helper) {
        var machine = machine(helper);
        machine.cycleSide(Direction.EAST);
        var target = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 1);
        tick(machine, 40);
        helper.assertTrue(!drops(helper, POS.below()).isEmpty() && drops(helper, POS.east()).isEmpty(), "First operation uses the first output face");
        tick(machine, 40);
        helper.assertTrue(drops(helper, POS.east()).isEmpty(), "Every operation must keep using the highest-priority output");
        for (var item : drops(helper, POS.below())) item.discard();
        helper.setBlock(POS.below(), Blocks.STONE);
        int before = drops(helper, POS.east()).size();
        tick(machine, 40);
        helper.assertTrue(target.getItem().getCount() == 1 && drops(helper, POS.east()).size() > before,
                "A blocked face must be skipped when another output is usable");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void cancelledSecondProductRollsBackFirstWithoutLosingInput(GameTestHelper helper) {
        var machine = machine(helper);
        var target = drop(helper, POS.above(), crystal(), 1);
        var original = target.getItem().copy();
        var count = new AtomicInteger();
        var outputArea = new AABB(helper.absolutePos(POS.below()));
        Consumer<EntityJoinLevelEvent> cancelSecond = event -> {
            if (event.getEntity() instanceof ItemEntity && outputArea.contains(event.getEntity().position())
                    && count.incrementAndGet() == 2) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancelSecond);
        try {
            tick(machine, 40);
        } finally {
            NeoForge.EVENT_BUS.unregister(cancelSecond);
        }
        helper.assertTrue(count.get() == 2 && drops(helper, POS.below()).isEmpty()
                && ItemStack.matches(original, target.getItem()) && machine.getLumenAmount() == 1000,
                "Failure to spawn either product must roll back all drops and retain the input and energy");
        tick(machine, 1);
        helper.assertTrue(!target.isAlive() && drops(helper, POS.below()).size() == 2 && machine.getLumenAmount() == 975,
                "Retry must produce one pair of products and charge once");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 80)
    public static void dropModePausesInventoryAutomationAndRejectsStaleInputHandlers(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.AUTO_CHISEL.get());
        var machine = (AutoChiselBlockEntity) helper.getBlockEntity(POS);
        var cached = machine.getItemHandler(Direction.UP);
        machine.toggleDroppedItemMode();
        helper.setBlock(POS.above(), Blocks.CHEST);
        var chest = (ChestBlockEntity) helper.getBlockEntity(POS.above());
        chest.setItem(0, crystal());
        helper.assertTrue(!cached.insertItem(0, crystal(), false).isEmpty(), "A cached input handler must respect dropped-item mode");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!chest.isEmpty() && machine.getInventory().getStackInSlot(0).isEmpty(), "Dropped-item mode must not pull adjacent inventories");
            machine.toggleDroppedItemMode();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(chest.isEmpty() && !machine.getInventory().getStackInSlot(0).isEmpty(), "Inventory automation must resume after switching back");
                helper.succeed();
            });
        });
    }

    private static AutoChiselBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.AUTO_CHISEL.get());
        var machine = (AutoChiselBlockEntity) helper.getBlockEntity(POS);
        machine.toggleDroppedItemMode();
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(1000), ILumenHandler.Action.EXECUTE);
        return machine;
    }

    @GameTest(template = "wand_empty")
    public static void roundRobinVisitsAllInputFaces(GameTestHelper helper) {
        var machine = machine(helper);
        machine.cycleSide(Direction.EAST, true); // none
        machine.cycleSide(Direction.EAST, true); // input/output
        machine.toggleRoundRobin();
        var first = drop(helper, POS.above(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 1);
        var second = drop(helper, POS.north(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 2);
        var third = drop(helper, POS.east(), new ItemStack(ItemsAS.STARMETAL_INGOT.get(), 4), 3);
        tick(machine, 120);
        helper.assertTrue(first.getItem().getCount() == 3 && second.getItem().getCount() == 3
                && third.getItem().getCount() == 3 && machine.getLumenAmount() == 925,
                "Polling must visit distinct entities across all input and shared faces");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void outputPriorityIsDownUpNorthSouthWestEast(GameTestHelper helper) {
        var machine = machine(helper);
        for (var side : Direction.values()) {
            while (machine.getSideMode(side) != AutoChiselBlockEntity.SideMode.INPUT_OUTPUT) machine.cycleSide(side);
        }
        for (var expected : List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            var target = drop(helper, POS.east(), crystal(), 1);
            tick(machine, 40);
            helper.assertTrue(!target.isAlive() && drops(helper, POS.relative(expected)).size() == 2,
                    "Expected output at " + expected + " after blocking every higher-priority face");
            for (var product : drops(helper, POS.relative(expected))) product.discard();
            helper.setBlock(POS.relative(expected), Blocks.STONE);
        }
        helper.assertTrue(machine.getLumenAmount() == 850, "Six completed operations must cost 150 Lm");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void removingInputPermissionResetsTargetAndIgnoresLegacyDirection(GameTestHelper helper) {
        var machine = machine(helper);
        var target = drop(helper, POS.north(), crystal(), 1);
        tick(machine, 30);
        machine.cycleSide(Direction.NORTH);
        tick(machine, 40);
        helper.assertTrue(target.isAlive() && machine.getProgress() == 0 && machine.getLumenAmount() == 1000,
                "An output-only face must stop being a chisel input immediately");
        machine.cycleSide(Direction.NORTH); // shared face
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.putInt("dropDirection", Direction.UP.ordinal());
        saved.putInt("outputCursor", Direction.EAST.ordinal());
        machine.loadWithComponents(saved, helper.getLevel().registryAccess());
        tick(machine, 40);
        helper.assertTrue(!target.isAlive() && drops(helper, POS.below()).size() == 2,
                "Legacy direction and output cursors must not override the configured faces or fixed priority");
        helper.succeed();
    }

    private static AutoChiselBlockEntity reload(GameTestHelper helper, AutoChiselBlockEntity machine) {
        var restored = new AutoChiselBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(machine.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        return restored;
    }

    @GameTest(template = "wand_empty")
    public static void noneFacesExcludeDropTargetsAndOutputs(GameTestHelper helper) {
        var machine = machine(helper);
        var target = drop(helper, POS.above(), crystal(), 1);
        tick(machine, 30);
        machine.cycleSide(Direction.UP, true); // input -> none
        tick(machine, 40);
        helper.assertTrue(target.isAlive() && machine.getProgress() == 0 && machine.getLumenAmount() == 1000,
                "Disabling an input face must reset work and exclude its dropped items");
        machine.cycleSide(Direction.DOWN);
        machine.cycleSide(Direction.DOWN); // output -> both -> none
        machine.cycleSide(Direction.UP); // none -> input
        tick(machine, 40);
        helper.assertTrue(machine.getStatus() == AutoChiselBlockEntity.Status.NO_OUTPUT && target.isAlive(),
                "None must not count as an output face");
        machine.cycleSide(Direction.EAST); // input -> output
        tick(machine, 40);
        helper.assertTrue(!target.isAlive() && drops(helper, POS.below()).isEmpty()
                && drops(helper, POS.east()).size() == 2 && machine.getLumenAmount() == 975,
                "Outputs must skip disabled faces and use the next configured output");
        helper.succeed();
    }

    private static ItemStack crystal() {
        var stack = ModContent.ASTRAL_FLUIX_CRYSTAL.toStack();
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES)
                .setAttributeTier(CrystalPropertiesAS.SIZE, 2).setAttributeTier(CrystalPropertiesAS.PURITY, 2)
                .setAttributeTier(CrystalPropertiesAS.CUT, 2));
        return stack;
    }

    private static ItemEntity drop(GameTestHelper helper, BlockPos pos, ItemStack stack, int order) {
        var center = helper.absolutePos(pos).getCenter();
        var base = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, stack);
        var entity = (ItemEntity) stack.getItem().createEntity(helper.getLevel(), base, stack);
        entity.setUUID(new UUID(helper.absolutePos(POS).asLong(), order));
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static List<ItemEntity> drops(GameTestHelper helper, BlockPos pos) {
        var bounds = new AABB(helper.absolutePos(pos));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.isAlive() && bounds.contains(entity.position()));
    }

    private static void tick(AutoChiselBlockEntity machine, int ticks) {
        for (int i = 0; i < ticks; i++) machine.serverTick();
    }
}
