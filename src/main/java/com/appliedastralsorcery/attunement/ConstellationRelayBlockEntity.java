package com.appliedastralsorcery.attunement;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModConfig;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ConstellationRelayBlockEntity extends BlockEntity {
    private static final int EVENT_ATTUNEMENT_COMPLETE = 1;

    /** Client implementation is installed at client setup; dedicated servers never load FX classes. */
    public interface ClientEffects {
        ClientEffects NONE = new ClientEffects() {};
        default void tick() {}
        default void finish() {}
        default void stop() {}
    }

    @Nullable private ClientEffects clientEffects;
    private long progressUpdateTime;
    private int station = -1;
    @Nullable private BlockPos altarPos;
    private int progress;
    private ItemStack activeInput = ItemStack.EMPTY;
    private boolean visible;
    private boolean working;
    private boolean usingLumen;
    private boolean lumenRequired;
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return AttunementProcessing.accepts(stack); }
        @Override protected void onContentsChanged(int slot) {
            progress = 0;
            activeInput = ItemStack.EMPTY;
            working = false;
            usingLumen = false;
            lumenRequired = false;
            sync();
        }
    };

    public ConstellationRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CONSTELLATION_RELAY_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getInventory() { return inventory; }
    public int getProgress() { return progress; }
    public boolean isVisible() { return visible; }
    public boolean isWorking() { return working; }
    public boolean isUsingLumen() { return usingLumen; }
    public float getStarMapRotation() {
        if (station < 0 || station >= AttunementLayout.STATIONS.size()) return 0;
        var offset = AttunementLayout.STATIONS.get(station).offset();
        // The top of the unrotated map points north; turn it away from the altar.
        return (float) Math.atan2(-offset.getX(), -offset.getZ());
    }

    public float getVisualProgress(float partialTick) {
        if (level == null || !level.isClientSide || !working) return progress;
        double elapsed = Math.max(0, level.getGameTime() - progressUpdateTime + partialTick);
        return (float) Math.min(ModConfig.ATTUNEMENT_TICKS.get() - 1.0, progress + elapsed);
    }

    private ClientEffects clientEffects() {
        if (clientEffects == null) clientEffects = ((ConstellationRelayBlock) getBlockState().getBlock()).createClientEffects(this);
        return clientEffects;
    }

    public void clientTick() { clientEffects().tick(); }

    @Override public boolean triggerEvent(int id, int data) {
        if (id == EVENT_ATTUNEMENT_COMPLETE) {
            if (level != null && level.isClientSide) {
                clientEffects().finish();
                if (altarPos != null && level.getBlockEntity(altarPos) instanceof IridescentAttunementBlockEntity altar)
                    altar.onStationFinished(station);
            }
            return true;
        }
        return super.triggerEvent(id, data);
    }

    @Override public void setRemoved() {
        if (clientEffects != null) {
            clientEffects.stop();
            clientEffects = null;
        }
        super.setRemoved();
    }
    @Nullable public BaseConstellation getConstellation() {
        return station < 0 || station >= AttunementLayout.STATIONS.size() ? null
                : AttunementLayout.STATIONS.get(station).constellation().get();
    }

    /** Ambiguous layouts cannot let two altars power the same relay. */
    @Nullable private IridescentAttunementBlockEntity findAltar() {
        if (level == null) return null;
        if (altarPos != null && !level.hasChunk(altarPos.getX() >> 4, altarPos.getZ() >> 4)) return null;
        IridescentAttunementBlockEntity found = null;
        int index = -1;
        for (int i = 0; i < AttunementLayout.STATIONS.size(); i++) {
            var center = worldPosition.subtract(AttunementLayout.STATIONS.get(i).offset());
            if (level.hasChunk(center.getX() >> 4, center.getZ() >> 4)
                    && level.getBlockEntity(center) instanceof IridescentAttunementBlockEntity altar) {
                if (found != null) {
                    bind(null, -1);
                    return null;
                }
                found = altar;
                index = i;
            }
        }
        bind(found == null ? null : found.getBlockPos(), index);
        return found;
    }

    private void bind(@Nullable BlockPos center, int index) {
        if (station != index || !java.util.Objects.equals(altarPos, center)) {
            station = index;
            altarPos = center;
            progress = 0;
            activeInput = ItemStack.EMPTY;
            lumenRequired = false;
            sync();
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        var altar = findAltar();
        var constellation = getConstellation();
        boolean formed = altar != null && constellation != null && altar.hasStructure();
        boolean natural = formed && AttunementProcessing.isVisible(server, altar.getBlockPos(), constellation);
        boolean nextWorking = false;
        if (formed && AttunementProcessing.accepts(inventory.getStackInSlot(0))) {
            var input = inventory.getStackInSlot(0);
            if (!ItemStack.isSameItemSameComponents(activeInput, input)) {
                progress = 0;
                activeInput = input.copyWithCount(1);
                lumenRequired = false;
                setChanged();
            }
            // Remember any use of lumen across sky changes and reloads, then charge once on completion.
            // An entirely natural attunement still works with an empty tank.
            int cost = lumenRequired || !natural ? ModConfig.ATTUNEMENT_LUMEN_PER_CRAFT.get() : 0;
            if (cost == 0 || altar.consumeLumen(cost, true)) {
                int duration = ModConfig.ATTUNEMENT_TICKS.get();
                int next = Math.min(progress, duration - 1) + 1;
                // Keep both the input and the entire fee if the output cannot enter the world.
                if (next < duration || spawnResult(server, constellation)) {
                    if (next == duration && cost > 0) altar.consumeLumen(cost, false);
                    progress = next < duration ? next : 0;
                    lumenRequired = next < duration && cost > 0;
                    nextWorking = next < duration;
                    if (next == duration) server.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_ATTUNEMENT_COMPLETE, 0);
                    setChanged();
                }
            }
        }
        // Displaying a star map does not make its constellation naturally available for free attunement.
        boolean nextVisible = formed && (natural || altar.getLumenAmount() > 0);
        boolean nextUsingLumen = nextWorking && !natural;
        if (visible != nextVisible || working != nextWorking || usingLumen != nextUsingLumen
                || nextWorking && server.getGameTime() % 20 == 0) {
            visible = nextVisible;
            working = nextWorking;
            usingLumen = nextUsingLumen;
            sync();
        }
    }

    private boolean spawnResult(ServerLevel server, BaseConstellation constellation) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.05;
        double z = worldPosition.getZ() + 0.5;
        var bounds = new AABB(x - 0.125, y, z - 0.125, x + 0.125, y + 0.25, z + 0.125);
        if (!server.getWorldBorder().isWithinBounds(bounds) || !server.noCollision(bounds)) return false;
        var output = AttunementProcessing.attune(server, inventory.getStackInSlot(0), constellation);
        if (output.isEmpty()) return false;
        net.minecraft.world.entity.Entity entity = new ItemEntity(server, x, y, z, output);
        // NeoForge otherwise cancels a vanilla ItemEntity spawn and queues a replacement.
        // Construct the custom crystal entity first so that cancellation really means failure,
        // rather than retrying a successful replacement and duplicating the output every tick.
        if (output.getItem().hasCustomEntity(output)) {
            var custom = output.getItem().createEntity(server, entity, output);
            if (custom != null) entity = custom;
        }
        entity.setDeltaMovement(0, 0.3, 0);
        if (entity instanceof ItemEntity item) item.setDefaultPickUpDelay();
        if (!server.addFreshEntity(entity)) return false;
        inventory.extractItem(0, 1, false);
        return true;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        var stack = inventory.extractItem(0, 64, false);
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, stack);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("progress", progress);
        if (!activeInput.isEmpty()) tag.put("activeInput", activeInput.save(registries));
        tag.putInt("station", station);
        if (altarPos != null) tag.putLong("altar", altarPos.asLong());
        tag.putBoolean("visible", visible);
        tag.putBoolean("working", working);
        tag.putBoolean("usingLumen", usingLumen);
        tag.putBoolean("lumenRequired", lumenRequired);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        progress = Math.max(0, tag.getInt("progress"));
        activeInput = ItemStack.parseOptional(registries, tag.getCompound("activeInput"));
        int previousStation = station;
        station = tag.contains("station") ? tag.getInt("station") : -1;
        altarPos = tag.contains("altar") ? BlockPos.of(tag.getLong("altar")) : null;
        visible = tag.getBoolean("visible");
        working = tag.getBoolean("working");
        usingLumen = working && tag.getBoolean("usingLumen");
        lumenRequired = progress > 0 && tag.getBoolean("lumenRequired");
        progressUpdateTime = tag.getLong("progressTime");
        // The lens is tinted with the constellation colour, which lives in the chunk mesh.
        if (level != null && level.isClientSide && station != previousStation)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        tag.putLong("progressTime", level == null ? 0 : level.getGameTime());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
