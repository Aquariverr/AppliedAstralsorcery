package com.appliedastralsorcery.wand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Escrow only: resources cannot be used until the complete build has been reserved. */
final class WandMaterialBuffer {
    private final ItemStack wand;
    private final IGrid grid;
    private final PlayerSource source;
    private final ServerPlayer player;
    private final boolean useMeItems;
    private final Map<AEKey, Long> contents = new LinkedHashMap<>();
    private final Map<AEItemKey, Long> localReservations = new LinkedHashMap<>();

    WandMaterialBuffer(ItemStack wand, IGrid grid, ServerPlayer player) {
        this.wand = wand;
        this.grid = grid;
        this.source = new PlayerSource(player);
        this.player = player;
        this.useMeItems = MEResonatingWandItem.enabled(wand, MEResonatingWandItem.USE_ME_ITEMS);
        List<GenericStack> retained = wand.getOrDefault(MEResonatingWandItem.RETURN_BUFFER.get(), List.of());
        for (var resource : retained) {
            contents.merge(resource.what(), resource.amount(), Long::sum);
        }
    }

    boolean reserve(Map<AEKey, Long> required, ServerPlayer player) {
        if (player.isCreative()) return true;
        var inventory = grid == null ? null : grid.getStorageService().getInventory();
        Map<AEKey, Long> networkReservations = new LinkedHashMap<>();
        double power = 0;
        for (var entry : required.entrySet()) {
            long needed = Math.max(0, entry.getValue() - contents.getOrDefault(entry.getKey(), 0L));
            boolean item = entry.getKey() instanceof AEItemKey;
            long fromNetwork = inventory != null && (!item || useMeItems)
                    ? inventory.extract(entry.getKey(), needed, Actionable.SIMULATE, source) : 0;
            long fromInventory = item ? inventoryItems((AEItemKey) entry.getKey(), needed - fromNetwork, false) : 0;
            if (fromNetwork + fromInventory < needed) {
                String message = item ? useMeItems ? "missing_combined" : "missing_inventory" : "missing";
                MEResonatingWandItem.message(player, message, entry.getKey().getDisplayName(), needed);
                return false;
            }
            networkReservations.put(entry.getKey(), fromNetwork);
            power += (double) fromNetwork / Math.max(1, entry.getKey().getAmountPerOperation());
        }
        if (power > 0 && (grid == null || grid.getEnergyService()
                .extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.0001 < power)) {
            MEResonatingWandItem.message(player, "no_power");
            return false;
        }
        for (var entry : required.entrySet()) {
            long needed = Math.max(0, entry.getValue() - contents.getOrDefault(entry.getKey(), 0L));
            if (needed == 0) continue;
            long fromNetwork = networkReservations.get(entry.getKey());
            long extracted = fromNetwork == 0 || grid == null || inventory == null ? 0 : StorageHelper.poweredExtraction(
                    grid.getEnergyService(), inventory, entry.getKey(), fromNetwork, source);
            if (entry.getKey() instanceof AEItemKey item && extracted < needed) {
                long fromInventory = inventoryItems(item, needed - extracted, true);
                localReservations.merge(item, fromInventory, Long::sum);
                extracted += fromInventory;
            }
            contents.merge(entry.getKey(), extracted, Long::sum);
            save();
            if (extracted != needed) {
                MEResonatingWandItem.message(player, "interrupted");
                return false;
            }
        }
        return true;
    }

    void consume(AEKey key, long amount) {
        if (player.isCreative()) return;
        if (key instanceof AEItemKey item) {
            // Consume ME materials first so unused backpack materials return to their original source.
            long fromNetwork = contents.getOrDefault(key, 0L) - localReservations.getOrDefault(item, 0L);
            long fromInventory = Math.max(0, amount - fromNetwork);
            localReservations.computeIfPresent(item, (ignored, reserved) -> Math.max(0, reserved - fromInventory));
        }
        long remaining = contents.getOrDefault(key, 0L) - amount;
        if (remaining < 0) throw new IllegalStateException("Unreserved wand material");
        if (remaining == 0) contents.remove(key);
        else contents.put(key, remaining);
        save();
    }

    void refund() {
        // Return backpack reservations to the backpack, even when the network is unavailable.
        for (var entry : localReservations.entrySet()) {
            long amount = entry.getValue();
            while (amount > 0) {
                int count = (int) Math.min(amount, entry.getKey().toStack().getMaxStackSize());
                var returned = entry.getKey().toStack(count);
                player.getInventory().add(returned);
                if (!returned.isEmpty()) player.drop(returned, false);
                amount -= count;
            }
            contents.computeIfPresent(entry.getKey(), (key, stored) -> stored - entry.getValue());
        }
        localReservations.clear();
        contents.values().removeIf(amount -> amount <= 0);
        save();
        if (grid != null) {
            var inventory = grid.getStorageService().getInventory();
            for (var key : List.copyOf(contents.keySet())) {
                long remaining = contents.get(key) - inventory.insert(key, contents.get(key), Actionable.MODULATE, source);
                if (remaining == 0) contents.remove(key); else contents.put(key, remaining);
                save();
            }
        }
    }

    boolean canRecover(Map<AEKey, Long> recovered) {
        if (recovered.isEmpty()) return true;
        if (grid == null) return false;
        var inventory = grid.getStorageService().getInventory();
        return recovered.entrySet().stream().allMatch(entry -> inventory.insert(entry.getKey(), entry.getValue(),
                Actionable.SIMULATE, source) == entry.getValue());
    }

    boolean recover(Map<AEKey, Long> recovered) {
        if (recovered.isEmpty()) return true;
        // Persist recovery before touching external storage. Partial insertions can never lose blocks.
        recovered.forEach((key, amount) -> contents.merge(key, amount, Long::sum));
        save();
        boolean complete = true;
        for (var entry : recovered.entrySet()) {
            long inserted = grid == null ? 0 : grid.getStorageService().getInventory().insert(
                    entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
            contents.computeIfPresent(entry.getKey(), (key, amount) -> amount - inserted);
            contents.values().removeIf(amount -> amount <= 0);
            save();
            complete &= inserted == entry.getValue();
        }
        return complete;
    }

    private long inventoryItems(AEItemKey key, long requested, boolean extract) {
        long found = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && found < requested; i++) {
            var stack = inventory.getItem(i);
            if (!key.equals(AEItemKey.of(stack))) continue;
            int amount = (int) Math.min(requested - found, stack.getCount());
            found += amount;
            if (extract) stack.shrink(amount);
        }
        if (extract) inventory.setChanged();
        return found;
    }

    private void save() {
        if (contents.isEmpty()) wand.remove(MEResonatingWandItem.RETURN_BUFFER);
        else wand.set(MEResonatingWandItem.RETURN_BUFFER, contents.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList());
    }
}
