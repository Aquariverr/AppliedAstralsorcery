package com.appliedastralsorcery.infuser;

import javax.annotation.Nonnull;

import java.util.EnumSet;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.recipe.infusion.ActiveInfusionRecipe;
import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Native infusion, with an automated input and a persistent ME output buffer. */
public final class MEStarlightInfuserBlockEntity extends TileInfuser implements IGridConnectedBlockEntity {
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this,
            (owner, node) -> owner.setChanged())
            .setInWorldNode(true).setExposedOnSides(EnumSet.allOf(Direction.class))
            .setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1.0);
    private ItemStack output = ItemStack.EMPTY;
    // Normally extraction is all-or-nothing after simulation. Retain any partial extraction from
    // third-party storage providers so a changed provider can never destroy fluid or craft for free.
    private FluidStack reservedFluid = FluidStack.EMPTY;
    private final IItemHandler inventory = new IItemHandler() {
        @Override public int getSlots() { return 2; }
        @Override @Nonnull public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? getTileData().getInventory().getStackInSlot(0).copy()
                    : slot == 1 ? output.copy() : ItemStack.EMPTY;
        }
        @Override @Nonnull public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || getTileData().getActiveRecipe().isPresent()) return stack;
            return getTileData().getInventory().insertItem(0, stack, simulate);
        }
        @Override @Nonnull public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            if (slot == 0) return getTileData().getActiveRecipe().isPresent() ? ItemStack.EMPTY
                    : getTileData().getInventory().extractItem(0, amount, simulate);
            if (slot != 1 || output.isEmpty()) return ItemStack.EMPTY;
            var extracted = output.copyWithCount(Math.min(amount, output.getCount()));
            if (!simulate) { output.shrink(extracted.getCount()); setChanged(); }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 64; }
        @Override public boolean isItemValid(int slot, @Nonnull ItemStack stack) { return slot == 0 && !stack.isEmpty(); }
    };

    public MEStarlightInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.STARLIGHT_INFUSER_ENTITY), pos, state);
    }

    @Override public IManagedGridNode getMainNode() { return mainNode; }
    @Override public IGridNode getGridNode(Direction side) { return mainNode.getNode(); }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    @Override public void saveChanges() { setChanged(); }
    public IItemHandler getInventory() { return inventory; }

    @Override public void serverTick(ServerLevel server) {
        if (!mainNode.isReady()) {
            mainNode.setVisualRepresentation(ModContent.ME_STARLIGHT_INFUSER_ITEM.get());
            mainNode.create(server, worldPosition);
        }
        if (server.getGameTime() % 5 == 0) {
            exportOutput();
            if (getTileData().getActiveRecipe().isEmpty()) returnReservedFluid();
        }
        // Native ticking retains recipe duration, liquid ring validation, effects and cancellation.
        super.serverTick(server);
        if (output.isEmpty() && getTileData().getActiveRecipe().isEmpty()
                && !getTileData().getInventory().getStackInSlot(0).isEmpty() && hasStructure()) {
            findMatchingRecipe(server).ifPresent(recipe -> startCrafting(server, recipe));
        }
    }

    @Override public void startCrafting(Level level, RecipeHolder<InfusionRecipe> recipe) {
        if (output.isEmpty()) super.startCrafting(level, recipe);
    }

    /** Called at the original completion point, replacing only the dropped output. */
    public void collectOutput(ItemStack stack) {
        output = stack.copy();
        setChanged();
        exportOutput();
    }

    private void exportOutput() {
        var grid = mainNode.getGrid();
        if (!mainNode.isOnline() || grid == null || output.isEmpty()) return;
        long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(output),
                output.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this));
        if (inserted > 0) { output.shrink((int) inserted); setChanged(); }
    }

    /** Keeps the native chalice cost and world-fluid probability, adding ME between the two. */
    public boolean consumeInfusionInputs(ActiveInfusionRecipe active, InfusionRecipe recipe, Level level) {
        if (recipe.getFluidConsumptionChance() <= 0) return true;
        var required = recipe.getChaliceInputFluidStack();
        var draw = active.getDrawInstance();
        if (reservedFluid.isEmpty() && draw.consumeLiquid(level, worldPosition, required, true))
            return draw.consumeLiquid(level, worldPosition, required, false);
        if (consumeNetworkFluid(required)) return true;
        // A partial provider extraction is held for this payment, rather than charging the pools too.
        if (!reservedFluid.isEmpty()) return false;
        return recipe.consumeInputs(createInput(level), level.registryAccess());
    }

    private boolean consumeNetworkFluid(FluidStack required) {
        if (required.isEmpty()) return true;
        if (!reservedFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(reservedFluid, required)) {
            returnReservedFluid();
            if (!reservedFluid.isEmpty()) return false;
        }
        int missing = required.getAmount() - reservedFluid.getAmount();
        var grid = mainNode.getGrid();
        if (missing > 0 && mainNode.isOnline() && grid != null) {
            var storage = grid.getStorageService().getInventory();
            var key = AEFluidKey.of(required);
            var source = IActionSource.ofMachine(this);
            if (storage.extract(key, missing, Actionable.SIMULATE, source) == missing) {
                long extracted = storage.extract(key, missing, Actionable.MODULATE, source);
                if (extracted > 0) {
                    reservedFluid = required.copyWithAmount(reservedFluid.getAmount() + (int) extracted);
                    setChanged();
                }
            }
        }
        if (reservedFluid.getAmount() < required.getAmount()) return false;
        reservedFluid.shrink(required.getAmount());
        setChanged();
        return true;
    }

    private void returnReservedFluid() {
        var grid = mainNode.getGrid();
        if (reservedFluid.isEmpty() || !mainNode.isOnline() || grid == null) return;
        long inserted = grid.getStorageService().getInventory().insert(AEFluidKey.of(reservedFluid),
                reservedFluid.getAmount(), Actionable.MODULATE, IActionSource.ofMachine(this));
        if (inserted > 0) { reservedFluid.shrink((int) inserted); setChanged(); }
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        var input = getTileData().getInventory();
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), input.getStackInSlot(0));
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output);
        input.setStackInSlot(0, ItemStack.EMPTY);
        output = ItemStack.EMPTY;
        returnReservedFluid();
        setChanged();
    }

    @Override public void setRemoved() { mainNode.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { mainNode.destroy(); super.onChunkUnloaded(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        if (!output.isEmpty()) tag.put("meInfuserOutput", output.save(registries));
        if (!reservedFluid.isEmpty()) tag.put("meInfuserReservedFluid", reservedFluid.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        output = ItemStack.parseOptional(registries, tag.getCompound("meInfuserOutput"));
        reservedFluid = FluidStack.parseOptional(registries, tag.getCompound("meInfuserReservedFluid"));
    }
}
