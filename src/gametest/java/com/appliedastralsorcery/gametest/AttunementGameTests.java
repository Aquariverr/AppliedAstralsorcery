package com.appliedastralsorcery.gametest;

import com.appliedastralsorcery.ModConfig;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.attunement.AttunementProcessing;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import com.appliedastralsorcery.attunement.IridescentAttunementBlockEntity;
import hellfirepvp.astralsorcery.common.component.AttunedConstellationComponent;
import hellfirepvp.astralsorcery.common.constellation.level.LevelSkyHandler;
import hellfirepvp.astralsorcery.common.crystal.CrystalPropertyGenerator;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AttunementGameTests {
    private static final BlockPos CENTER = new BlockPos(10, 2, 10);

    private static IridescentAttunementBlockEntity build(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        AttunementLayout.structure().place(level, center);
        return (IridescentAttunementBlockEntity) level.getBlockEntity(center);
    }

    private static ConstellationRelayBlockEntity relay(GameTestHelper helper, int index) {
        return (ConstellationRelayBlockEntity) helper.getLevel().getBlockEntity(
                helper.absolutePos(CENTER).offset(AttunementLayout.STATIONS.get(index).offset()));
    }

    private static ItemStack crystal() {
        var stack = new ItemStack(ItemsAS.ROCK_CRYSTAL.get());
        var attributes = stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
        if (attributes != null) stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES,
                CrystalPropertyGenerator.generateRandomProperties(attributes));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Attunement test crystal"));
        return stack;
    }

    @GameTest(template = "attunement_test")
    public static void lumenDisplayReceivesInitialAndLiveUpdates(GameTestHelper helper) {
        var altar = build(helper);
        var registries = helper.getLevel().registryAccess();
        altar.hasStructure();
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1000), ILumenHandler.Action.EXECUTE);
        // Use a separate receiver to exercise the native initial-chunk and live-packet decode paths.
        var receiver = new IridescentAttunementBlockEntity(altar.getBlockPos(), altar.getBlockState());
        receiver.handleUpdateTag(altar.getUpdateTag(registries), registries);
        helper.assertTrue(receiver.getLumenAmount() == 1000, "Joining a loaded chunk must show its stored lumen");
        helper.assertTrue(receiver.getTileData().hasStructure(), "Lumen updates must retain native structure synchronization");
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        for (int remaining : new int[]{750, 0}) {
            altar.consumeLumen(altar.getLumenAmount() - remaining, false);
            receiver.onDataPacket(connection, (ClientboundBlockEntityDataPacket) altar.getUpdatePacket(), registries);
            helper.assertTrue(receiver.getLumenAmount() == remaining, "Live consumption must update the displayed amount");
            var display = receiver.getDisplayTooltip().orElseThrow().lumenComponent().getLumenDisplay();
            helper.assertTrue(display.size() == 1, "The native lumen bar must remain visible when empty");
            var stored = display.getFirst();
            helper.assertTrue(stored.lumen() == LumenAS.PRISMATIC.get() && stored.amount() == remaining
                            && stored.maxAmount() == IridescentAttunementBlockEntity.LUMEN_CAPACITY,
                    "Display must show Prismatic lumen with the correct amount and capacity");
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void daylightNeedsFuelAndRejectsInvalidInput(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        var relay = relay(helper, 0);
        var input = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, relay.getBlockPos(), null);
        helper.assertTrue(input != null, "Relay must expose an item handler");
        helper.assertTrue(input.insertItem(0, new ItemStack(Items.DIRT), false).is(Items.DIRT), "Reject non-attuneable items");
        var attuned = crystal();
        attuned.set(DataComponentsAS.ATTUNED_CONSTELLATION,
                new AttunedConstellationComponent(AttunementLayout.STATIONS.getFirst().constellation().get()));
        helper.assertFalse(input.insertItem(0, attuned, false).isEmpty(), "Reject already attuned items");
        helper.assertTrue(input.insertItem(0, crystal(), false).isEmpty(), "Accept unattuned crystals");
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(altar.hasStructure(), "Native platform plus twelve relays must form");
            helper.assertTrue(relay.getProgress() == 0 && !relay.isVisible(), "Daylight without fuel must pause and hide maps");
            helper.assertTrue(altar.getLumenAmount() == 0, "No fuel may be generated");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test", timeoutTicks = 650)
    public static void twelveParallelAttunementsPreserveComponents(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        var lumen = helper.getLevel().getCapability(ILumenHandler.BLOCK, altar.getBlockPos(), null);
        helper.assertTrue(lumen != null, "Altar must expose lumen capability");
        helper.assertTrue(lumen.fill(LumenAS.AEVITAS.stack(10), ILumenHandler.Action.EXECUTE) == 0,
                "Altar must reject non-prismatic lumen");
        lumen.fill(LumenAS.PRISMATIC.stack(16000), ILumenHandler.Action.EXECUTE);
        var input = crystal();
        for (int i = 0; i < 12; i++) relay(helper, i).getInventory().insertItem(0, input.copy(), false);
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(ModConfig.ATTUNEMENT_LUMEN_PER_CRAFT.get() == 50, "Default cost must be 50 Lm per attunement");
            helper.assertTrue(altar.getLumenAmount() == 16000, "Progress must not consume lumen before completion");
        });
        helper.succeedWhen(() -> {
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), item -> !item.getItem().isEmpty());
            helper.assertTrue(items.size() == 12, "Every relay must emit one attuned item; found " + items.size()
                    + ", progress=" + relay(helper, 0).getProgress() + ", fuel=" + altar.getLumenAmount()
                    + ", conversion=" + AttunementProcessing.attune(helper.getLevel(), input,
                            AttunementLayout.STATIONS.getFirst().constellation().get()));
            var constellations = new java.util.HashSet<>();
            for (var entity : items) {
                var output = entity.getItem();
                helper.assertTrue(output.is(ItemsAS.ATTUNED_ROCK_CRYSTAL.get()), "Native crystal item conversion must apply");
                helper.assertTrue(java.util.Objects.equals(output.get(DataComponentsAS.CRYSTAL_ATTRIBUTES),
                        input.get(DataComponentsAS.CRYSTAL_ATTRIBUTES)), "Crystal attributes must survive");
                helper.assertTrue(input.getHoverName().equals(output.getHoverName()), "Custom components must survive");
                constellations.add(output.get(DataComponentsAS.ATTUNED_CONSTELLATION).getConstellation().orElseThrow());
                helper.assertTrue(entity.getDeltaMovement().y > 0, "Products must launch upward");
            }
            helper.assertTrue(constellations.size() == 12, "Each clock position must bind a different constellation");
            helper.assertTrue(altar.getLumenAmount() == 16000 - 12 * ModConfig.ATTUNEMENT_LUMEN_PER_CRAFT.get(),
                    "Twelve completed attunements must consume exactly 600 Lm in total");
        });
    }

    @GameTest(template = "attunement_test")
    public static void storedPrismaticLumenRevealsAllStarMaps(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        helper.getLevel().setBlockAndUpdate(altar.getBlockPos().above(), Blocks.STONE.defaultBlockState());
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1), ILumenHandler.Action.EXECUTE);
        helper.runAtTickTime(5, () -> {
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                helper.assertTrue(relay.isVisible() && !relay.isWorking() && !relay.isUsingLumen(),
                        "One stored lumen must reveal every idle map during daylight and under a roof");
                var receiver = new ConstellationRelayBlockEntity(relay.getBlockPos(), relay.getBlockState());
                var registries = helper.getLevel().registryAccess();
                receiver.handleUpdateTag(relay.getUpdateTag(registries), registries);
                helper.assertTrue(receiver.isVisible() && receiver.getConstellation() == relay.getConstellation(),
                        "Each displayed star map must reach the client with its assigned constellation");
            }
            helper.assertTrue(altar.getLumenAmount() == 1, "Displaying star maps alone must not consume lumen");
            altar.consumeLumen(1, false);
        });
        helper.runAtTickTime(10, () -> {
            for (int i = 0; i < 12; i++) helper.assertFalse(relay(helper, i).isVisible(),
                    "Without stored lumen, daylight must hide the maps again");
            altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1), ILumenHandler.Action.EXECUTE);
        });
        helper.runAtTickTime(15, () -> {
            for (int i = 0; i < 12; i++) helper.assertTrue(relay(helper, i).isVisible(),
                    "Refilling the central altar must reveal all maps again");
            helper.getLevel().removeBlock(altar.getBlockPos().below(), false);
        });
        helper.runAtTickTime(20, () -> {
            for (int i = 0; i < 12; i++) helper.assertFalse(relay(helper, i).isVisible(),
                    "Stored lumen must not reveal maps on an incomplete structure");
            helper.assertTrue(altar.getLumenAmount() == 1, "Idle display changes must preserve stored lumen");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void incompleteStructureNeverConsumesFuel(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1000), ILumenHandler.Action.EXECUTE);
        relay(helper, 0).getInventory().insertItem(0, crystal(), false);
        var missing = relay(helper, 1).getBlockPos();
        helper.getLevel().removeBlock(missing, false);
        helper.runAtTickTime(10, () -> {
            helper.assertFalse(altar.hasStructure(), "Missing relay must invalidate structure");
            helper.assertTrue(altar.getLumenAmount() == 1000 && relay(helper, 0).getProgress() == 0,
                    "Incomplete structure must not process or spend fuel");
            helper.getLevel().setBlockAndUpdate(missing, ModContent.CONSTELLATION_RELAY.get().defaultBlockState());
            helper.getLevel().removeBlock(helper.absolutePos(CENTER).below(), false);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertFalse(altar.hasStructure(), "Missing floor must invalidate structure");
            helper.assertTrue(altar.getLumenAmount() == 1000, "Fuel cannot replace the floor");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test", timeoutTicks = 650)
    public static void blockedOutputRetainsInputAndEntireFee(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1000), ILumenHandler.Action.EXECUTE);
        var relay = relay(helper, 0);
        relay.getInventory().insertItem(0, crystal(), false);
        helper.getLevel().setBlockAndUpdate(relay.getBlockPos().above(), Blocks.STONE.defaultBlockState());
        helper.runAtTickTime(520, () -> {
            helper.assertTrue(relay.getProgress() == 499 && !relay.getInventory().getStackInSlot(0).isEmpty(),
                    "Blocked product must remain in the relay");
            helper.assertTrue(altar.getLumenAmount() == 1000, "Blocked output must retain the entire attunement fee");
            helper.getLevel().removeBlock(relay.getBlockPos().above(), false);
        });
        helper.runAtTickTime(530, () -> {
            helper.assertTrue(relay.getInventory().getStackInSlot(0).isEmpty() && altar.getLumenAmount() == 950,
                    "Cleared output must complete exactly once; progress=" + relay.getProgress()
                            + ", fuel=" + altar.getLumenAmount() + ", input=" + relay.getInventory().getStackInSlot(0));
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void saveReloadAndInputReplacement(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1000), ILumenHandler.Action.EXECUTE);
        var relay = relay(helper, 0);
        relay.getInventory().insertItem(0, crystal(), false);
        helper.runAtTickTime(20, () -> {
            int progress = relay.getProgress();
            helper.assertTrue(progress > 0, "Test requires an in-progress operation");
            var registry = helper.getLevel().registryAccess();
            var tag = relay.saveWithoutMetadata(registry);
            helper.assertTrue(tag.getBoolean("lumenRequired"), "Lumen-assisted progress must record its completion fee");
            relay.loadWithComponents(tag, registry);
            helper.assertTrue(relay.getProgress() == progress && !relay.getInventory().getStackInSlot(0).isEmpty(),
                    "Inventory and progress must survive NBT round trip");
            helper.assertTrue(relay.saveWithoutMetadata(registry).getBoolean("lumenRequired"),
                    "Pending completion fee must survive NBT round trip");
            int fuel = altar.getLumenAmount();
            altar.loadWithComponents(altar.saveWithoutMetadata(registry), registry);
            helper.assertTrue(altar.getLumenAmount() == fuel, "Lumen must survive NBT round trip");
            relay.getInventory().extractItem(0, 1, false);
            helper.assertTrue(relay.getProgress() == 0, "Removing input must reset progress");
            helper.assertFalse(relay.saveWithoutMetadata(registry).getBoolean("lumenRequired"),
                    "Removing input must clear the pending fee");
            relay.getInventory().insertItem(0, crystal(), false);
            helper.assertTrue(relay.getProgress() == 0, "New input cannot inherit progress");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void processingStatePausesAndResumesWithLumen(GameTestHelper helper) {
        helper.setDayTime(1000);
        var altar = build(helper);
        var relay = relay(helper, 0);
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(49), ILumenHandler.Action.EXECUTE);
        relay.getInventory().insertItem(0, crystal(), false);
        int[] pausedProgress = {0};
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(relay.getProgress() == 0 && !relay.isWorking() && relay.isVisible() && !relay.isUsingLumen(),
                    "Less than 50 Lm may display maps but must not start lumen-assisted attunement");
            var snapshot = relay.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(snapshot.getInt("progress") == 0 && !snapshot.getBoolean("working"),
                    "Client snapshot must preserve paused progress and processing state");
            helper.assertTrue(snapshot.getLong("progressTime") == helper.getLevel().getGameTime(),
                    "Animation snapshots must carry their server timestamp");
            altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1), ILumenHandler.Action.EXECUTE);
        });
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(relay.isWorking() && relay.getProgress() > 0 && relay.isVisible() && relay.isUsingLumen(),
                    "Refilling lumen must resume progress and lumen effects while displaying the map");
            helper.assertTrue(altar.getLumenAmount() == 50, "The entire fee must remain in the altar until completion");
            helper.assertTrue(relay.getUpdateTag(helper.getLevel().registryAccess()).getBoolean("usingLumen"),
                    "Client effects must distinguish lumen-powered attunement from natural visibility");
            pausedProgress[0] = relay.getProgress();
            altar.consumeLumen(1, false);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(relay.getProgress() == pausedProgress[0] && !relay.isWorking() && altar.getLumenAmount() == 49,
                    "Losing the required reserve must pause progress without spending or losing it");
            altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1), ILumenHandler.Action.EXECUTE);
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(relay.getProgress() > pausedProgress[0] && altar.getLumenAmount() == 50,
                    "Restoring 50 Lm must resume the same operation without a repeated charge");
            relay.getInventory().extractItem(0, 1, false);
            helper.assertTrue(!relay.isWorking() && !relay.isUsingLumen() && relay.getProgress() == 0,
                    "Removing input must stop effects immediately");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test", batch = "attunement_night", timeoutTicks = 650)
    public static void lumenAssistedCompletionKeepsFeeAcrossReloadAndSkyChanges(GameTestHelper helper) {
        helper.setDayTime(18000);
        var altar = build(helper);
        helper.getLevel().setBlockAndUpdate(altar.getBlockPos().above(), Blocks.STONE.defaultBlockState());
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(50), ILumenHandler.Action.EXECUTE);
        int[] selected = {-1}, pausedProgress = {0};
        helper.runAtTickTime(5, () -> {
            var sky = LevelSkyHandler.getContext(helper.getLevel()).orElseThrow();
            for (int i = 0; i < 12; i++) {
                if (sky.getConstellationHandler().isCurrentlyActive(AttunementLayout.STATIONS.get(i).constellation().get())) {
                    selected[0] = i;
                    relay(helper, i).getInventory().insertItem(0, crystal(), false);
                    return;
                }
            }
            helper.fail("Test requires a constellation in the current night sky");
        });
        helper.runAtTickTime(20, () -> {
            var relay = relay(helper, selected[0]);
            pausedProgress[0] = relay.getProgress();
            helper.assertTrue(pausedProgress[0] > 0 && altar.getLumenAmount() == 50,
                    "Covered altar must progress using lumen without charging before completion");
            var registries = helper.getLevel().registryAccess();
            relay.loadWithComponents(relay.saveWithoutMetadata(registries), registries);
            helper.getLevel().removeBlock(altar.getBlockPos().above(), false);
            altar.consumeLumen(50, false);
        });
        helper.runAtTickTime(25, () -> {
            var relay = relay(helper, selected[0]);
            helper.assertTrue(AttunementProcessing.isVisible(helper.getLevel(), altar.getBlockPos(), relay.getConstellation()),
                    "Removing the roof must restore the constellation's natural conditions");
            helper.assertTrue(relay.getProgress() == pausedProgress[0] && !relay.isWorking(),
                    "Reloading or restoring natural conditions must not erase an incurred completion fee");
            altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(50), ILumenHandler.Action.EXECUTE);
        });
        helper.succeedWhen(() -> {
            var outputs = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            helper.assertTrue(outputs.size() == 1, "One completed attunement must emit one product");
            helper.assertTrue(altar.getLumenAmount() == 0, "Completion must deduct exactly 50 Lm once");
            helper.assertTrue(relay(helper, selected[0]).getInventory().getStackInSlot(0).isEmpty(),
                    "Completion must consume exactly one input");
        });
    }

    @GameTest(template = "attunement_test", batch = "attunement_night", timeoutTicks = 650)
    public static void naturalConstellationsUseNoFuel(GameTestHelper helper) {
        helper.setDayTime(18000);
        var altar = build(helper);
        var activeConstellations = new java.util.HashSet<>();
        helper.runAtTickTime(5, () -> {
            var sky = LevelSkyHandler.getContext(helper.getLevel()).orElseThrow();
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                var constellation = AttunementLayout.STATIONS.get(i).constellation().get();
                boolean active = sky.getConstellationHandler().isCurrentlyActive(constellation);
                if (active) activeConstellations.add(constellation);
                helper.assertTrue(relay.isVisible() == active, "Only active nighttime constellations may show a map");
                relay.getInventory().insertItem(0, crystal(), false);
            }
        });
        helper.runAtTickTime(20, () -> {
            boolean any = false;
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                boolean active = AttunementProcessing.isVisible(helper.getLevel(), altar.getBlockPos(), relay.getConstellation());
                helper.assertTrue((relay.getProgress() > 0) == active, "Only visible constellations may process without fuel");
                any |= active;
            }
            helper.assertTrue(any && altar.getLumenAmount() == 0, "At least one constellation must progress naturally for free");
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(activeConstellations.isEmpty(), "Wait for the night's constellations");
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            helper.assertTrue(items.size() == activeConstellations.size(), "Every visible constellation must finish without any lumen");
            var completed = new java.util.HashSet<>();
            for (var item : items) {
                var attunement = item.getItem().get(DataComponentsAS.ATTUNED_CONSTELLATION);
                helper.assertTrue(attunement != null, "Every product must be attuned");
                completed.add(attunement.getConstellation().orElseThrow());
            }
            helper.assertTrue(completed.equals(activeConstellations), "Only currently visible constellations may finish without fuel");
            helper.assertTrue(altar.getLumenAmount() == 0, "Natural attunement must finish with an empty tank");
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                helper.assertTrue(relay.getInventory().getStackInSlot(0).isEmpty()
                                == activeConstellations.contains(relay.getConstellation()),
                        "Unseen constellations must keep their inputs while visible constellations consume theirs");
            }
        });
    }

    @GameTest(template = "attunement_test", batch = "attunement_night")
    public static void naturalAttunementPreservesStoredLumen(GameTestHelper helper) {
        helper.setDayTime(18000);
        var altar = build(helper);
        altar.getLumenHandler().fill(LumenAS.PRISMATIC.stack(1000), ILumenHandler.Action.EXECUTE);
        helper.runAtTickTime(5, () -> {
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                if (AttunementProcessing.isVisible(helper.getLevel(), altar.getBlockPos(), relay.getConstellation()))
                    relay.getInventory().insertItem(0, crystal(), false);
            }
        });
        helper.runAtTickTime(20, () -> {
            boolean working = false;
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                working |= relay.getProgress() > 0;
                helper.assertTrue(relay.isVisible() && !relay.isUsingLumen(),
                        "Stored lumen reveals every map, while naturally available inputs do not use lumen effects");
            }
            helper.assertTrue(working, "A naturally visible constellation must process");
            helper.assertTrue(altar.getLumenAmount() == 1000, "Natural conditions must take priority over stored lumen");
            altar.consumeLumen(1000, false);
        });
        helper.runAtTickTime(25, () -> {
            for (int i = 0; i < 12; i++) {
                var relay = relay(helper, i);
                boolean natural = AttunementProcessing.isVisible(helper.getLevel(), altar.getBlockPos(), relay.getConstellation());
                helper.assertTrue(relay.isVisible() == natural, "An empty tank must restore natural nighttime map visibility");
            }
            helper.succeed();
        });
    }
}
