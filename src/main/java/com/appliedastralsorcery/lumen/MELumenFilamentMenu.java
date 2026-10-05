package com.appliedastralsorcery.lumen;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import java.util.Comparator;
import java.util.List;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative configuration; button zero stops requests, all other buttons select a type. */
public final class MELumenFilamentMenu extends AbstractContainerMenu {
    private static final int DATA_COUNT = 3;
    private final List<Lumen> availableLumen;
    private final MELumenFilamentBlockEntity filament;
    private final Player owner;
    private final ContainerData data;

    public MELumenFilamentMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    public MELumenFilamentMenu(int id, Inventory inventory, MELumenFilamentBlockEntity filament) {
        super(ModContent.FILAMENT_MENU.get(), id);
        this.filament = filament;
        this.owner = inventory.player;
        // Registry names give the server and client the same ordering, including addon lumen types.
        availableLumen = RegistriesAS.REGISTRY_LUMEN.stream()
                .filter(lumen -> lumen != LumenAS.NONE.get())
                .sorted(Comparator.comparing(lumen -> lumen.getRegistryKey().orElseThrow().location().toString()))
                .toList();
        data = filament == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> availableLumen.indexOf(filament.getSelectedLumen()) + 1;
                    case 1 -> filament.getStatus().ordinal();
                    case 2 -> filament.getBufferedAmount();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server values always come from the block entity, never from client data slots.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(data);
    }

    public List<Lumen> getAvailableLumen() {
        return availableLumen;
    }

    public Lumen getSelectedLumen() {
        int index = data.get(0) - 1;
        return index >= 0 && index < availableLumen.size() ? availableLumen.get(index) : null;
    }

    public Component getStatusMessage() {
        var statuses = MELumenFilamentBlockEntity.Status.values();
        int index = data.get(1);
        return statuses[index >= 0 && index < statuses.length ? index : 0].getMessage();
    }

    public int getBufferedAmount() {
        return data.get(2);
    }

    @Override
    @ParametersAreNonnullByDefault
    @SuppressWarnings("resource")
    public boolean clickMenuButton(Player player, int button) {
        if (filament == null || player.level().isClientSide() || !stillValid(player)
                || button < 0 || button > availableLumen.size()) {
            return false;
        }
        filament.setSelectedLumen(button == 0 ? null : availableLumen.get(button - 1));
        broadcastChanges();
        return true;
    }

    @Override
    @ParametersAreNonnullByDefault
    @SuppressWarnings("resource")
    public boolean stillValid(Player player) {
        if (player != owner || player.isSpectator()) return false;
        if (filament == null) return player.level().isClientSide();
        var level = filament.getLevel();
        var pos = filament.getBlockPos();
        return level == player.level() && !filament.isRemoved() && level.getBlockEntity(pos) == filament
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }

    @Override
    @Nonnull
    @ParametersAreNonnullByDefault
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
