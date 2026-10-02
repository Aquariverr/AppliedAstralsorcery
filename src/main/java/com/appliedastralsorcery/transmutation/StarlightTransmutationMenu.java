package com.appliedastralsorcery.transmutation;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class StarlightTransmutationMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOTS = 18, DATA_COUNT = 10;
    public static final int MARKER_SLOT_START = 54, AUTO_PULL_BUTTON = 0, OVERCLOCK_BUTTON = 1;
    private final StarlightTransmutationBlockEntity machine;
    private final Player owner;
    private final ContainerData data;
    private final ItemStackHandler markers;
    public StarlightTransmutationMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public StarlightTransmutationMenu(int id, Inventory inventory, StarlightTransmutationBlockEntity machine) {
        super(ModContent.TRANSMUTATION_MENU.get(), id);
        this.machine = machine;
        owner = inventory.player;
        markers = machine == null ? new ItemStackHandler(9) : machine.getPullMarkers();
        var items = machine == null ? new ItemStackHandler(MACHINE_SLOTS) : machine.getInventory();
        for (int group = 0; group < 2; group++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int slot = group * 9 + row * 3 + col;
                    addSlot(new SlotItemHandler(items, slot, 22 + group * 164 + col * 18, 53 + row * 18) {
                        @ParametersAreNonnullByDefault
                        @Override public boolean mayPlace(ItemStack stack) {
                            return getSlotIndex() < StarlightTransmutationBlockEntity.INPUT_SLOTS
                                    && (machine == null || machine.accepts(stack));
                        }
                        @Override public void setChanged() { super.setChanged(); if (machine != null) machine.inventoryChanged(); }
                    });
                }
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 51 + col * 18, 197 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 51 + col * 18, 255));
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new SlotItemHandler(markers, slot, 51 + slot * 18, 155) {
                @Override public boolean isActive() { return isAutoPull(); }
                @ParametersAreNonnullByDefault
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @ParametersAreNonnullByDefault
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        data = machine == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> machine.getStatus().ordinal();
                    case 1 -> machine.getProgress() & 0xFFFF;
                    case 2 -> machine.getProgress() >>> 16;
                    case 3 -> machine.getDuration() & 0xFFFF;
                    case 4 -> machine.getDuration() >>> 16;
                    case 5 -> machine.getMainNode().isOnline() ? 1 : 0;
                    case 6 -> machine.hasStarlight() ? 1 : 0;
                    case 7 -> machine.getDisplayConstellation() == null ? 0
                            : RegistriesAS.REGISTRY_CONSTELLATIONS.getId(machine.getDisplayConstellation()) + 1;
                    case 8 -> machine.isAutoPull() ? 1 : 0;
                    case 9 -> machine.isLumenOverclock() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
        addDataSlots(data);
    }
    public StarlightTransmutationBlockEntity.Status getStatus() {
        return StarlightTransmutationBlockEntity.Status.values()[Math.clamp(data.get(0), 0,
                StarlightTransmutationBlockEntity.Status.values().length - 1)];
    }
    public int getProgress() { return (data.get(1) & 0xFFFF) | (data.get(2) & 0xFFFF) << 16; }
    public int getDuration() { return (data.get(3) & 0xFFFF) | (data.get(4) & 0xFFFF) << 16; }
    public boolean isOnline() { return data.get(5) != 0; }
    public boolean hasStarlight() { return data.get(6) != 0; }
    public boolean isAutoPull() { return data.get(8) != 0; }
    public boolean isLumenOverclock() { return data.get(9) != 0; }
    public ItemStack getPullMarker(int slot) { return markers.getStackInSlot(slot); }
    public boolean setMarkerAmount(Player player, int slot, ItemStack expectedItem, int amount, boolean relative) {
        if (slot < 0 || slot >= 9 || expectedItem.isEmpty()) return false;
        var marker = getPullMarker(slot);
        if (marker.isEmpty() || !ItemStack.isSameItemSameComponents(marker, expectedItem)) return false;
        // Relative scroll requests accumulate on the server even before the client receives slot updates.
        long requested = relative ? (long) marker.getCount() + amount : amount;
        int count = (int) Math.clamp(requested, 0L, Math.min(64, marker.getMaxStackSize()));
        return setPullMarker(player, slot, count == 0 ? ItemStack.EMPTY : marker.copyWithCount(count));
    }
    @SuppressWarnings("resource") // Minecraft manages the player's world lifetime.
    public boolean setPullMarker(Player player, int slot, ItemStack stack) {
        if (machine == null || player.level().isClientSide || !stillValid(player) || !isAutoPull()) return false;
        if (!machine.setPullMarker(slot, stack)) return false;
        broadcastChanges();
        return true;
    }
    @ParametersAreNonnullByDefault
    @SuppressWarnings("resource") // Minecraft manages the player's world lifetime.
    @Override public boolean clickMenuButton(Player player, int button) {
        if (machine == null || player.level().isClientSide || !stillValid(player)) return false;
        if (button == AUTO_PULL_BUTTON) machine.toggleAutoPull();
        else if (button == OVERCLOCK_BUTTON) machine.toggleLumenOverclock();
        else return false;
        broadcastChanges();
        return true;
    }
    @ParametersAreNonnullByDefault
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        if (slot >= MARKER_SLOT_START && slot < MARKER_SLOT_START + 9) {
            if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
                int markerSlot = slot - MARKER_SLOT_START;
                var held = getCarried();
                var marker = getPullMarker(markerSlot);
                if (button == 0) {
                    // ME interface configuration: add a held stack to the same target,
                    // replace a different target, or clear with an empty hand.
                    var next = held.copy();
                    if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, marker))
                        next.setCount(Math.min(64, marker.getCount() + held.getCount()));
                    setPullMarker(player, markerSlot, next);
                } else if (marker.isEmpty()) {
                    if (!held.isEmpty()) setPullMarker(player, markerSlot, held.copyWithCount(1));
                } else if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, marker)) {
                    int remaining = marker.getCount() - (held.isEmpty() ? 1 : held.getCount());
                    setPullMarker(player, markerSlot, remaining <= 0 ? ItemStack.EMPTY : marker.copyWithCount(remaining));
                }
            }
            return;
        }
        super.clicked(slot, button, type, player);
    }
    @ParametersAreNonnullByDefault
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index < MARKER_SLOT_START && slot.isActive() && super.canTakeItemForPickAll(stack, slot);
    }
    public BaseConstellation getConstellation() {
        int id = data.get(7) & 0xFFFF;
        return id == 0 ? null : RegistriesAS.REGISTRY_CONSTELLATIONS.byId(id - 1);
    }
    @ParametersAreNonnullByDefault
    @SuppressWarnings("resource") // Minecraft manages the player's world lifetime.
    @Override public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        if (machine == null) return player.level().isClientSide;
        return machine.getLevel() == player.level() && !machine.isRemoved()
                && player.level().getBlockEntity(machine.getBlockPos()) == machine
                && player.distanceToSqr(machine.getBlockPos().getCenter()) <= 64;
    }
    @Nonnull
    @ParametersAreNonnullByDefault
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= MARKER_SLOT_START || !slots.get(index).isActive()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, MARKER_SLOT_START, true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, StarlightTransmutationBlockEntity.INPUT_SLOTS, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (machine != null && index < MACHINE_SLOTS) machine.inventoryChanged();
        slot.onTake(player, stack);
        return original;
    }
}
