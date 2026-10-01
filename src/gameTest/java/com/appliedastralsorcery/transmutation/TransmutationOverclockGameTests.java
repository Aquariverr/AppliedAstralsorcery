package com.appliedastralsorcery.transmutation;

import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.lumen.LumenKey;
import com.mojang.authlib.GameProfile;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationOverclockGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void eachOperationCosts25AionAndTakesExactly20Ticks(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            insert(machine, LumenKey.of(LumenAS.AION.get()), 50);
            machine.toggleLumenOverclock();
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD, 3));
            tick(helper, machine, 19);
            helper.assertTrue(machine.getProgress() == 19 && machine.getDuration() == 20
                    && machine.getInventory().getStackInSlot(9).isEmpty() && lumen(machine) == 25,
                    "The first operation must charge exactly 25 Aion once and not finish before tick 20");
            tick(helper, machine, 1);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 1
                    && machine.getInventory().getStackInSlot(0).getCount() == 2 && lumen(machine) == 25,
                    "The first operation must finish on tick 20");
            tick(helper, machine, 20);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 2
                    && machine.getInventory().getStackInSlot(0).getCount() == 1 && lumen(machine) == 0,
                    "The next operation must separately charge 25 Aion and finish after another 20 ticks");
            tick(helper, machine, 79);
            helper.assertTrue(machine.getProgress() == 79 && machine.getDuration() == 80
                    && machine.getInventory().getStackInSlot(9).getCount() == 2,
                    "An empty lumen cell must fall back to the full original recipe duration");
            tick(helper, machine, 1);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 3
                    && machine.getInventory().getStackInSlot(0).isEmpty(), "Fallback must finish without lumen");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void insufficientAionFallsBackWithoutConsumingLumenOrChangingAnActiveJob(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            insert(machine, LumenKey.of(LumenAS.AION.get()), 24);
            insert(machine, LumenKey.of(LumenAS.EVORSIO.get()), 100);
            machine.toggleLumenOverclock();
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD, 2));
            tick(helper, machine, 20);
            helper.assertTrue(machine.getProgress() == 20 && machine.getDuration() == 80 && lumen(machine) == 24,
                    "24 Aion and other lumen must run at the native duration without draining anything");
            insert(machine, LumenKey.of(LumenAS.AION.get()), 1);
            tick(helper, machine, 59);
            helper.assertTrue(machine.getProgress() == 79 && machine.getDuration() == 80
                    && lumen(machine) == 25 && machine.getInventory().getStackInSlot(9).isEmpty(),
                    "Replenishing lumen must not change or charge an already running normal operation");
            tick(helper, machine, 1);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 1 && lumen(machine) == 25,
                    "Fallback must finish at the original recipe duration");
            tick(helper, machine, 20);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 2 && lumen(machine) == 0,
                    "The next operation must use newly available Aion");
            helper.assertTrue(machine.getMainNode().getGrid().getStorageService().getInventory().extract(
                    LumenKey.of(LumenAS.EVORSIO.get()), 100, Actionable.SIMULATE, IActionSource.empty()) == 100,
                    "Other lumen types must remain untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void disabledByDefaultAndSwitchAppliesToNextOperation(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            insert(machine, LumenKey.of(LumenAS.AION.get()), 50);
            helper.assertTrue(!machine.isLumenOverclock(), "Overclock must default to off");
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD, 2));
            tick(helper, machine, 1);
            machine.toggleLumenOverclock();
            tick(helper, machine, 78);
            helper.assertTrue(machine.getProgress() == 79 && machine.getDuration() == 80 && lumen(machine) == 50,
                    "Enabling overclock during a normal operation must not shorten or charge that operation");
            tick(helper, machine, 2);
            helper.assertTrue(machine.getProgress() == 1 && machine.getDuration() == 20 && lumen(machine) == 25,
                    "The next operation must start overclocked");
            machine.toggleLumenOverclock();
            tick(helper, machine, 19);
            helper.assertTrue(machine.getInventory().getStackInSlot(9).getCount() == 2 && lumen(machine) == 25,
                    "Turning off must not undo a paid operation or charge it again");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void blockedUnlitAndOfflineChambersDoNotCharge(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            insert(machine, LumenKey.of(LumenAS.AION.get()), 50);
            machine.toggleLumenOverclock();
            tick(helper, machine, 1);
            helper.assertTrue(lumen(machine) == 50, "Idle chambers must not consume lumen");
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD));
            for (int slot = 9; slot < 18; slot++) machine.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            tick(helper, machine, 30);
            helper.assertTrue(machine.getProgress() == 0 && lumen(machine) == 50,
                    "Blocked output must not prepay an operation");
            // Allow the beam to expire before freeing output space.
            helper.runAfterDelay(8, () -> {
                machine.getInventory().setStackInSlot(9, ItemStack.EMPTY);
                machine.serverTick(helper.getLevel());
                helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT
                        && lumen(machine) == 50, "Missing starlight must not consume lumen");
                machine.receiveStarlight(helper.getLevel(), new StarlightTransmissionPacket(ConstellationsAS.AEVITAS.get(), 10));
                machine.serverTick(helper.getLevel());
                helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.WRONG_CONSTELLATION
                        && lumen(machine) == 50, "The wrong constellation must not consume lumen");
                helper.setBlock(POS.below(), Blocks.AIR);
                helper.runAfterDelay(15, () -> {
                    tick(helper, machine, 30);
                    helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.OFFLINE
                            && machine.getProgress() == 0,
                            "Offline networks must not prepay or advance operations");
                    // Read the cell directly: the offline grid deliberately exposes no storage.
                    var cell = ((DriveBlockEntity) helper.getBlockEntity(POS.east())).getCellInventory(0);
                    helper.assertTrue(cell != null && cell.getAvailableStacks().get(LumenKey.of(LumenAS.AION.get())) == 50,
                            "Power loss must leave all lumen in the cell");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void paidJobSurvivesLightLossAndReloadWithoutChargingAgain(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            insert(machine, LumenKey.of(LumenAS.AION.get()), 50);
            machine.toggleLumenOverclock();
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.ECHO_SHARD));
            tick(helper, machine, 5);
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT
                        && lumen(machine) == 25, "Light loss must pause an already paid operation");
                int savedProgress = machine.getProgress();
                var registries = helper.getLevel().registryAccess();
                var saved = machine.saveWithoutMetadata(registries);
                helper.setBlock(POS, Blocks.AIR);
                helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
                var restored = (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
                restored.loadWithComponents(saved, registries);
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(restored.isLumenOverclock() && restored.getProgress() == savedProgress,
                            "Save/load must preserve overclock configuration and paused progress");
                    tick(helper, restored, 19 - savedProgress);
                    helper.assertTrue(restored.getDuration() == 20 && restored.getProgress() == 19 && lumen(restored) == 25,
                            "Restoring a paid job must retain its 20-tick duration without another payment");
                    tick(helper, restored, 1);
                    helper.assertTrue(restored.getInventory().getStackInSlot(9).getCount() == 1 && lumen(restored) == 25,
                            "The restored operation must finish exactly once");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "wand_empty")
    public static void menuToggleIsValidatedSyncedAndPersisted(GameTestHelper helper) {
        var machine = setup(helper);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "overclock"));
        player.setPos(machine.getBlockPos().getCenter());
        var menu = new StarlightTransmutationMenu(1, player.getInventory(), machine);
        var mirror = new StarlightTransmutationMenu(1, player.getInventory());
        menu.addSlotListener(new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) {}
            @Override public void dataChanged(AbstractContainerMenu changed, int slot, int value) { mirror.setData(slot, value); }
        });
        helper.assertTrue(menu.clickMenuButton(player, StarlightTransmutationMenu.OVERCLOCK_BUTTON)
                && machine.isLumenOverclock() && mirror.isLumenOverclock(), "The server toggle must sync to the client");
        var restored = new StarlightTransmutationBlockEntity(machine.getBlockPos(), machine.getBlockState());
        var registries = helper.getLevel().registryAccess();
        restored.loadWithComponents(machine.saveWithoutMetadata(registries), registries);
        helper.assertTrue(restored.isLumenOverclock(), "The switch must survive save/load");
        restored.loadWithComponents(new CompoundTag(), registries);
        helper.assertTrue(!restored.isLumenOverclock(), "Old saves must default to disabled");
        helper.assertTrue(!menu.clickMenuButton(player, 99) && machine.isLumenOverclock(), "Unknown buttons must be ignored");
        player.setPos(machine.getBlockPos().getCenter().add(30, 0, 0));
        helper.assertTrue(!menu.clickMenuButton(player, StarlightTransmutationMenu.OVERCLOCK_BUTTON)
                && machine.isLumenOverclock(), "Out-of-range players must not toggle overclock");
        player.setPos(machine.getBlockPos().getCenter());
        helper.assertTrue(menu.clickMenuButton(player, StarlightTransmutationMenu.OVERCLOCK_BUTTON)
                && !mirror.isLumenOverclock(), "The switch must also turn off and sync");
        helper.succeed();
    }

    private static StarlightTransmutationBlockEntity setup(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.east(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) helper.getBlockEntity(POS.east())).getInternalInventory()
                .setItemDirect(0, ModContent.LUMEN_CELL.toStack());
        return (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
    }

    private static void insert(StarlightTransmutationBlockEntity machine, LumenKey key, int amount) {
        long inserted = machine.getMainNode().getGrid().getStorageService().getInventory()
                .insert(key, amount, Actionable.MODULATE, IActionSource.empty());
        if (inserted != amount) throw new AssertionError("Test ME storage did not accept lumen");
    }

    private static long lumen(StarlightTransmutationBlockEntity machine) {
        return machine.getMainNode().getGrid().getStorageService().getInventory()
                .extract(LumenKey.of(LumenAS.AION.get()), 1000, Actionable.SIMULATE, IActionSource.empty());
    }

    private static void tick(GameTestHelper helper, StarlightTransmutationBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) {
            machine.receiveStarlight(helper.getLevel(), new StarlightTransmissionPacket(ConstellationsAS.MINERALIS.get(), 10));
            machine.serverTick(helper.getLevel());
        }
    }
}
