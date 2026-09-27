package com.appliedastralsorcery.chalice;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import appeng.api.stacks.AEFluidKey;
import com.appliedastralsorcery.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MEChaliceMenu extends AbstractContainerMenu {
    public static final int TARGET_BUTTON_BASE = 100_000;
    public static final int MARKER_LEFT = 4, MARKER_RIGHT = 5;
    private AEFluidKey clientSelection, lastSentSelection;
    private boolean selectionSent;
    private final MEChaliceBlockEntity chalice;
    private final Player owner;
    private final ContainerData data;
    private final List<Fluid> types = BuiltInRegistries.FLUID.stream()
            .filter(fluid -> fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState()))
            .sorted(Comparator.comparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString())).toList();

    public MEChaliceMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public MEChaliceMenu(int id, Inventory inventory, MEChaliceBlockEntity chalice) {
        super(ModContent.CHALICE_MENU.get(), id);
        this.chalice = chalice;
        owner = inventory.player;
        data = chalice == null ? new SimpleContainerData(8) : new ContainerData() {
            @Override public int get(int index) { return switch (index) {
                case 0 -> types.indexOf(chalice.getSelectedFluid()) + 1;
                case 1 -> chalice.getPullTarget();
                case 2 -> chalice.getStoredAmount();
                case 3 -> BuiltInRegistries.FLUID.getId(chalice.getContainedFluid().getFluid());
                case 4 -> chalice.isExportEnabled() ? 1 : 0;
                case 5 -> chalice.isPullEnabled() ? 1 : 0;
                case 6 -> chalice.getMainNode().isOnline() ? 1 : 0;
                case 7 -> chalice.isSwitchPending() ? 1 : 0;
                default -> 0;
            }; }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 8; }
        };
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 18 + col * 18, 157 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 18 + col * 18, 215));
    }
    // Container data packets use signed shorts. 64 buckets must survive the full unsigned range.
    public int value(int index) { return data.get(index) & 0xFFFF; }
    public int getPullTarget() { return value(1); }
    public int getStoredAmount() { return value(2); }
    public List<Fluid> getTypes() { return types; }
    public Fluid getSelectedFluid() {
        var key = getSelectedKey();
        return key == null ? null : key.getFluid();
    }
    public AEFluidKey getSelectedKey() { return chalice == null ? clientSelection : chalice.getSelectedKey(); }
    public void setClientSelection(AEFluidKey key) {
        if (chalice == null) {
            clientSelection = key;
            data.set(0, key == null ? 0 : types.indexOf(key.getFluid()) + 1);
        }
    }
    public boolean setFluidMarker(Player player, AEFluidKey key) {
        if (chalice == null || player.level().isClientSide() || !stillValid(player)
                || key != null && !types.contains(key.getFluid())) return false;
        chalice.setSelectedKey(key);
        selectionSent = false;
        broadcastChanges();
        return true;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (chalice != null && owner instanceof ServerPlayer player) {
            var key = chalice.getSelectedKey();
            if (!selectionSent || !Objects.equals(key, lastSentSelection)) {
                PacketDistributor.sendToPlayer(player, new ChaliceFluidSelection(containerId,
                        key == null ? FluidStack.EMPTY : key.toStack(1)));
                selectionSent = true;
                lastSentSelection = key;
            }
        }
    }
    public FluidStack getStoredFluid() {
        var fluid = BuiltInRegistries.FLUID.byId(value(3));
        return fluid == null || fluid == Fluids.EMPTY || value(2) == 0 ? FluidStack.EMPTY : new FluidStack(fluid, value(2));
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (chalice == null || player.level().isClientSide() || !stillValid(player)) return false;
        if (button >= TARGET_BUTTON_BASE && button <= TARGET_BUTTON_BASE + MEChaliceBlockEntity.CAPACITY)
            chalice.setPullTarget(button - TARGET_BUTTON_BASE);
        else if (button == 1) chalice.setExportEnabled(!chalice.isExportEnabled());
        else if (button == 2) chalice.setPullEnabled(!chalice.isPullEnabled());
        else if (button == 3) chalice.setSelectedFluid(null);
        else if (button == MARKER_LEFT || button == MARKER_RIGHT) {
            var carried = getCarried();
            var key = ChaliceFluidMarker.carried(carried, button == MARKER_RIGHT ? 1 : 0);
            if (!carried.isEmpty() && key == null) return false;
            return setFluidMarker(player, key);
        }
        else if (button >= 100 && button < 100 + types.size()) chalice.setSelectedFluid(types.get(button - 100));
        else return false;
        broadcastChanges();
        return true;
    }
    @Override public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        return chalice == null ? player.level().isClientSide() : !chalice.isRemoved()
                && chalice.getLevel() == player.level() && player.level().getBlockEntity(chalice.getBlockPos()) == chalice
                && player.distanceToSqr(chalice.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        if (index < 27 ? !moveItemStackTo(stack, 27, 36, false) : !moveItemStackTo(stack, 0, 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
