package com.appliedastralsorcery.transmutation;

import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import com.mojang.authlib.GameProfile;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationPullGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void pullRespectsTargetsComponentsExistingItemsAndOfflineMode(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            var iron = new ItemStack(Items.RAW_IRON);
            var named = iron.copy();
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact component marker"));
            storage.insert(AEItemKey.of(iron), 20, Actionable.MODULATE, IActionSource.empty());
            storage.insert(AEItemKey.of(named), 5, Actionable.MODULATE, IActionSource.empty());
            machine.setPullMarker(0, iron.copyWithCount(3));
            machine.setPullMarker(1, named.copyWithCount(2));
            machine.setPullMarker(2, iron.copyWithCount(4));
            machine.setPullMarker(3, iron.copyWithCount(3));
            machine.getInventory().setStackInSlot(3, new ItemStack(Items.AMETHYST_SHARD));
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(), "Manual mode must not pull marked items");
            machine.toggleAutoPull();
            machine.serverTick(helper.getLevel());
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 3
                    && ItemStack.isSameItemSameComponents(machine.getInventory().getStackInSlot(1), named)
                    && machine.getInventory().getStackInSlot(1).getCount() == 2
                    && machine.getInventory().getStackInSlot(2).getCount() == 4,
                    "Pulls must honor each slot's target and exact components");
            helper.assertTrue(machine.getInventory().getStackInSlot(3).is(Items.RAW_IRON)
                    && machine.getInventory().getStackInSlot(3).getCount() == 3
                    && storage.extract(AEItemKey.of(Items.AMETHYST_SHARD), 10, Actionable.SIMULATE, IActionSource.empty()) == 1,
                    "Like an ME interface, return a different item before restocking the requested one");
            helper.assertTrue(storage.extract(AEItemKey.of(iron), 100, Actionable.SIMULATE, IActionSource.empty()) == 10
                    && storage.extract(AEItemKey.of(named), 100, Actionable.SIMULATE, IActionSource.empty()) == 3,
                    "Network extraction must exactly equal material added to inputs, without over-pulling");
            machine.toggleAutoPull();
            machine.getInventory().extractItem(0, 1, false);
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 2, "Turning off must immediately stop refilling");
            helper.setBlock(POS.below(), Blocks.AIR);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(!machine.getMainNode().isOnline(), "Test network must be offline");
                machine.toggleAutoPull();
                machine.serverTick(helper.getLevel());
                helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 2, "Offline auto pull must not withdraw items");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void repeatedTransmutationRefillsWithoutConsumingMarkers(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            storage.insert(AEItemKey.of(Items.AMETHYST_SHARD), 2, Actionable.MODULATE, IActionSource.empty());
            machine.setPullMarker(0, new ItemStack(Items.AMETHYST_SHARD));
            machine.toggleAutoPull();
            for (int i = 0; i < 45; i++) {
                machine.receiveStarlight(helper.getLevel(), new StarlightTransmissionPacket(ConstellationsAS.MINERALIS.get(), 10));
                machine.serverTick(helper.getLevel());
            }
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty()
                    && machine.getPullMarkers().getStackInSlot(0).getCount() == 1, "Only real stock may be consumed; exhausted ME must not materialize markers");
            helper.assertTrue(storage.extract(AEItemKey.of(Items.QUARTZ), 100, Actionable.SIMULATE, IActionSource.empty()) == 4
                    && storage.extract(AEItemKey.of(Items.DIAMOND), 100, Actionable.SIMULATE, IActionSource.empty()) == 6,
                    "Two automatically supplied jobs must produce exactly two recipe outputs");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void markerClicksAndPacketsAreNonConsumptiveValidatedAndSynced(GameTestHelper helper) {
        var machine = setup(helper);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "transmutation-pull"));
        player.setPos(machine.getBlockPos().getCenter());
        var menu = new StarlightTransmutationMenu(12, player.getInventory(), machine);
        player.containerMenu = menu;
        var mirror = new StarlightTransmutationMenu(12, player.getInventory());
        menu.addSlotListener(new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) { mirror.getSlot(slot).set(stack.copy()); }
            @Override public void dataChanged(AbstractContainerMenu changed, int slot, int value) { mirror.setData(slot, value); }
        });
        helper.assertTrue(menu.clickMenuButton(player, StarlightTransmutationMenu.AUTO_PULL_BUTTON) && mirror.isAutoPull(), "Mode must sync to the viewing menu");
        menu.setCarried(new ItemStack(Items.RAW_IRON, 5));
        menu.clicked(StarlightTransmutationMenu.MARKER_SLOT_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 5 && machine.getInventory().getStackInSlot(0).isEmpty()
                && mirror.getPullMarker(0).getCount() == 5, "Marking must copy and synchronize without consuming or inserting actual items");
        for (var type : ClickType.values()) {
            if (type != ClickType.PICKUP) menu.clicked(StarlightTransmutationMenu.MARKER_SLOT_START, 0, type, player);
        }
        helper.assertTrue(menu.getPullMarker(0).getCount() == 5 && menu.getCarried().getCount() == 5
                && menu.quickMoveStack(player, StarlightTransmutationMenu.MARKER_SLOT_START).isEmpty(),
                "Shift-click, clone, throw and drag must never turn markers into real items");
        menu.clicked(StarlightTransmutationMenu.MARKER_SLOT_START, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getPullMarker(0).getCount() == 10 && menu.getCarried().getCount() == 5,
                "Repeated left-click must add the held quantity like an ME interface");
        menu.clicked(StarlightTransmutationMenu.MARKER_SLOT_START, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getPullMarker(0).getCount() == 5, "Right-click must subtract the held quantity");
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 2));
        helper.assertTrue(menu.getSlot(0).isActive() && menu.getSlot(0).mayPickup(player)
                && !menu.quickMoveStack(player, 0).isEmpty() && machine.getInventory().getStackInSlot(0).isEmpty(),
                "Actual input stock must remain accessible while automatic stocking is enabled");
        helper.assertTrue(!new TransmutationFilterSelection(13, 0, new ItemStack(Items.RAW_IRON)).apply(player)
                && !new TransmutationFilterSelection(12, 9, new ItemStack(Items.RAW_IRON)).apply(player)
                && !new TransmutationFilterSelection(12, 0, new ItemStack(Items.BEDROCK)).apply(player),
                "Wrong containers, invalid slots and non-input items must be rejected");
        helper.assertTrue(new TransmutationFilterSelection(12, 1, new ItemStack(Items.RAW_IRON, 99)).apply(player)
                && mirror.getPullMarker(1).getCount() == 64, "JEI marker requests must clamp and sync counts");
        menu.clicked(StarlightTransmutationMenu.MARKER_SLOT_START, 1, ClickType.PICKUP, player);
        helper.assertTrue(mirror.getPullMarker(0).isEmpty() && menu.getCarried().getCount() == 5, "Clearing must not modify the cursor");
        menu.clickMenuButton(player, StarlightTransmutationMenu.AUTO_PULL_BUTTON);
        helper.assertTrue(!new TransmutationFilterSelection(12, 1, ItemStack.EMPTY).apply(player)
                && menu.getSlot(0).isActive() && !menu.getSlot(StarlightTransmutationMenu.MARKER_SLOT_START).isActive()
                && mirror.getPullMarker(1).getCount() == 64, "Manual mode must restore real slots and retain read-only markers");
        player.setPos(machine.getBlockPos().getCenter().add(20, 0, 0));
        helper.assertTrue(!menu.clickMenuButton(player, StarlightTransmutationMenu.AUTO_PULL_BUTTON), "Remote configuration must be rejected");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void interfaceStockingReturnsExcessClearedAndChangedTargets(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            machine.setPullMarker(0, new ItemStack(Items.RAW_IRON, 4));
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 9));
            machine.getInventory().setStackInSlot(1, new ItemStack(Items.AMETHYST_SHARD, 5));
            machine.toggleAutoPull();
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 4
                    && machine.getInventory().getStackInSlot(1).isEmpty(), "Return surplus and all stock in unconfigured slots");
            helper.assertTrue(storage.extract(AEItemKey.of(Items.RAW_IRON), 100, Actionable.SIMULATE, IActionSource.empty()) == 5
                    && storage.extract(AEItemKey.of(Items.AMETHYST_SHARD), 100, Actionable.SIMULATE, IActionSource.empty()) == 5,
                    "Every returned item must reach real ME storage");
            machine.setPullMarker(0, new ItemStack(Items.AMETHYST_SHARD, 3));
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(), "Changing type must return the old contents first");
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).is(Items.AMETHYST_SHARD)
                    && machine.getInventory().getStackInSlot(0).getCount() == 3, "Restock the new configuration after returning old contents");
            machine.setPullMarker(0, new ItemStack(Items.AMETHYST_SHARD));
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 1, "Lowering the target must return the difference");
            machine.setPullMarker(0, ItemStack.EMPTY);
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty()
                    && storage.extract(AEItemKey.of(Items.RAW_IRON), 100, Actionable.SIMULATE, IActionSource.empty()) == 9
                    && storage.extract(AEItemKey.of(Items.AMETHYST_SHARD), 100, Actionable.SIMULATE, IActionSource.empty()) == 5,
                    "Clearing the configuration must return all stock without loss or duplication");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void fullMEStorageKeepsItemsAndRetriesPartialReturns(GameTestHelper helper) {
        var machine = setup(helper);
        helper.runAfterDelay(25, () -> {
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            var key = AEItemKey.of(Items.RAW_IRON);
            long filled = storage.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
            helper.assertTrue(filled > 0 && storage.insert(key, 1, Actionable.SIMULATE, IActionSource.empty()) == 0, "Test cell must be full");
            machine.setPullMarker(0, new ItemStack(Items.RAW_IRON, 4));
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 9));
            machine.toggleAutoPull();
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 9, "A full network must never void unwanted stock");
            storage.extract(key, 2, Actionable.MODULATE, IActionSource.empty());
            machine.serverTick(helper.getLevel());
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 7, "Only the amount accepted by ME may leave the buffer");
            storage.extract(key, 3, Actionable.MODULATE, IActionSource.empty());
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 4, "Retry until stock reaches the target exactly");
            machine.toggleAutoPull();
            machine.setPullMarker(0, ItemStack.EMPTY);
            storage.extract(key, 4, Actionable.MODULATE, IActionSource.empty());
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 4, "Disabling must stop returns as well as imports");
            machine.toggleAutoPull();
            machine.serverTick(helper.getLevel());
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(), "Re-enabling must finish the pending return");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void pullSettingsPersistButMarkersNeverDropAsItems(GameTestHelper helper) {
        var machine = setup(helper);
        machine.setPullMarker(0, new ItemStack(Items.RAW_IRON, 7));
        machine.toggleAutoPull();
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 2));
        var registry = helper.getLevel().registryAccess();
        var restored = new StarlightTransmutationBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.loadWithComponents(machine.saveWithoutMetadata(registry), registry);
        helper.assertTrue(restored.isAutoPull() && restored.getPullMarkers().getStackInSlot(0).getCount() == 7
                && restored.getInventory().getStackInSlot(0).getCount() == 2, "Mode, marker target and actual stock must persist separately");
        machine.dropContents();
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(1)).stream()
                .filter(entity -> entity.getItem().is(Items.RAW_IRON)).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(dropped == 2, "Breaking must drop only real contents, not seven phantom items");
        restored.loadWithComponents(new CompoundTag(), registry);
        helper.assertTrue(!restored.isAutoPull() && restored.getPullMarkers().getStackInSlot(0).isEmpty(), "Legacy saves must default to manual mode without markers");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void markerAmountsAccumulateValidateAndNeverMoveRealItems(GameTestHelper helper) {
        var machine = setup(helper);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "marker-amounts"));
        player.setPos(machine.getBlockPos().getCenter());
        var menu = new StarlightTransmutationMenu(12, player.getInventory(), machine);
        player.containerMenu = menu;
        machine.toggleAutoPull();
        var item = new ItemStack(Items.RAW_IRON);
        machine.setPullMarker(0, item.copyWithCount(5));
        machine.getInventory().setStackInSlot(0, item.copyWithCount(3));
        menu.setCarried(item.copyWithCount(7));
        for (int i = 0; i < 10; i++) {
            helper.assertTrue(new TransmutationMarkerAmount(12, 0, item, 1, true).apply(player), "Accept relative scroll requests");
        }
        helper.assertTrue(menu.getPullMarker(0).getCount() == 15, "Rapid scrolls must accumulate without waiting for client synchronization");
        helper.assertTrue(new TransmutationMarkerAmount(12, 0, item, 42, false).apply(player)
                && menu.getPullMarker(0).getCount() == 42, "Amount editor sets an exact target");
        helper.assertTrue(new TransmutationMarkerAmount(12, 0, item, Integer.MAX_VALUE, true).apply(player)
                && menu.getPullMarker(0).getCount() == 64, "Oversized deltas must clamp without integer overflow");
        var differentComponents = item.copy();
        differentComponents.set(DataComponents.CUSTOM_NAME, Component.literal("Changed target"));
        helper.assertTrue(!new TransmutationMarkerAmount(13, 0, item, 10, false).apply(player)
                && !new TransmutationMarkerAmount(12, -1, item, 10, false).apply(player)
                && !new TransmutationMarkerAmount(12, 9, item, 10, false).apply(player)
                && !new TransmutationMarkerAmount(12, 0, ItemStack.EMPTY, 10, false).apply(player)
                && !new TransmutationMarkerAmount(12, 0, differentComponents, 10, false).apply(player),
                "Reject wrong menus, invalid slots and stale item/component identities");
        machine.setPullMarker(0, new ItemStack(Items.AMETHYST_SHARD));
        helper.assertTrue(!new TransmutationMarkerAmount(12, 0, item, 10, false).apply(player), "Old amount editors must not overwrite a different marker");
        machine.setPullMarker(0, item);
        machine.toggleAutoPull();
        helper.assertTrue(!new TransmutationMarkerAmount(12, 0, item, 10, false).apply(player), "Manual mode rejects quantity editing");
        machine.toggleAutoPull();
        player.setPos(machine.getBlockPos().getCenter().add(20, 0, 0));
        helper.assertTrue(!new TransmutationMarkerAmount(12, 0, item, 10, false).apply(player), "Out-of-range amount editors must be rejected");
        player.setPos(machine.getBlockPos().getCenter());
        helper.assertTrue(new TransmutationMarkerAmount(12, 0, item, 0, false).apply(player)
                && menu.getPullMarker(0).isEmpty(), "Zero clears the configuration");
        helper.assertTrue(!new TransmutationMarkerAmount(12, 0, item, 10, true).apply(player), "Scrolling an empty slot cannot recreate its former marker");
        helper.assertTrue(menu.getCarried().getCount() == 7 && machine.getInventory().getStackInSlot(0).getCount() == 3,
                "Quantity editing must not consume the cursor or change real stock directly");
        helper.succeed();
    }

    private static StarlightTransmutationBlockEntity setup(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.east(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) helper.getBlockEntity(POS.east())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        return (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
    }
}
