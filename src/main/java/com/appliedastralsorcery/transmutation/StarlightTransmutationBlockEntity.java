package com.appliedastralsorcery.transmutation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.lumen.LumenKey;
import com.mojang.serialization.Codec;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.component.CrystalAttributesComponent;
import hellfirepvp.astralsorcery.common.data.level.FocalPointData;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lib.StarlightNetworkNodesAS;
import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.api.provider.TransmissionNodeProvider;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionChain;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.base.TileEntityNetwork;
import hellfirepvp.astralsorcery.common.tile.network.ForwardingStarlightReceiverNode;
import hellfirepvp.astralsorcery.common.tile.network.FocusCrystalSourceNode;
import hellfirepvp.astralsorcery.common.tile.network.provider.ForwardingStarlightReceiverNodeProvider;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import hellfirepvp.astralsorcery.common.util.level.DayTimeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class StarlightTransmutationBlockEntity
        extends TileEntityNetwork<ForwardingStarlightReceiverNode, TileEntityNetwork.Data>
        implements ForwardingStarlightReceiverNode.ReceiverTile, IGridConnectedBlockEntity {
    public static final int INPUT_SLOTS = 9, OUTPUT_SLOTS = 9;
    public static final int OVERCLOCK_LUMEN_COST = 25, OVERCLOCK_DURATION = 20;
    // Transmission is live light, not a stored energy resource. Allow the network's tick ordering.
    private static final int STARLIGHT_TIMEOUT = 2;
    public enum Status { IDLE, MISSING_INPUTS, NO_STARLIGHT, WRONG_CONSTELLATION, OFFLINE, OUTPUT_BLOCKED, RUNNING }
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (IGridNodeListener<StarlightTransmutationBlockEntity>) (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(Set.of(Direction.values()))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2.0);
    private final Map<BaseConstellation, Long> receivedStarlight = new HashMap<>();
    private final ItemStackHandler inventory = new ItemStackHandler(INPUT_SLOTS + OUTPUT_SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot < INPUT_SLOTS && accepts(stack); }
        @Override protected void onContentsChanged(int slot) { inventoryChanged(); }
    };
    private final ItemStackHandler pullMarkers = new ItemStackHandler(INPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { if (!loading) setChanged(false); }
    };
    private boolean autoPull;
    private boolean lumenOverclock, jobOverclocked;
    private int overclockCharge;
    private ResourceLocation recipeId;
    private int progress, duration;
    private Status status = Status.IDLE;
    private boolean loading;
    private boolean starlightRoutesInitialized;
    private ItemStack displayItem = ItemStack.EMPTY;
    private BaseConstellation displayConstellation;
    private BaseConstellation focalConstellation;

    public StarlightTransmutationBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.TRANSMUTATION_ENTITY), pos, state);
    }
    @Override public Codec<Data> dataCodec() { return Data.CODEC; }
    @Override public DeferredHolder<TransmissionNodeProvider<?>, ForwardingStarlightReceiverNodeProvider> getNodeProvider() {
        return StarlightNetworkNodesAS.FORWARDING_RECEIVER_NODE;
    }
    @Override public IManagedGridNode getMainNode() { return mainNode; }
    @Override public void saveChanges() { setChanged(); }
    @Override public IGridNode getGridNode(Direction side) { return mainNode.getNode(); }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public ItemStackHandler getInventory() { return inventory; }
    public ItemStackHandler getPullMarkers() { return pullMarkers; }
    public boolean isAutoPull() { return autoPull; }
    public void toggleAutoPull() { autoPull = !autoPull; setChanged(false); }
    public boolean isLumenOverclock() { return lumenOverclock; }
    public void toggleLumenOverclock() { lumenOverclock = !lumenOverclock; setChanged(false); }
    public boolean setPullMarker(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INPUT_SLOTS || !stack.isEmpty() && !accepts(stack)) return false;
        pullMarkers.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY
                : stack.copyWithCount(Math.clamp(stack.getCount(), 1, Math.min(64, stack.getMaxStackSize()))));
        return true;
    }
    public int getProgress() { return progress; }
    public int getDuration() { return duration; }
    public Status getStatus() { return status; }
    public boolean hasStarlight() { return focalConstellation != null || !receivedStarlight.isEmpty(); }
    public BaseConstellation getDisplayConstellation() { return displayConstellation; }

    private Stream<BaseConstellation> availableConstellations() {
        return Stream.concat(receivedStarlight.keySet().stream(), Stream.ofNullable(focalConstellation));
    }

    private BaseConstellation receivedConstellation(FocalCombineRecipe recipe) {
        // Stable ordering prevents multiple live beams from making the icon flicker.
        return availableConstellations().filter(constellation -> constellation != null)
                .filter(constellation -> recipe == null || recipe.isRequiredConstellation(constellation))
                .min(Comparator.comparingInt(RegistriesAS.REGISTRY_CONSTELLATIONS::getId)).orElse(null);
    }
    public ItemStack getDisplayItem() {
        if (level != null && level.isClientSide) return displayItem;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) return inventory.getStackInSlot(slot).copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = super.getUpdateTag(registries);
        var preview = getDisplayItem();
        if (!preview.isEmpty()) tag.put("preview", preview.save(registries));
        if (displayConstellation != null)
            tag.putString("displayConstellation", RegistriesAS.REGISTRY_CONSTELLATIONS.getKey(displayConstellation).toString());
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readDisplayState(tag, registries);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        readDisplayState(packet.getTag(), registries);
    }
    private void readDisplayState(CompoundTag tag, HolderLookup.Provider registries) {
        displayItem = ItemStack.parseOptional(registries, tag.getCompound("preview"));
        var constellationId = ResourceLocation.tryParse(tag.getString("displayConstellation"));
        displayConstellation = constellationId == null ? null : RegistriesAS.REGISTRY_CONSTELLATIONS.get(constellationId);
    }

    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (level == null || level.isClientSide) return true;
        return level.getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .anyMatch(holder -> holder.value().getInputs().stream().anyMatch(input -> input.test(stack)));
    }

    public void inventoryChanged() {
        if (!loading) markForUpdate(false);
    }

    @Override public void receiveStarlight(ServerLevel server, StarlightTransmissionPacket packet) {
        if (Float.isFinite(packet.amount()) && packet.amount() > 0)
            receivedStarlight.put(packet.constellation(), server.getGameTime());
    }

    @Override public void serverTick(ServerLevel server) {
        var previousConstellation = displayConstellation;
        super.serverTick(server);
        updateFocalStarlight(server);
        if (!starlightRoutesInitialized) {
            // AS can cache this position as a block endpoint before our receiver registers.
            // Refresh after registration, including when the node was restored from world data.
            getNetworkNode().ifPresent(node -> StarlightTransmissionLevelHelper.getInstance().getHandler(server)
                    .ifPresent(handler -> {
                        clearLegacyCrystalStarlight(server);
                        handler.notifyNodeChange(node);
                        starlightRoutesInitialized = true;
                    }));
        }
        if (!mainNode.isReady()) {
            mainNode.setVisualRepresentation(ModContent.STARLIGHT_TRANSMUTATION_CHAMBER_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        receivedStarlight.values().removeIf(tick -> server.getGameTime() - tick > STARLIGHT_TIMEOUT);
        displayConstellation = receivedConstellation(null);
        if (mainNode.isOnline()) {
            exportOutputs();
            if (autoPull) balanceInputs();
        }
        process(server);
        var state = getBlockState();
        boolean working = status == Status.RUNNING;
        if (state.getValue(StarlightTransmutationBlock.WORKING) != working)
            server.setBlock(worldPosition, state.setValue(StarlightTransmutationBlock.WORKING, working), Block.UPDATE_CLIENTS);
        // World renderers also need the selected recipe's constellation when no GUI is open.
        if (previousConstellation != displayConstellation) markForUpdate(false);
    }

    private void updateFocalStarlight(ServerLevel server) {
        // Keep local focal light separate from transmitted packets so daytime,
        // covering the chamber or removing the focal point stops it immediately.
        focalConstellation = null;
        if (!DayTimeHelper.isNight(server)
                || server.getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.getX(), worldPosition.getZ())
                        > worldPosition.getY() + 1) return;
        var focalPoints = (FocalPointData) DataAS.DOMAIN_AS.getData(server, DataAS.KEY_FOCAL_POINT_DATA);
        focalConstellation = focalPoints.getNode(worldPosition).map(node -> node.getConstellation()).orElse(null);
    }

    private void clearLegacyCrystalStarlight(ServerLevel server) {
        // The removed compatibility patch persisted source data for ordinary crystals.
        // Undo that state for this chamber's sources when loading, without initializing
        // crystals or changing sources that still have their matching focal point.
        var focalPoints = (FocalPointData) DataAS.DOMAIN_AS.getData(server, DataAS.KEY_FOCAL_POINT_DATA);
        for (var source : StarlightNetworkLevelHelper.get(server).getSourceNodes()) {
            if (!(source instanceof FocusCrystalSourceNode crystal) || crystal.getConstellation().isEmpty()) continue;
            if (focalPoints.getNode(source.getNodePos())
                    .map(point -> point.getConstellation().equals(crystal.getConstellation().orElse(null))).orElse(false)) continue;
            if (StarlightTransmissionChain.makeChain(server, source).getReceiverEndpoints().stream()
                    .noneMatch(receiver -> receiver.getNodePos().equals(worldPosition))) continue;
            crystal.setConstellation(null);
            crystal.setCrystalProperties(CrystalAttributesComponent.defaultEmpty());
            crystal.markDirty(server);
        }
    }

    private void process(ServerLevel server) {
        var recipes = server.getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .sorted(Comparator.comparing((RecipeHolder<FocalCombineRecipe> holder) -> !holder.id().equals(recipeId))
                        .thenComparing(holder -> holder.id().toString())).toList();
        boolean anyInput = false, matchingInputs = false;
        RecipeHolder<FocalCombineRecipe> selected = null;
        int[] consumed = null;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) anyInput |= !inventory.getStackInSlot(slot).isEmpty();
        for (var holder : recipes) {
            var match = TransmutationPlan.match(holder.value().getInputs(), inventory, INPUT_SLOTS);
            if (match == null) continue;
            matchingInputs = true;
            if (availableConstellations().anyMatch(holder.value()::isRequiredConstellation)) {
                selected = holder;
                consumed = match;
                break;
            }
        }
        if (!anyInput || !matchingInputs) {
            resetProgress();
            status = anyInput ? Status.MISSING_INPUTS : Status.IDLE;
            return;
        }
        if (selected == null) {
            // Pause an existing job while the light source is interrupted.
            status = hasStarlight() ? Status.WRONG_CONSTELLATION : Status.NO_STARLIGHT;
            return;
        }
        if (!selected.id().equals(recipeId)) {
            resetProgress();
            recipeId = selected.id();
        }
        displayConstellation = receivedConstellation(selected.value());
        // Recipe reloads may change normal duration, but paid jobs always take 20 ticks.
        duration = jobOverclocked ? OVERCLOCK_DURATION : Math.max(1, selected.value().getDuration());
        if (!mainNode.isOnline()) { status = Status.OFFLINE; return; }
        var output = TransmutationPlan.output(selected.value().getOutputs(), inventory, INPUT_SLOTS, OUTPUT_SLOTS);
        if (output == null) { status = Status.OUTPUT_BLOCKED; return; }
        // Decide once at the start. Insufficient lumen falls back to the native recipe duration.
        if (progress == 0) {
            jobOverclocked = lumenOverclock && chargeOverclock();
            duration = jobOverclocked ? OVERCLOCK_DURATION : Math.max(1, selected.value().getDuration());
        }
        status = Status.RUNNING;
        progress = Math.min(progress + 1, duration);
        setChanged(false);
        if (progress < duration) return;
        // No inventory mutation happens until both the full input and full output plans exist.
        loading = true;
        try {
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                if (consumed[slot] > 0) {
                    var remaining = inventory.getStackInSlot(slot).copy();
                    remaining.shrink(consumed[slot]);
                    inventory.setStackInSlot(slot, remaining);
                }
            }
            for (int slot = 0; slot < OUTPUT_SLOTS; slot++) inventory.setStackInSlot(INPUT_SLOTS + slot, output[slot]);
        } finally { loading = false; }
        resetProgress();
        markForUpdate(false);
        exportOutputs();
    }

    private void resetProgress() {
        if (recipeId != null || progress != 0 || duration != 0) {
            recipeId = null;
            progress = duration = 0;
            if (jobOverclocked) overclockCharge = 0;
            jobOverclocked = false;
            setChanged(false);
        }
    }

    private boolean chargeOverclock() {
        int needed = OVERCLOCK_LUMEN_COST - overclockCharge;
        if (needed <= 0) return true;
        var grid = mainNode.getGrid();
        if (grid == null) return false;
        var storage = grid.getStorageService().getInventory();
        var key = LumenKey.of(LumenAS.AION.get());
        var source = IActionSource.ofMachine(this);
        if (storage.extract(key, needed, Actionable.SIMULATE, source) < needed) return false;
        // Retain any unexpected partial extraction from external storage for a later job.
        overclockCharge += (int) storage.extract(key, needed, Actionable.MODULATE, source);
        setChanged(false);
        return overclockCharge >= OVERCLOCK_LUMEN_COST;
    }

    private void exportOutputs() {
        var grid = mainNode.getGrid();
        if (grid == null || !mainNode.isOnline()) return;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        for (int slot = INPUT_SLOTS; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            long inserted = storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
        }
    }

    private void balanceInputs() {
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        var source = IActionSource.ofMachine(this);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            var marker = pullMarkers.getStackInSlot(slot);
            var current = inventory.getStackInSlot(slot);
            int target = marker.isEmpty() ? 0 : Math.min(marker.getCount(), marker.getMaxStackSize());
            boolean matches = !marker.isEmpty() && ItemStack.isSameItemSameComponents(current, marker);
            // Like an ME interface, return unwanted stock before trying to restock.
            // Only remove what ME accepted; a full network keeps the remainder here.
            int excess = current.isEmpty() ? 0 : matches ? Math.max(0, current.getCount() - target) : current.getCount();
            if (excess > 0) {
                long inserted = StorageHelper.poweredInsert(energy, storage, AEItemKey.of(current), excess, source);
                if (inserted > 0) inventory.extractItem(slot, (int) inserted, false);
                continue;
            }
            if (target == 0 || !accepts(marker)) continue;
            int missing = target - current.getCount();
            if (missing <= 0) continue;
            long extracted = StorageHelper.poweredExtraction(energy, storage, AEItemKey.of(marker), missing, source);
            if (extracted > 0) inventory.setStackInSlot(slot, marker.copyWithCount(current.getCount() + (int) extracted));
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
    @Override public void setRemoved() { mainNode.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { mainNode.destroy(); super.onChunkUnloaded(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putBoolean("autoPull", autoPull);
        tag.putBoolean("lumenOverclock", lumenOverclock);
        tag.putBoolean("jobOverclocked", jobOverclocked);
        tag.putInt("overclockCharge", overclockCharge);
        tag.put("pullMarkers", pullMarkers.serializeNBT(registries));
        tag.putInt("progress", progress);
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        loading = true;
        try {
            if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
            for (int slot = 0; slot < INPUT_SLOTS; slot++) pullMarkers.setStackInSlot(slot, ItemStack.EMPTY);
            if (tag.contains("pullMarkers")) pullMarkers.deserializeNBT(registries, tag.getCompound("pullMarkers"));
            autoPull = tag.getBoolean("autoPull");
            lumenOverclock = tag.getBoolean("lumenOverclock");
        } finally { loading = false; }
        recipeId = ResourceLocation.tryParse(tag.getString("recipe"));
        progress = recipeId == null ? 0 : Math.max(0, tag.getInt("progress"));
        jobOverclocked = recipeId != null && tag.getBoolean("jobOverclocked");
        overclockCharge = Math.clamp(tag.getInt("overclockCharge"), 0, OVERCLOCK_LUMEN_COST);
        duration = 0;
        receivedStarlight.clear();
        focalConstellation = null;
        displayConstellation = null;
    }
}
