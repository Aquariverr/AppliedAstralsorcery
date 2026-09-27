package com.appliedastralsorcery.wand;

import appeng.api.ids.AEComponents;
import com.appliedastralsorcery.ModContent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Slotless settings menu bound to the exact stack and hand that opened it. */
public final class MEResonatingWandMenu extends AbstractContainerMenu {
    private final Player owner;
    private final ItemStack wand;
    private final InteractionHand hand;
    private final ContainerData data;

    public MEResonatingWandMenu(int id, Inventory inventory) { this(id, inventory, null, ItemStack.EMPTY); }

    public MEResonatingWandMenu(int id, Inventory inventory, InteractionHand hand, ItemStack wand) {
        super(ModContent.WAND_MENU.get(), id);
        this.owner = inventory.player;
        this.hand = hand;
        this.wand = wand;
        data = hand == null ? new SimpleContainerData(2) : new ContainerData() {
            @Override public int get(int index) {
                return index == 0 ? MEResonatingWandItem.options(wand) : wand.has(AEComponents.WIRELESS_LINK_TARGET) ? 1 : 0;
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 2; }
        };
        addDataSlots(data);
    }

    public boolean enabled(int option) { return (data.get(0) & option) != 0; }
    public boolean isLinked() { return data.get(1) == 1; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (hand == null || player.level().isClientSide() || !stillValid(player)
                || button != MEResonatingWandItem.USE_ME_ITEMS && button != MEResonatingWandItem.BUILD_FLUIDS
                        && button != MEResonatingWandItem.REPLACE_BLOCKS) return false;
        MEResonatingWandItem.setOptions(wand, MEResonatingWandItem.options(wand) ^ button);
        player.getInventory().setChanged();
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        return hand == null ? player.level().isClientSide()
                : player.getItemInHand(hand) == wand && wand.is(ModContent.ME_RESONATING_WAND);
    }

    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
