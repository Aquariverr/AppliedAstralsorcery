package com.appliedastralsorcery.chisel;

import com.appliedastralsorcery.ModContent;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class AutoChiselMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 1 + AutoChiselBlockEntity.OUTPUT_SLOTS;
    private static final int DATA_COUNT = 14;
    public static final int MODE_BUTTON = 6;
    // Button 7 and data slot 10 are reserved for the removed target-direction setting.
    public static final int ROUND_ROBIN_BUTTON = 8;
    public static final int AUTO_INPUT_BUTTON = 9;
    public static final int AUTO_OUTPUT_BUTTON = 10;
    public static final int REVERSE_SIDE_BASE = 11;
    private boolean configurationOpen;
    private final AutoChiselBlockEntity machine;
    private final Player owner;
    private final ContainerData data;

    public AutoChiselMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public AutoChiselMenu(int id, Inventory inventory, AutoChiselBlockEntity machine) {
        super(ModContent.AUTO_CHISEL_MENU.get(), id);
        this.machine = machine;
        owner = inventory.player;
        var items = machine == null ? new ItemStackHandler(MACHINE_SLOTS) : machine.getInventory();
        addSlot(new SlotItemHandler(items, 0, 23, 78) {
            @Override public boolean mayPlace(ItemStack stack) { return !isDroppedItemMode() && ChiselProcessing.accepts(stack); }
            @Override public boolean isActive() { return !configurationOpen; }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(items, 1 + row * 3 + col, 181 + col * 18, 60 + row * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean isActive() { return !configurationOpen; }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(playerSlot(inventory, 9 + row * 9 + col, 18 + col * 18, 129 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(playerSlot(inventory, col, 18 + col * 18, 187));
        data = machine == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> machine.getLumenAmount();
                    case 1 -> machine.getProgress();
                    case 2 -> machine.getStatus().ordinal();
                    case 9 -> machine.isDroppedItemMode() ? 1 : 0;
                    case 10 -> 0;
                    case 11 -> machine.isRoundRobin() ? 1 : 0;
                    case 12 -> machine.isAutoInput() ? 1 : 0;
                    case 13 -> machine.isAutoOutput() ? 1 : 0;
                    default -> machine.getSideMode(Direction.values()[index - 3]).ordinal();
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
        addDataSlots(data);
    }
    public int getLumenAmount() { return data.get(0); }
    public int getProgress() { return data.get(1); }
    public boolean isDroppedItemMode() { return data.get(9) != 0; }
    public boolean isRoundRobin() { return data.get(11) != 0; }
    public boolean isAutoInput() { return data.get(12) != 0; }
    public boolean isAutoOutput() { return data.get(13) != 0; }
    public boolean isConfigurationOpen() { return configurationOpen; }
    public void setConfigurationOpen(boolean open) { configurationOpen = open; }
    private Slot playerSlot(Inventory inventory, int slot, int x, int y) {
        return new Slot(inventory, slot, x, y) {
            @Override public boolean isActive() { return !configurationOpen; }
        };
    }
    public AutoChiselBlockEntity.Status getStatus() {
        return AutoChiselBlockEntity.Status.values()[Math.clamp(data.get(2), 0, AutoChiselBlockEntity.Status.values().length - 1)];
    }
    public AutoChiselBlockEntity.SideMode getSideMode(Direction side) {
        return AutoChiselBlockEntity.SideMode.values()[Math.clamp(data.get(3 + side.ordinal()), 0,
                AutoChiselBlockEntity.SideMode.values().length - 1)];
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || player.level().isClientSide || !stillValid(player) || button < 0 || button == 7
                || button >= REVERSE_SIDE_BASE + 6) return false;
        switch (button) {
            case MODE_BUTTON -> machine.toggleDroppedItemMode();
            case ROUND_ROBIN_BUTTON -> machine.toggleRoundRobin();
            case AUTO_INPUT_BUTTON -> machine.toggleAutoInput();
            case AUTO_OUTPUT_BUTTON -> machine.toggleAutoOutput();
            default -> {
                boolean reverse = button >= REVERSE_SIDE_BASE;
                machine.cycleSide(Direction.values()[reverse ? button - REVERSE_SIDE_BASE : button], reverse);
            }
        }
        broadcastChanges();
        return true;
    }
    @Override public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        if (machine == null) return player.level().isClientSide;
        return machine.getLevel() == player.level() && !machine.isRemoved()
                && player.level().getBlockEntity(machine.getBlockPos()) == machine
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (machine != null && index < MACHINE_SLOTS) machine.inventoryChanged(index);
        slot.onTake(player, stack);
        return original;
    }
}
