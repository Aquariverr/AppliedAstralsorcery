package com.appliedastralsorcery.altar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarCraftingInput;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileFocusRelay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class AltarAutomationBlockEntity extends BlockEntity implements ICraftingMachine {
    public static final int RANGE = 16;
    private static final String LINK_TAG = "appliedas:automation";
    private final List<ItemStack> returns = new ArrayList<>();
    private BlockPos altarPos;
    private UUID jobId;
    private ResourceLocation recipeId;
    private Direction returnSide = Direction.DOWN;
    private int gridMask;
    private int relayMask;
    private boolean completed;

    // External inventories may extract returns but cannot insert partial processing jobs.
    private final IItemHandler output = new IItemHandler() {
        @Override public int getSlots() { return Math.max(1, returns.size()); }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < returns.size() ? returns.get(slot).copy() : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= returns.size() || amount <= 0) return ItemStack.EMPTY;
            var stored = returns.get(slot);
            var result = stored.copyWithCount(Math.min(amount, stored.getCount()));
            if (!simulate) {
                stored.shrink(result.getCount());
                // Keep indices stable during an external extraction pass.
                setChanged();
            }
            return result;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };

    public AltarAutomationBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.ALTAR_AUTOMATION_ENTITY.get(), pos, state);
    }

    public IItemHandler getOutput() { return output; }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(ModContent.ALTAR_AUTOMATION_ITEM.get()),
                Component.translatable("block.appliedas.altar_automation_interface"), List.of());
    }

    @Override
    public boolean acceptsPlans() {
        return level instanceof ServerLevel && jobId == null && returns.stream().allMatch(ItemStack::isEmpty);
    }

    /** Only loaded chunks participate; equal distances have a stable coordinate tie break. */
    public TileAltar findNearestAltar() {
        if (!(level instanceof ServerLevel server)) return null;
        TileAltar nearest = null;
        double distance = RANGE * RANGE + 1;
        for (int x = (worldPosition.getX() - RANGE) >> 4; x <= (worldPosition.getX() + RANGE) >> 4; x++) {
            for (int z = (worldPosition.getZ() - RANGE) >> 4; z <= (worldPosition.getZ() + RANGE) >> 4; z++) {
                var chunk = server.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof TileAltar altar) || entity.isRemoved()) continue;
                    double candidate = worldPosition.distSqr(altar.getBlockPos());
                    if (candidate <= RANGE * RANGE && (candidate < distance || candidate == distance
                            && nearest != null && altar.getBlockPos().compareTo(nearest.getBlockPos()) < 0)) {
                        distance = candidate;
                        nearest = altar;
                    }
                }
            }
        }
        return nearest;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction side) {
        if (!acceptsPlans() || side == null || !pattern.supportsPushInputsToExternalInventory()) return false;
        var altar = findNearestAltar();
        if (altar == null || altar.getTileData().getActiveRecipe().isPresent() || !altar.hasStructure()
                || hasReservation(altar)) return false;
        var candidates = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(RecipeTypesAS.ALTAR_CRAFTING_TYPE.get()));
        candidates.sort(Comparator.comparing(holder -> holder.id().toString()));
        for (var holder : candidates) {
            var plan = AltarRecipePlan.create(altar, holder, pattern, inputs);
            if (plan == null || !canPlace(altar, plan)) continue;
            // Time restrictions must hold before taking the materials; focus and tier were checked by the planner.
            if (holder.value().isOnlyNight()
                    && !hellfirepvp.astralsorcery.common.util.level.DayTimeHelper.isNight(level)) continue;
            altarPos = altar.getBlockPos().immutable();
            jobId = UUID.randomUUID();
            recipeId = holder.id();
            returnSide = side;
            completed = false;
            gridMask = relayMask = 0;
            var link = new CompoundTag();
            link.putLong("interface", worldPosition.asLong());
            link.putUUID("job", jobId);
            altar.getPersistentData().put(LINK_TAG, link);
            for (int i = 0; i < 9; i++) {
                var stack = plan.grid().get(i);
                if (!stack.isEmpty()) {
                    altar.getTileData().getAltarInventory().setStackInSlot(i, stack.copy());
                    gridMask |= 1 << i;
                }
            }
            for (int i = 0; i < 25; i++) {
                var stack = plan.relays().get(i);
                if (!stack.isEmpty()) {
                    relay(altar, i).getTileData().getInventory().setStackInSlot(0, stack.copy());
                    relayMask |= 1 << i;
                }
            }
            for (var stack : plan.additional()) {
                var item = new ItemEntity(level, altarPos.getX() + 0.5, altarPos.getY() + 1.2,
                        altarPos.getZ() + 0.5, stack.copy());
                item.setDeltaMovement(Vec3.ZERO);
                item.setNoGravity(true);
                item.setUnlimitedLifetime();
                item.setNoPickUpDelay();
                level.addFreshEntity(item);
            }
            altar.startCrafting(holder, jobId);
            altar.setChanged();
            for (var counter : inputs) counter.clear();
            setChanged();
            return true;
        }
        return false;
    }

    private boolean canPlace(TileAltar altar, AltarRecipePlan plan) {
        for (int i = 0; i < 9; i++) {
            if (!altar.getTileData().getAltarInventory().getStackInSlot(i).isEmpty()) return false;
        }
        for (int i = 0; i < 25; i++) {
            var offset = TileAltar.getRelayGridOffsets().get(i);
            if (offset == null) continue;
            if (!level.hasChunkAt(altar.getBlockPos().offset(offset))) return false;
            var relay = relay(altar, i);
            if (relay != null && !relay.getTileData().getInventory().getStackInSlot(0).isEmpty()) return false;
            if (!plan.relays().get(i).isEmpty() && relay == null) return false;
        }
        return true;
    }

    private TileFocusRelay relay(TileAltar altar, int slot) {
        var offset = TileAltar.getRelayGridOffsets().get(slot);
        if (offset == null || !level.hasChunkAt(altar.getBlockPos().offset(offset))) return null;
        return level.getBlockEntity(altar.getBlockPos().offset(offset)) instanceof TileFocusRelay relay ? relay : null;
    }

    private static boolean hasReservation(TileAltar altar) {
        var data = altar.getPersistentData().getCompound(LINK_TAG);
        if (!data.hasUUID("job")) return false;
        var pos = BlockPos.of(data.getLong("interface"));
        if (!altar.getLevel().hasChunkAt(pos)) return true;
        if (altar.getLevel().getBlockEntity(pos) instanceof AltarAutomationBlockEntity entity
                && data.getUUID("job").equals(entity.jobId)) return true;
        altar.getPersistentData().remove(LINK_TAG);
        altar.setChanged();
        return false;
    }

    /** An unloaded interface must not turn a network job into uncollected world drops. */
    public static boolean shouldPause(TileAltar altar) {
        var data = altar.getPersistentData().getCompound(LINK_TAG);
        var active = altar.getTileData().getActiveRecipe().orElse(null);
        if (!data.hasUUID("job") || active == null || !data.getUUID("job").equals(active.getPlayerUUID())) return false;
        var pos = BlockPos.of(data.getLong("interface"));
        return !altar.getLevel().hasChunkAt(pos)
                || altar.getLevel().getBlockEntity(pos) instanceof AltarAutomationBlockEntity entity
                && data.getUUID("job").equals(entity.jobId) && entity.completed;
    }

    /** Called only at the native recipe completion point, after all resource checks succeeded. */
    public static boolean collectCraftedOutput(AltarRecipe recipe, AltarCraftingInput input, HolderLookup.Provider registries) {
        var altar = input.getAltar();
        if (altar == null) return false;
        var link = altar.getPersistentData().getCompound(LINK_TAG);
        if (!link.hasUUID("job")) return false;
        var pos = BlockPos.of(link.getLong("interface"));
        if (!altar.getLevel().hasChunkAt(pos)
                || !(altar.getLevel().getBlockEntity(pos) instanceof AltarAutomationBlockEntity entity)
                || entity.completed || !link.getUUID("job").equals(entity.jobId)
                || !link.getUUID("job").equals(input.getPlayerUUID())
                || !altar.getBlockPos().equals(entity.altarPos)) return false;
        for (var stack : recipe.getOutputs(input, registries)) entity.queueReturn(stack);
        entity.completed = true;
        entity.setChanged();
        return true;
    }

    private void queueReturn(ItemStack stack) {
        var remaining = stack.copy();
        while (!remaining.isEmpty()) returns.add(remaining.split(remaining.getMaxStackSize()));
    }

    public void serverTick() {
        if (level == null || level.getGameTime() % 5 != 0) return;
        if (jobId != null && altarPos != null && level.hasChunkAt(altarPos)) {
            var altar = level.getBlockEntity(altarPos) instanceof TileAltar tile ? tile : null;
            if (altar == null) clearJob(); // The altar's own block removal drops its inventory.
            else if (completed) {
                // Wait for all relays to load before recovering their container remainders.
                for (int i = 0; i < 25; i++) {
                    var offset = TileAltar.getRelayGridOffsets().get(i);
                    if ((relayMask & 1 << i) != 0 && offset != null
                            && !level.hasChunkAt(altarPos.offset(offset))) return;
                }
                if (altar.getTileData().getActiveRecipe().filter(active -> jobId.equals(active.getPlayerUUID())).isPresent()) {
                    altar.abortCrafting();
                }
                for (int i = 0; i < 9; i++) {
                    if ((gridMask & 1 << i) != 0) {
                        var inventory = altar.getTileData().getAltarInventory();
                        queueReturn(inventory.getStackInSlot(i));
                        inventory.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
                for (int i = 0; i < 25; i++) {
                    var relay = (relayMask & 1 << i) == 0 ? null : relay(altar, i);
                    if (relay != null) {
                        queueReturn(relay.getTileData().getInventory().getStackInSlot(0));
                        relay.getTileData().getInventory().setStackInSlot(0, ItemStack.EMPTY);
                    }
                }
                altar.getPersistentData().remove(LINK_TAG);
                altar.setChanged();
                clearJob();
            } else if (altar.getTileData().getActiveRecipe().isEmpty() && altar.hasStructure()) {
                // Night or structure loss can abort AS crafting. Resume the same batch when valid again.
                var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
                if (holder != null && holder.value() instanceof AltarRecipe recipe
                        && recipe.matches(altar.createInput(level, jobId), level)) {
                    altar.startCrafting(new RecipeHolder<>(holder.id(), recipe), jobId);
                }
            }
        }
        var destination = worldPosition.relative(returnSide);
        if (!level.hasChunkAt(destination)) return;
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, destination, returnSide.getOpposite());
        if (handler != null) {
            for (int i = 0; i < returns.size(); i++) {
                var stack = returns.get(i);
                if (stack.isEmpty()) continue;
                var remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
                if (remainder.getCount() != stack.getCount()) {
                    returns.set(i, remainder);
                    setChanged();
                }
            }
        }
        returns.removeIf(ItemStack::isEmpty);
    }

    private void clearJob() {
        jobId = null;
        altarPos = null;
        recipeId = null;
        completed = false;
        gridMask = relayMask = 0;
        setChanged();
    }

    public Component getStatus() {
        if (jobId != null) return Component.translatable("message.appliedas.altar.working",
                altarPos.getX(), altarPos.getY(), altarPos.getZ());
        if (returns.stream().anyMatch(stack -> !stack.isEmpty())) return Component.translatable("message.appliedas.altar.return_blocked");
        var altar = findNearestAltar();
        return altar == null ? Component.translatable("message.appliedas.altar.not_found", RANGE)
                : Component.translatable("message.appliedas.altar.ready", altar.getBlockPos().getX(),
                        altar.getBlockPos().getY(), altar.getBlockPos().getZ());
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (var stack : returns) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        returns.clear();
        // Already delivered inputs stay in the altar/relays; native crafting can still finish normally.
        clearJob();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var items = new ListTag();
        for (var stack : returns) if (!stack.isEmpty()) items.add(stack.save(registries));
        tag.put("returns", items);
        tag.putInt("returnSide", returnSide.ordinal());
        if (jobId != null) {
            tag.putUUID("job", jobId);
            tag.putLong("altar", altarPos.asLong());
            tag.putString("recipe", recipeId.toString());
            tag.putBoolean("completed", completed);
            tag.putInt("gridMask", gridMask);
            tag.putInt("relayMask", relayMask);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        returns.clear();
        var items = tag.getList("returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) ItemStack.parse(registries, items.getCompound(i)).ifPresent(returns::add);
        returnSide = Direction.from3DDataValue(tag.getInt("returnSide"));
        jobId = tag.hasUUID("job") ? tag.getUUID("job") : null;
        altarPos = jobId == null ? null : BlockPos.of(tag.getLong("altar"));
        recipeId = jobId == null ? null : ResourceLocation.tryParse(tag.getString("recipe"));
        completed = tag.getBoolean("completed");
        gridMask = tag.getInt("gridMask");
        relayMask = tag.getInt("relayMask");
        if (recipeId == null) { jobId = null; altarPos = null; }
    }
}
