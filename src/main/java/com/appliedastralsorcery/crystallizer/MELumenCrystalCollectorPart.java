package com.appliedastralsorcery.crystallizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import com.appliedastralsorcery.AppliedAstralsorcery;
import hellfirepvp.astralsorcery.common.block.tile.LumenCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.tile.TileLumenCrystalCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Harvests one native lumen-crystal growth stage at a time, always preserving stage zero. */
public final class MELumenCrystalCollectorPart extends AEBasePart implements IGridTickable {
    private static final IPartModel MODEL = new PartModel(ResourceLocation.fromNamespaceAndPath(
            AppliedAstralsorcery.MOD_ID, "part/me_lumen_crystal_collector"));
    private final List<ItemStack> pendingDrops = new ArrayList<>();

    public MELumenCrystalCollectorPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2)
                .addService(IGridTickable.class, this);
    }

    public static void registerModels() {
        getModels().forEach(model -> PartModels.registerModels(model.getModels()));
    }

    @appeng.items.parts.PartModels
    public static List<IPartModel> getModels() { return List.of(MODEL); }

    @Override public IPartModel getStaticModels() { return MODEL; }

    @Override public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(1, 1, 14, 15, 15, 16);
        helper.addBox(4, 4, 13, 12, 12, 14); // cable nub on the back, like AE2 monitors
    }

    public BlockPos getCollectionTarget() {
        var side = Objects.requireNonNull(getSide(), "Collector must be attached to a cable face");
        return getBlockEntity().getBlockPos().relative(side);
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(5, 5, false);
    }

    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        serverTick();
        return TickRateModulation.SAME;
    }

    public void serverTick() {
        if (!(getLevel() instanceof ServerLevel level) || !getMainNode().isOnline()) return;
        var grid = getMainNode().getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        var source = IActionSource.ofMachine(this);
        if (!pendingDrops.isEmpty()) {
            if (flushDrops(storage, source)) getHost().markForSave();
            return;
        }

        var target = getCollectionTarget();
        if (!level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return;
        var state = level.getBlockState(target);
        if (!state.is(BlocksAS.LUMEN_CRYSTAL_CLUSTER.get())
                || state.getValue(LumenCrystalClusterBlock.STAGE) <= 0
                || !(level.getBlockEntity(target) instanceof TileLumenCrystalCluster cluster)) return;
        var node = getMainNode().getNode();
        if (node == null) return;
        var player = Platform.getFakePlayer(level, node.getOwningPlayerProfileId());
        if (!level.mayInteract(player, target)
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, target, state, player)).isCanceled()) return;
        // Protection/event handlers may have changed the block; only harvest the exact cluster we checked.
        if (level.getBlockState(target) != state || level.getBlockEntity(target) != cluster) return;

        // The native loot table copies the crystal's lumen component. Never synthesize untyped items.
        var drops = Block.getDrops(state, level, target, cluster, player, ItemStack.EMPTY).stream()
                .filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        if (drops.isEmpty() || !canStoreAll(storage, source, drops)) return;
        if (!level.setBlock(target, state.setValue(LumenCrystalClusterBlock.STAGE,
                state.getValue(LumenCrystalClusterBlock.STAGE) - 1), Block.UPDATE_ALL)) return;

        // Commit loot once. If storage acceptance changed since simulation, retain the remainder across saves/drops.
        pendingDrops.addAll(drops);
        getHost().markForSave();
        flushDrops(storage, source);
        getHost().markForSave();
        level.levelEvent(2001, target, Block.getId(state));
    }

    private static boolean canStoreAll(MEStorage storage, IActionSource source, List<ItemStack> drops) {
        var totals = new LinkedHashMap<AEItemKey, Long>();
        for (var stack : drops) totals.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
        for (var entry : totals.entrySet()) {
            if (storage.insert(entry.getKey(), entry.getValue(), Actionable.SIMULATE, source) < entry.getValue())
                return false;
        }
        return true;
    }

    private boolean flushDrops(MEStorage storage, IActionSource source) {
        boolean changed = false;
        var iterator = pendingDrops.iterator();
        while (iterator.hasNext()) {
            var stack = iterator.next();
            int inserted = (int) storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            if (inserted > 0) { stack.shrink(inserted); changed = true; }
            if (stack.isEmpty()) iterator.remove();
        }
        return changed;
    }

    private CompoundTag saveDrops(HolderLookup.Provider registries) {
        var data = new CompoundTag();
        var drops = new ListTag();
        pendingDrops.forEach(stack -> drops.add(stack.save(registries)));
        data.put("outputs", drops);
        return data;
    }

    private void loadDrops(CompoundTag data, HolderLookup.Provider registries) {
        pendingDrops.clear();
        for (var element : data.getList("outputs", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.parseOptional(registries, (CompoundTag) element);
            if (!stack.isEmpty()) pendingDrops.add(stack);
        }
    }

    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.put("collector", saveDrops(registries));
    }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        loadDrops(data.getCompound("collector"), registries);
    }

    @Override public void exportSettings(SettingsFrom mode, DataComponentMap.Builder output) {
        super.exportSettings(mode, output);
        if (mode == SettingsFrom.DISMANTLE_ITEM && getLevel() != null) {
            var data = new CompoundTag();
            data.put("collector", saveDrops(getLevel().registryAccess()));
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }

    @Override public void importSettings(SettingsFrom mode, DataComponentMap input, Player player) {
        super.importSettings(mode, input, player);
        var settings = input.get(DataComponents.CUSTOM_DATA);
        if (mode == SettingsFrom.DISMANTLE_ITEM && settings != null && getLevel() != null) {
            loadDrops(settings.copyTag().getCompound("collector"), getLevel().registryAccess());
            getHost().markForSave();
        }
    }
}
