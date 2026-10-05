package com.appliedastralsorcery.lumen;

import java.util.Locale;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.SettingsFrom;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerViewFactory;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNode;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenRequestHelper;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenTransferNotifiable;
import hellfirepvp.astralsorcery.common.util.LumenUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MELumenFilamentBlockEntity extends AENetworkedBlockEntity implements LumenTransferNotifiable {
    public static final int TRANSFER_AMOUNT = 500;
    public static final int TRANSFER_INTERVAL = 20;
    private Lumen selected;
    private Lumen recentlyTransmittedLumen;
    private long transmittedLumenGameTime = Long.MIN_VALUE;
    private final LumenStackList pending = LumenStackList.create();
    private final ILumenHandler buffer = LumenHandlerViewFactory.builder()
            .tankCapacity(lumen -> TRANSFER_AMOUNT)
            .inputFilter((incoming, stored) -> pending.getLumenStacks().isEmpty()
                    || pending.getLumenStacks().getFirst().is(incoming.getLumen()))
            .onChange(lumen -> setChanged()).createView(pending);
    private Status status = Status.WAITING;

    public enum Status {
        UNCONFIGURED, OFFLINE, FULL, NO_SOURCE, RECEIVING, WAITING;

        public Component getMessage() {
            return Component.translatable("message.appliedas.filament." + name().toLowerCase(Locale.ROOT));
        }
    }

    public MELumenFilamentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);
    }

    public Lumen getSelectedLumen() {
        return selected;
    }

    public void setSelectedLumen(Lumen lumen) {
        selected = lumen == LumenAS.NONE.get() ? null : lumen;
        status = Status.WAITING;
        // Already-received lumen remains buffered until it can enter the grid, even on reconfiguration.
        setChanged();
    }

    public int getBufferedAmount() {
        return pending.getLumenStacks().stream().mapToInt(LumenStack::getAmount).sum();
    }

    public Status getStatus() {
        if (!getMainNode().isOnline()) return Status.OFFLINE;
        if (getBufferedAmount() > 0) return Status.FULL;
        if (selected == null) return Status.UNCONFIGURED;
        return status;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        updateBaseFace(server);
        // Like the original filament, retain a transmission node even when no type is selected.
        var node = LumenNetworkHelper.getNode(server, worldPosition).orElseGet(() ->
                LumenNetworkHelper.createNode(server, worldPosition, worldPosition.getCenter(),
                        LumenNode.ConnectionType.TRANSMISSION).orElse(null));
        if (server.getGameTime() % TRANSFER_INTERVAL != 0) return;
        if (!getMainNode().isOnline()) {
            status = Status.OFFLINE;
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var inventory = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);

        // A small persistent buffer prevents loss if an external ME inventory changes its acceptance.
        flushBuffer(inventory, source);
        if (getBufferedAmount() > 0) {
            status = Status.FULL;
            return;
        }
        if (selected == null) {
            status = Status.UNCONFIGURED;
            return;
        }
        var key = LumenKey.of(selected);
        int accepted = (int) inventory.insert(key, TRANSFER_AMOUNT, Actionable.SIMULATE, source);
        if (accepted <= 0) {
            status = Status.FULL;
            return;
        }
        if (node == null) return;
        var requested = selected.stack(accepted);
        var chain = LumenRequestHelper.requestRelayed(server, node, requested,
                sourceNode -> !(server.getBlockEntity(sourceNode.getPos()) instanceof MELumenArrayBlockEntity array)
                        || array.canInteractWithFilaments()).orElse(null);
        if (chain == null) {
            status = Status.NO_SOURCE;
            return;
        }
        var transferred = LumenUtil.tryChainTransfer(buffer, server, chain, requested, ILumenHandler.Action.EXECUTE);
        if (transferred.isEmpty()) {
            status = Status.NO_SOURCE;
            return;
        }
        chain.playTransferEffect(server, transferred.getLumen());
        flushBuffer(inventory, source);
        status = getBufferedAmount() == 0 ? Status.RECEIVING : Status.FULL;
    }

    private void updateBaseFace(ServerLevel server) {
        var gridNode = getMainNode().getNode();
        if (gridNode == null) return;
        var connected = gridNode.getConnectedSides();
        var state = getBlockState();
        var current = state.getValue(MELumenFilamentBlock.BASE_FACE);
        // Keep the current mounting face while it is connected, including unpowered networks.
        if (connected.isEmpty() || connected.contains(current)) return;
        for (var side : Direction.values()) {
            if (connected.contains(side)) {
                server.setBlock(worldPosition, state.setValue(MELumenFilamentBlock.BASE_FACE, side), Block.UPDATE_CLIENTS);
                return;
            }
        }
    }

    @Override
    public void onTransfer(ServerLevel server, Lumen type) {
        recentlyTransmittedLumen = type;
        transmittedLumenGameTime = server.getGameTime();
        markForClientUpdate();
    }

    public Lumen getRecentlyTransmittedLumen() {
        return recentlyTransmittedLumen == null ? LumenAS.NONE.get() : recentlyTransmittedLumen;
    }

    public long getTransmittedLumenGameTime() {
        return transmittedLumenGameTime;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeUtf(getRecentlyTransmittedLumen().getRegistryKey().orElseThrow().location().toString());
        data.writeLong(transmittedLumenGameTime);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        recentlyTransmittedLumen = readLumen(data.readUtf());
        transmittedLumenGameTime = data.readLong();
        return changed;
    }

    private void flushBuffer(appeng.api.storage.MEStorage inventory, IActionSource source) {
        for (var stack : buffer.getContainedLumen()) {
            if (stack.isEmpty()) continue;
            var inserted = inventory.insert(LumenKey.of(stack.getLumen()), stack.getAmount(), Actionable.MODULATE, source);
            if (inserted > 0) buffer.drain(stack.getLumen(), (int) inserted, ILumenHandler.Action.EXECUTE);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filament", saveFilament(true));
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        loadFilament(tag.getCompound("filament"), true);
    }

    private CompoundTag saveFilament(boolean includeBuffer) {
        var tag = new CompoundTag();
        if (selected != null) tag.putString("selected", selected.getRegistryKey().orElseThrow().location().toString());
        if (includeBuffer && !pending.getLumenStacks().isEmpty()) {
            var stack = pending.getLumenStacks().getFirst();
            if (!stack.isEmpty()) {
                tag.putString("pending_type", stack.getLumen().getRegistryKey().orElseThrow().location().toString());
                tag.putInt("pending_amount", stack.getAmount());
            }
        }
        return tag;
    }

    private void loadFilament(CompoundTag tag, boolean includeBuffer) {
        selected = readLumen(tag.getString("selected"));
        status = Status.WAITING;
        if (includeBuffer) {
            pending.clear();
            var type = readLumen(tag.getString("pending_type"));
            int amount = Math.clamp(tag.getInt("pending_amount"), 0, TRANSFER_AMOUNT);
            if (type != null && amount > 0) pending.setLumenStack(type.stack(amount));
        }
    }

    private static Lumen readLumen(String name) {
        var id = ResourceLocation.tryParse(name);
        var lumen = id == null ? null : RegistriesAS.REGISTRY_LUMEN.get(id);
        return lumen == LumenAS.NONE.get() ? null : lumen;
    }

    @Override
    public void exportSettings(SettingsFrom from, DataComponentMap.Builder output, Player player) {
        super.exportSettings(from, output, player);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(saveFilament(from == SettingsFrom.DISMANTLE_ITEM)));
    }

    @Override
    public void importSettings(SettingsFrom from, DataComponentMap input, Player player) {
        super.importSettings(from, input, player);
        var data = input.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            loadFilament(data.copyTag(), from == SettingsFrom.DISMANTLE_ITEM);
            setChanged();
        }
    }
}
