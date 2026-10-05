package com.appliedastralsorcery.gateway;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MECelestialGatewayMenu extends AbstractContainerMenu {
    private final MECelestialGatewayBlockEntity gate;
    private final ContainerData data;
    public MECelestialGatewayMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public MECelestialGatewayMenu(int id, Inventory inventory, @Nullable MECelestialGatewayBlockEntity gate) {
        super(ModContent.GATEWAY_MENU.get(), id);
        this.gate = gate;
        var handler = gate == null ? new ItemStackHandler(1) : gate.getInventory();
        addSlot(new SlotItemHandler(handler, 0, 26, 40) {
            @Override public boolean mayPlace(ItemStack stack) { return SpatialCellAccess.accepts(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        data = gate == null ? new SimpleContainerData(2) : new ContainerData() {
            public int get(int index) {
                return index == 0 ? (gate.hasStructure() && gate.doesSeeSky() ? 1 : 0)
                        : SpatialCellAccess.progress(handler.getStackInSlot(0));
            }
            public void set(int index, int value) {}
            public int getCount() { return 2; }
        };
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 102 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 160));
    }
    public boolean active() { return data.get(0) != 0; }
    public int progress() { return data.get(1); }
    @SuppressWarnings("resource")
    @Override public boolean stillValid(Player player) {
        return gate == null || !gate.isRemoved() && player.level() == gate.getLevel() && gate.canUse(player)
                && player.distanceToSqr(gate.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index == 0 ? !moveItemStackTo(stack, 1, slots.size(), true) : !moveItemStackTo(stack, 0, 1, false))
            return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
