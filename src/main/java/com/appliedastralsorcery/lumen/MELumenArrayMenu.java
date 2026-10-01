package com.appliedastralsorcery.lumen;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.Comparator;
import java.util.List;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MELumenArrayMenu extends AbstractContainerMenu {
    public static final int REDSTONE_BUTTON = 4, REDSTONE_DATA = 10;
    private final MELumenArrayBlockEntity array;
    private final Player owner;
    private final ContainerData data;
    private final List<Lumen> types = RegistriesAS.REGISTRY_LUMEN.stream()
            .filter(type -> type != LumenAS.NONE.get())
            .sorted(Comparator.comparing(type -> type.getRegistryKey().orElseThrow().location().toString())).toList();
    public MELumenArrayMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public MELumenArrayMenu(int id, Inventory inventory, @Nullable MELumenArrayBlockEntity array) {
        this(id, inventory, array, array != null && array.isAlchemyArray());
    }
    public MELumenArrayMenu(int id, Inventory inventory, @Nullable MELumenArrayBlockEntity array, boolean alchemy) {
        super(alchemy ? ModContent.ALCHEMY_ARRAY_MENU.get() : ModContent.ARRAY_MENU.get(), id);
        this.array = array;
        owner = inventory.player;
        data = array == null ? new SimpleContainerData(11) : new ContainerData() {
            public int get(int i) { return switch (i) {
                case 0 -> types.indexOf(array.getSelectedLumen()) + 1;
                case 1 -> array.getPullTarget();
                case 2 -> array.getStoredAmount();
                case 3 -> array.getTileData().getContainedFluid().getAmount();
                case 4 -> array.isExportEnabled() ? 1 : 0;
                case 5 -> array.isSupplyItemsEnabled() ? 1 : 0;
                case 6 -> array.canInteractWithFilaments() ? 1 : 0;
                case 7 -> array.getMainNode().isOnline() ? 1 : 0;
                case 8 -> types.indexOf(array.getActiveLumen()) + 1;
                case 9 -> array.isSwitchPending() ? 1 : 0;
                case REDSTONE_DATA -> array.isRedstoneControlEnabled() ? 1 : 0;
                default -> 0;
            }; }
            public void set(int i, int value) {}
            public int getCount() { return 11; }
        };
        addDataSlots(data);
        addSlot(new SlotItemHandler(array == null ? new ItemStackHandler(1) : array.getTileData().getInventory(), 0, 45, 85) {
            @Override public boolean mayPlace(ItemStack stack) {
                // AS's InventoryView.isItemValid is deliberately permissive; insertItem owns its recipe/fluid filters.
                return !stack.isEmpty() && getItemHandler().insertItem(0, stack.copyWithCount(1), true).isEmpty();
            }
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 18 + col * 18, 157 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 18 + col * 18, 215));
    }
    public int value(int index) { return data.get(index); }
    public boolean isAlchemyArray() { return getType() == ModContent.ALCHEMY_ARRAY_MENU.get(); }
    public List<Lumen> getTypes() { return types; }
    @Nullable public Lumen getSelectedLumen() {
        int index = value(0) - 1;
        return index >= 0 && index < types.size() ? types.get(index) : null;
    }
    @Nullable public Lumen getActiveLumen() {
        int index = value(8) - 1;
        return index >= 0 && index < types.size() ? types.get(index) : null;
    }
    // The player owns its Level; menu interactions only borrow it.
    @SuppressWarnings("resource")
    @Override public boolean clickMenuButton(Player player, int button) {
        if (array == null || player.level().isClientSide() || !stillValid(player)) return false;
        if (button >= 10000 && button <= 14000) array.setPullTarget(button - 10000);
        else if (button == 1) array.setExportEnabled(!array.isExportEnabled());
        else if (button == 2) array.setSupplyItemsEnabled(!array.isSupplyItemsEnabled());
        else if (button == 3) array.setInteractWithFilaments(!array.canInteractWithFilaments());
        else if (button == REDSTONE_BUTTON) array.setRedstoneControlEnabled(!array.isRedstoneControlEnabled());
        else if (button >= 100 && button < 100 + types.size()) array.setSelectedLumen(types.get(button - 100));
        else return false;
        broadcastChanges();
        return true;
    }
    // World lifetime belongs to Minecraft, not this menu.
    @SuppressWarnings("resource")
    @Override public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        return array == null ? player.level().isClientSide() : !array.isRemoved()
                && array.getLevel() == player.level() && player.level().getBlockEntity(array.getBlockPos()) == array
                && player.distanceToSqr(array.getBlockPos().getCenter()) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        if (index == 0 ? !moveItemStackTo(stack, 1, slots.size(), true) : !moveItemStackTo(stack, 0, 1, false))
            return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
