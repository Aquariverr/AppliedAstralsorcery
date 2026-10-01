package com.appliedastralsorcery.chisel;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.crystal.CrystalPropertyGenerator;
import hellfirepvp.astralsorcery.common.item.crystal.RockCrystalItem;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenHandlerViewFactory;
import hellfirepvp.astralsorcery.common.lumen.capability.LumenStackList;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenRequestHelper;
import hellfirepvp.astralsorcery.common.util.LumenUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class AutoChiselBlockEntity extends BlockEntity {
    public static final int OUTPUT_SLOTS = 9;
    public static final int WORK_TICKS = 40;
    public static final int LUMEN_COST = 25;
    public static final int LUMEN_CAPACITY = 2000;
    private static final Direction[] OUTPUT_PRIORITY = {
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public enum SideMode {
        // Append new modes so existing saves retain their input/output ordinals.
        INPUT, OUTPUT, INPUT_OUTPUT, NONE;
        public boolean allowsInput() { return this == INPUT || this == INPUT_OUTPUT; }
        public boolean allowsOutput() { return this == OUTPUT || this == INPUT_OUTPUT; }
        public SideMode cycle(boolean reverse) {
            return values()[Math.floorMod(ordinal() + (reverse ? -1 : 1), values().length)];
        }
        public Component label() {
            return Component.translatable("gui.appliedas.chisel.side." + name().toLowerCase(Locale.ROOT));
        }
    }

    public enum Status {
        IDLE, UNSPLITTABLE, OUTPUT_FULL, NO_LUMEN, WORKING, NO_OUTPUT, OUTPUT_BLOCKED;
        public Component label() {
            return Component.translatable("gui.appliedas.chisel.status." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final SideMode[] sideModes = new SideMode[6];
    private final EnumMap<Direction, IItemHandler> sidedItems = new EnumMap<>(Direction.class);
    private int progress;
    private boolean droppedItemMode;
    private boolean roundRobin;
    private boolean autoInput = true;
    private boolean autoOutput = true;
    private UUID activeTarget;
    private ItemStack activeInput = ItemStack.EMPTY;
    private UUID lastTarget;
    private Status dropStatus = Status.IDLE;
    private final ItemStackHandler inventory = new ItemStackHandler(1 + OUTPUT_SLOTS) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && ChiselProcessing.accepts(stack);
        }
        @Override protected void onContentsChanged(int slot) {
            inventoryChanged(slot);
        }
    };
    private final LumenStackList lumenContents = LumenStackList.create();
    private final ILumenHandler lumen = LumenHandlerViewFactory.builder()
            .tankCapacity(type -> type == LumenAS.EVORSIO.get() ? LUMEN_CAPACITY : 0)
            .inputFilter((incoming, stored) -> incoming.is(LumenAS.EVORSIO.get()))
            .onChange(type -> setChanged()).createView(lumenContents);
    private final IItemHandler unsidedItems = new SidedItems(null);

    public AutoChiselBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.AUTO_CHISEL_ENTITY.get(), pos, state);
        Arrays.fill(sideModes, SideMode.INPUT);
        sideModes[Direction.UP.ordinal()] = SideMode.INPUT;
        sideModes[Direction.DOWN.ordinal()] = SideMode.OUTPUT;
        for (var side : Direction.values()) sidedItems.put(side, new SidedItems(side));
    }

    public ItemStackHandler getInventory() { return inventory; }
    public ILumenHandler getLumenHandler() { return lumen; }
    public IItemHandler getItemHandler(Direction side) { return side == null ? unsidedItems : sidedItems.get(side); }
    public int getLumenAmount() { return lumenContents.getLumenStack(LumenAS.EVORSIO.get()).map(LumenStack::getAmount).orElse(0); }
    public int getProgress() { return progress; }
    public int getFortuneLevel() {
        // BlockItem imports these components on placement; BlockEntity persists them with the world.
        return level == null ? 0 : components().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(level.holderOrThrow(Enchantments.FORTUNE));
    }
    public SideMode getSideMode(Direction side) { return sideModes[side.ordinal()]; }
    public boolean isDroppedItemMode() { return droppedItemMode; }
    public boolean isRoundRobin() { return roundRobin; }
    public boolean isAutoInput() { return autoInput; }
    public boolean isAutoOutput() { return autoOutput; }
    public void toggleAutoInput() { autoInput = !autoInput; setChanged(); }
    public void toggleAutoOutput() { autoOutput = !autoOutput; setChanged(); }

    public void toggleDroppedItemMode() {
        droppedItemMode = !droppedItemMode;
        resetDropJob();
        invalidateCapabilities();
    }

    public void toggleRoundRobin() {
        roundRobin = !roundRobin;
        lastTarget = null;
        resetDropJob();
    }

    private void resetDropJob() {
        progress = 0;
        activeTarget = null;
        activeInput = ItemStack.EMPTY;
        dropStatus = Status.IDLE;
        setChanged();
    }

    void inventoryChanged(int slot) {
        // Slot shift-clicks can mutate a stack in place without calling ItemStackHandler's setters.
        if (slot == 0 && !droppedItemMode) progress = 0;
        setChanged();
    }

    public void cycleSide(Direction side) {
        cycleSide(side, false);
    }

    public void cycleSide(Direction side, boolean reverse) {
        sideModes[side.ordinal()] = getSideMode(side).cycle(reverse);
        // Cached views also check the current mode on every call.
        invalidateCapabilities();
        if (droppedItemMode) resetDropJob();
        setChanged();
    }

    public Status getStatus() {
        if (droppedItemMode) return dropStatus;
        if (inventory.getStackInSlot(0).isEmpty()) return Status.IDLE;
        if (!ChiselProcessing.canProcess(inventory.getStackInSlot(0))) return Status.UNSPLITTABLE;
        if (!hasOutputRoom()) return Status.OUTPUT_FULL;
        if (getLumenAmount() < LUMEN_COST) return Status.NO_LUMEN;
        return Status.WORKING;
    }

    // Reserve two empty slots before rolling random outputs: blocked jobs cannot reroll or lose products.
    private boolean hasOutputRoom() {
        int empty = 0;
        for (int slot = 1; slot <= OUTPUT_SLOTS; slot++) if (inventory.getStackInSlot(slot).isEmpty()) empty++;
        return empty >= 2;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        // Refill in either mode, even while idle or blocked; processing must not gate network requests.
        requestLumen(server);
        if (droppedItemMode) {
            tickDroppedItems(server);
            return;
        }
        if (server.getGameTime() % 5 == 0) transferItems();
        var input = inventory.getStackInSlot(0);
        if (input.getItem() instanceof RockCrystalItem) {
            var attributes = input.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            if (attributes != null && attributes.isEmpty()) {
                input.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, CrystalPropertyGenerator.generateRandomProperties(attributes));
                setChanged();
            }
        }
        if (!ChiselProcessing.canProcess(input)) {
            if (progress != 0) { progress = 0; setChanged(); }
            return;
        }
        if (!hasOutputRoom()) return;
        if (getLumenAmount() < LUMEN_COST) return;
        progress++;
        setChanged();
        if (progress < WORK_TICKS) return;
        List<ItemStack> results = ChiselProcessing.process(input, server.random, getFortuneLevel());
        if (results.isEmpty()) { progress = 0; return; }
        // All products fit in the slots reserved above; mutation stays within this server tick.
        lumen.drain(LumenAS.EVORSIO.get(), LUMEN_COST, ILumenHandler.Action.EXECUTE);
        inventory.extractItem(0, 1, false);
        for (var result : results) {
            for (int slot = 1; slot <= OUTPUT_SLOTS; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    inventory.setStackInSlot(slot, result);
                    break;
                }
            }
        }
        progress = 0;
    }

    private void requestLumen(ServerLevel server) {
        if (server.getGameTime() % 20 == 0 && getLumenAmount() < LUMEN_CAPACITY) {
            var request = LumenAS.EVORSIO.stack(Math.min(200, LUMEN_CAPACITY - getLumenAmount()));
            // Same nearby relay graph and line-of-sight checks used by native AS processing machines.
            LumenRequestHelper.requestRelayed(server, worldPosition, request).ifPresent(chain -> {
                var received = LumenUtil.tryChainTransfer(lumen, server, chain, request, ILumenHandler.Action.EXECUTE);
                if (!received.isEmpty()) chain.playTransferEffect(server, received.getLumen());
            });
        }
    }

    /** Only item centers inside a loaded, adjacent input block qualify. */
    private boolean isInDropInput(Vec3 position) {
        for (var side : Direction.values()) {
            var pos = worldPosition.relative(side);
            if (getSideMode(side).allowsInput() && level.hasChunkAt(pos) && new AABB(pos).contains(position)) return true;
        }
        return false;
    }

    private boolean isDropCandidate(ItemEntity entity) {
        if (!entity.isAlive() || !isInDropInput(entity.position())) return false;
        var stack = entity.getItem();
        if (ChiselProcessing.canProcess(stack)) return true;
        var attributes = stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
        return stack.getItem() instanceof RockCrystalItem && attributes != null && attributes.isEmpty();
    }

    private ItemEntity selectDrop(ServerLevel server) {
        var candidates = new ArrayList<ItemEntity>();
        for (var side : Direction.values()) {
            var pos = worldPosition.relative(side);
            if (!getSideMode(side).allowsInput() || !server.hasChunkAt(pos)) continue;
            var area = new AABB(pos);
            candidates.addAll(server.getEntitiesOfClass(ItemEntity.class, area,
                    entity -> area.contains(entity.position()) && isDropCandidate(entity)));
        }
        candidates.sort(Comparator.comparing(ItemEntity::getUUID));
        if (candidates.isEmpty()) return null;
        if (!roundRobin && lastTarget != null) {
            for (var candidate : candidates) if (candidate.getUUID().equals(lastTarget)) return candidate;
        }
        if (roundRobin && lastTarget != null) {
            for (var candidate : candidates) if (candidate.getUUID().compareTo(lastTarget) > 0) return candidate;
        }
        return candidates.getFirst();
    }

    private ItemEntity currentDrop(ServerLevel server) {
        if (activeTarget == null) return null;
        if (server.getEntity(activeTarget) instanceof ItemEntity entity && isDropCandidate(entity)
                && ItemStack.matches(activeInput, entity.getItem())) return entity;
        return null;
    }

    private Direction availableDropOutput(ServerLevel server) {
        for (var side : OUTPUT_PRIORITY) {
            var pos = worldPosition.relative(side);
            if (!getSideMode(side).allowsOutput() || !server.hasChunkAt(pos)) continue;
            var center = pos.getCenter();
            var bounds = new AABB(center.x - 0.125, center.y, center.z - 0.125,
                    center.x + 0.125, center.y + 0.25, center.z + 0.125);
            if (server.getWorldBorder().isWithinBounds(bounds) && server.noCollision(bounds)) return side;
        }
        return null;
    }

    private void tickDroppedItems(ServerLevel server) {
        // A moving, picked-up, merged or externally edited target cannot donate progress to another item.
        var target = currentDrop(server);
        if (target == null) {
            if (activeTarget != null || progress != 0) resetDropJob();
            else dropStatus = Status.IDLE;
            target = selectDrop(server);
            if (target == null) return;
            var input = target.getItem().copy();
            var attributes = input.get(DataComponentsAS.CRYSTAL_ATTRIBUTES);
            if (input.getItem() instanceof RockCrystalItem && attributes != null && attributes.isEmpty()) {
                input.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, CrystalPropertyGenerator.generateRandomProperties(attributes));
                target.setItem(input);
            }
            if (!ChiselProcessing.canProcess(input)) return;
            activeTarget = target.getUUID();
            activeInput = input.copy();
        }
        if (Arrays.stream(sideModes).noneMatch(SideMode::allowsOutput)) {
            dropStatus = Status.NO_OUTPUT;
            return;
        }
        var outputSide = availableDropOutput(server);
        if (outputSide == null) {
            dropStatus = Status.OUTPUT_BLOCKED;
            return;
        }
        if (getLumenAmount() < LUMEN_COST) { dropStatus = Status.NO_LUMEN; return; }
        dropStatus = Status.WORKING;
        progress++;
        setChanged();
        if (progress < WORK_TICKS) return;
        var results = ChiselProcessing.process(target.getItem(), server.random, getFortuneLevel());
        if (results.isEmpty()) { resetDropJob(); return; }
        if (!spawnDropResults(server, outputSide, results)) {
            // Entity-join cancellation rolls back all products; retain the input and lumen.
            progress = WORK_TICKS - 1;
            dropStatus = Status.OUTPUT_BLOCKED;
            return;
        }
        var remainder = target.getItem().copy();
        remainder.shrink(1);
        if (remainder.isEmpty()) target.discard();
        else target.setItem(remainder);
        lumen.drain(LumenAS.EVORSIO.get(), LUMEN_COST, ILumenHandler.Action.EXECUTE);
        lastTarget = target.getUUID();
        resetDropJob();
    }

    private boolean spawnDropResults(ServerLevel server, Direction side, List<ItemStack> results) {
        Vec3 position = worldPosition.relative(side).getCenter();
        var spawned = new ArrayList<ItemEntity>();
        for (var stack : results) {
            var entity = new ItemEntity(server, position.x, position.y, position.z, stack.copy());
            // Create native crystal/artifact entities synchronously, avoiding NeoForge's queued replacement.
            if (stack.getItem().hasCustomEntity(stack)) {
                var custom = stack.getItem().createEntity(server, entity, entity.getItem());
                if (!(custom instanceof ItemEntity item)) {
                    spawned.forEach(ItemEntity::discard);
                    return false;
                }
                entity = item;
            }
            entity.setPos(position);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setDefaultPickUpDelay();
            if (!server.addFreshEntity(entity)) {
                spawned.forEach(ItemEntity::discard);
                return false;
            }
            spawned.add(entity);
        }
        return true;
    }

    private void transferItems() {
        if (!autoInput && !autoOutput) return;
        for (var side : Direction.values()) {
            var mode = getSideMode(side);
            var neighborPos = worldPosition.relative(side);
            if ((!autoInput || !mode.allowsInput()) && (!autoOutput || !mode.allowsOutput())
                    || !level.hasChunkAt(neighborPos)) continue;
            var neighbor = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, side.getOpposite());
            if (neighbor == null) continue;
            // Evaluate both directions independently for a shared input/output face.
            if (autoOutput && mode.allowsOutput()) {
                for (int slot = 1; slot <= OUTPUT_SLOTS; slot++) {
                    var stored = inventory.getStackInSlot(slot);
                    if (stored.isEmpty()) continue;
                    var remainder = ItemHandlerHelper.insertItemStacked(neighbor, stored.copy(), false);
                    if (remainder.getCount() != stored.getCount()) inventory.setStackInSlot(slot, remainder);
                }
            }
            if (autoInput && mode.allowsInput() && inventory.getStackInSlot(0).isEmpty()) {
                for (int slot = 0; slot < neighbor.getSlots(); slot++) {
                    var candidate = neighbor.extractItem(slot, 1, true);
                    if (candidate.isEmpty() || !inventory.insertItem(0, candidate, true).isEmpty()) continue;
                    var extracted = neighbor.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) inventory.setStackInSlot(0, extracted);
                    break;
                }
            }
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("lumen", getLumenAmount());
        tag.putInt("progress", progress);
        tag.putIntArray("sides", Arrays.stream(sideModes).mapToInt(Enum::ordinal).toArray());
        tag.putInt("sideModeVersion", 2);
        tag.putBoolean("autoInput", autoInput);
        tag.putBoolean("autoOutput", autoOutput);
        tag.putBoolean("droppedItemMode", droppedItemMode);
        tag.putBoolean("roundRobin", roundRobin);
        if (lastTarget != null) tag.putUUID("lastTarget", lastTarget);
        if (activeTarget != null && !activeInput.isEmpty()) {
            tag.putUUID("activeTarget", activeTarget);
            tag.put("activeInput", activeInput.save(registries));
        }
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        lumenContents.clear();
        lumenContents.setLumenStack(LumenAS.EVORSIO.stack(Math.clamp(tag.getInt("lumen"), 0, LUMEN_CAPACITY)));
        progress = Math.clamp(tag.getInt("progress"), 0, WORK_TICKS - 1);
        int[] savedSides = tag.getIntArray("sides");
        for (int i = 0; i < Math.min(savedSides.length, sideModes.length); i++) {
            if (tag.getInt("sideModeVersion") < 2) {
                // Legacy ordinals were off=0, input=1, output=2. Off faces migrate to input, never new outputs.
                sideModes[i] = savedSides[i] == 2 ? SideMode.OUTPUT : SideMode.INPUT;
            } else {
                sideModes[i] = savedSides[i] >= 0 && savedSides[i] < SideMode.values().length
                        ? SideMode.values()[savedSides[i]] : SideMode.INPUT;
            }
        }
        autoInput = !tag.contains("autoInput") || tag.getBoolean("autoInput");
        autoOutput = !tag.contains("autoOutput") || tag.getBoolean("autoOutput");
        droppedItemMode = tag.getBoolean("droppedItemMode");
        roundRobin = tag.getBoolean("roundRobin");
        lastTarget = tag.hasUUID("lastTarget") ? tag.getUUID("lastTarget") : null;
        activeTarget = tag.hasUUID("activeTarget") ? tag.getUUID("activeTarget") : null;
        activeInput = ItemStack.parseOptional(registries, tag.getCompound("activeInput"));
        dropStatus = Status.IDLE;
        if (droppedItemMode && (activeTarget == null || activeInput.isEmpty())) progress = 0;
    }

    private final class SidedItems implements IItemHandler {
        private final Direction side;
        private SidedItems(Direction side) { this.side = side; }
        private boolean allows(SideMode mode) {
            return !droppedItemMode && (side == null || (mode == SideMode.INPUT
                    ? getSideMode(side).allowsInput() : getSideMode(side).allowsOutput()));
        }
        @Override public int getSlots() { return inventory.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) {
            return (slot == 0 ? allows(SideMode.INPUT) : allows(SideMode.OUTPUT))
                    ? inventory.getStackInSlot(slot).copy() : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? inventory.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot > 0 && slot <= OUTPUT_SLOTS && allows(SideMode.OUTPUT)
                    ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && allows(SideMode.INPUT) && inventory.isItemValid(slot, stack);
        }
    }
}
