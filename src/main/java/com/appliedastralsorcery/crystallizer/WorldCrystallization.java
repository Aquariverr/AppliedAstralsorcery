package com.appliedastralsorcery.crystallizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.block.tile.LumenCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenCrystallizationRecipe;
import hellfirepvp.astralsorcery.common.tile.TileLumenCrystalCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Shared by cable parts and legacy panels; committed growth lives in the actual world block. */
public final class WorldCrystallization {
    public static final int SEED_CHANCE = 1200;
    public static final int GROWTH_CHANCE = 6000;
    public static final int MATURE_STAGE = 4;

    private ItemStack marker = ItemStack.EMPTY;
    private ResourceLocation recipeId;
    private Lumen lumen;
    private ItemStack catalyst = ItemStack.EMPTY;
    private ItemStack heldCatalyst = ItemStack.EMPTY;
    private int operationCost;
    private float shatterMultiplier;
    private int storedLumen;
    private int ticks;
    private BlockPos clusterPosition;
    private boolean returning;
    private int legacyStage = -1;
    private final List<ItemStack> pendingItems = new ArrayList<>();

    public ItemStack getMarker() { return marker.copy(); }

    public RecipeHolder<LumenCrystallizationRecipe> findRecipe(Level level, ItemStack stack) {
        if (level == null || stack.isEmpty()) return null;
        return level.getRecipeManager().getAllRecipesFor(RecipeTypesAS.LUMEN_CRYSTALLIZATION_TYPE.get()).stream()
                .filter(holder -> holder.value().getInput().test(stack))
                .min(Comparator.comparing(holder -> holder.id().toString())).orElse(null);
    }

    public Lumen markCatalyst(Level level, ItemStack stack) {
        var recipe = findRecipe(level, stack);
        if (recipe == null) return null;
        if (!ItemStack.isSameItemSameComponents(marker, stack) || !recipe.id().equals(recipeId))
            cancelUnformed();
        marker = stack.copyWithCount(1);
        recipeId = recipe.id();
        return recipe.value().getLumenToCrystallize();
    }

    public void clearMarker() {
        cancelUnformed();
        marker = ItemStack.EMPTY;
        recipeId = null;
    }

    private void cancelUnformed() {
        if (clusterPosition == null && legacyStage < 0 && lumen != null) beginReturn();
    }

    private void beginReturn() {
        if (!heldCatalyst.isEmpty()) pendingItems.add(heldCatalyst.copy());
        heldCatalyst = ItemStack.EMPTY;
        returning = true;
    }

    /** Random uses nextInt(bound). No catalyst or operation cost is committed before world placement succeeds. */
    public boolean tick(ServerLevel level, BlockPos target, MEStorage storage, IActionSource source,
            IntUnaryOperator random) {
        boolean changed = flushItems(storage, source);
        if (returning) return returnResources(storage, source) || changed;
        if (!pendingItems.isEmpty() || !level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return changed;

        var state = level.getBlockState(target);
        var cluster = level.getBlockEntity(target) instanceof TileLumenCrystalCluster tile ? tile : null;
        if (lumen == null) {
            if (marker.isEmpty() || recipeId == null) return changed;
            var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null || !(holder.value() instanceof LumenCrystallizationRecipe recipe)
                    || !recipe.getInput().test(marker) || recipe.getLumenConsumedPerOperation() < 1
                    || recipe.getLumenConsumedPerOperation() > 1900) return changed;
            boolean existing = cluster != null && cluster.getTileData().getLumen() == recipe.getLumenToCrystallize()
                    && state.getValue(LumenCrystalClusterBlock.STAGE) < MATURE_STAGE;
            if (!existing && (!state.isAir() || cannotForm(level, target))) return changed;
            lumen = recipe.getLumenToCrystallize();
            operationCost = recipe.getLumenConsumedPerOperation();
            shatterMultiplier = recipe.getCatalystShatterMultiplier();
            catalyst = marker.copyWithCount(1);
            clusterPosition = existing ? target.immutable() : null;
            changed = true;
        }

        // Old versions kept a paid-for crystal only in NBT. Materialize it once, never over another block.
        if (legacyStage >= 0) {
            if (!state.isAir() || cannotForm(level, target)) return changed;
            if (placeCluster(level, target, legacyStage)) {
                clusterPosition = target.immutable();
                legacyStage = -1;
                return true;
            }
            return changed;
        }

        if (clusterPosition != null) {
            if (!clusterPosition.equals(target) || cluster == null || cluster.getTileData().getLumen() != lumen) {
                // A removed/replaced crystal is authoritative. Never recreate its recorded stage after mining or moving.
                beginReturn();
                return true;
            }
            if (state.getValue(LumenCrystalClusterBlock.STAGE) >= MATURE_STAGE) {
                beginReturn();
                return returnResources(storage, source) || changed;
            }
        } else if (!state.isAir() || cannotForm(level, target)) {
            return changed;
        }

        if (clusterPosition == null && heldCatalyst.isEmpty()) {
            var key = AEItemKey.of(catalyst);
            if (key == null || storage.extract(key, 1, Actionable.SIMULATE, source) < 1
                    || storage.extract(key, 1, Actionable.MODULATE, source) < 1) return changed;
            heldCatalyst = catalyst.copyWithCount(1);
            changed = true;
        }
        int required = operationCost + 100;
        int extracted = (int) storage.extract(LumenKey.of(lumen), Math.max(0, required - storedLumen),
                Actionable.MODULATE, source);
        if (extracted > 0) { storedLumen += extracted; changed = true; }
        if (storedLumen < 2) return changed;
        int available = storedLumen;
        ticks = (ticks + 1) % 20;
        if (ticks == 0) storedLumen -= 2;
        changed = true;

        if (clusterPosition == null && shatterMultiplier > 0
                && random.applyAsInt(Math.max(1, Mth.ceil(1200F / shatterMultiplier))) == 0) {
            heldCatalyst = ItemStack.EMPTY;
            return true;
        }
        if (available < required || random.applyAsInt(clusterPosition == null ? SEED_CHANCE : GROWTH_CHANCE) != 0)
            return changed;

        if (clusterPosition == null) {
            if (placeCluster(level, target, 0)) {
                clusterPosition = target.immutable();
                var remainder = heldCatalyst.getCraftingRemainingItem();
                if (!remainder.isEmpty()) pendingItems.add(remainder.copy());
                heldCatalyst = ItemStack.EMPTY;
            } else {
                return changed;
            }
        } else if (!level.setBlock(target, state.setValue(LumenCrystalClusterBlock.STAGE,
                state.getValue(LumenCrystalClusterBlock.STAGE) + 1), 3)) {
            return changed;
        }
        storedLumen -= operationCost;
        flushItems(storage, source);
        return true;
    }

    private static boolean cannotForm(ServerLevel level, BlockPos target) {
        return !BlocksAS.LUMEN_CRYSTAL_CLUSTER.get().defaultBlockState().canSurvive(level, target);
    }

    private boolean placeCluster(ServerLevel level, BlockPos target, int stage) {
        if (!level.getBlockState(target).isAir() || cannotForm(level, target)) return false;
        var state = BlocksAS.LUMEN_CRYSTAL_CLUSTER.get().defaultBlockState()
                .setValue(LumenCrystalClusterBlock.STAGE, stage);
        if (!level.setBlock(target, state, 3)
                || !(level.getBlockEntity(target) instanceof TileLumenCrystalCluster cluster)) return false;
        cluster.getTileData().setLumen(lumen);
        cluster.getTileData().markForUpdate();
        return true;
    }

    private boolean flushItems(MEStorage storage, IActionSource source) {
        boolean changed = false;
        var iterator = pendingItems.iterator();
        while (iterator.hasNext()) {
            var stack = iterator.next();
            int returned = (int) storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            if (returned > 0) { stack.shrink(returned); changed = true; }
            if (stack.isEmpty()) iterator.remove();
        }
        return changed;
    }

    private boolean returnResources(MEStorage storage, IActionSource source) {
        boolean changed = flushItems(storage, source);
        if (lumen != null && storedLumen > 0) {
            int returned = (int) storage.insert(LumenKey.of(lumen), storedLumen, Actionable.MODULATE, source);
            if (returned > 0) { storedLumen -= returned; changed = true; }
        }
        if (storedLumen == 0 && pendingItems.isEmpty()) { clearCycle(); return true; }
        return changed;
    }

    private void clearCycle() {
        lumen = null;
        catalyst = ItemStack.EMPTY;
        heldCatalyst = ItemStack.EMPTY;
        operationCost = storedLumen = ticks = 0;
        shatterMultiplier = 0;
        clusterPosition = null;
        returning = false;
        legacyStage = -1;
        pendingItems.clear();
    }

    public CompoundTag save(HolderLookup.Provider registries, boolean contents) {
        var tag = new CompoundTag();
        if (!marker.isEmpty()) tag.put("marker", marker.save(registries));
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        if (!contents) return tag;
        var job = new CompoundTag();
        if (lumen != null) job.putString("lumen", lumen.getRegistryKey().orElseThrow().location().toString());
        if (!catalyst.isEmpty()) job.put("catalyst", catalyst.save(registries));
        if (!heldCatalyst.isEmpty()) job.put("held", heldCatalyst.save(registries));
        job.putInt("cost", operationCost);
        job.putFloat("shatter", shatterMultiplier);
        job.putInt("stored", storedLumen);
        job.putInt("ticks", ticks);
        if (clusterPosition != null) job.putLong("cluster_pos", clusterPosition.asLong());
        job.putBoolean("returning", returning);
        job.putInt("legacy_stage", legacyStage);
        var items = new ListTag();
        pendingItems.forEach(stack -> items.add(stack.save(registries)));
        job.put("outputs", items);
        tag.put("world_job", job);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries, boolean contents) {
        var nextMarker = ItemStack.parseOptional(registries, tag.getCompound("marker"));
        var nextRecipe = ResourceLocation.tryParse(tag.getString("recipe"));
        if (!contents && (!ItemStack.isSameItemSameComponents(marker, nextMarker)
                || !Objects.equals(recipeId, nextRecipe))) cancelUnformed();
        marker = nextMarker.isEmpty() ? ItemStack.EMPTY : nextMarker.copyWithCount(1);
        recipeId = nextRecipe;
        if (!contents) return;
        clearCycle();
        boolean legacy = !tag.contains("world_job", Tag.TAG_COMPOUND);
        var job = tag.getCompound(legacy ? "job" : "world_job");
        var id = ResourceLocation.tryParse(job.getString("lumen"));
        lumen = id == null ? null : RegistriesAS.REGISTRY_LUMEN.get(id);
        if (lumen == LumenAS.NONE.get()) lumen = null;
        catalyst = ItemStack.parseOptional(registries, job.getCompound("catalyst"));
        heldCatalyst = ItemStack.parseOptional(registries, job.getCompound("held"));
        operationCost = Math.clamp(job.getInt("cost"), 1, 1900);
        shatterMultiplier = Math.max(0, job.getFloat("shatter"));
        storedLumen = Math.clamp(job.getInt("stored"), 0, operationCost + 100);
        ticks = Math.floorMod(job.getInt("ticks"), 20);
        returning = job.getBoolean(legacy ? "finished" : "returning");
        if (!legacy && job.contains("cluster_pos")) clusterPosition = BlockPos.of(job.getLong("cluster_pos"));
        String stageKey = legacy ? "stage" : "legacy_stage";
        legacyStage = job.contains(stageKey) && !returning
                ? Math.clamp(job.getInt(stageKey), -1, MATURE_STAGE) : -1;
        for (var element : job.getList("outputs", Tag.TAG_COMPOUND)) {
            var stack = ItemStack.parseOptional(registries, (CompoundTag) element);
            if (!stack.isEmpty()) pendingItems.add(stack);
        }
        if (lumen == null) { storedLumen = 0; legacyStage = -1; beginReturn(); }
    }

    public static boolean hasSupportingPanel(LevelReader level, BlockPos clusterPos) {
        for (var side : Direction.values()) {
            var hostPos = clusterPos.relative(side);
            if (level.getChunk(hostPos.getX() >> 4, hostPos.getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
            var blockEntity = level.getBlockEntity(hostPos);
            if (blockEntity instanceof WorldCrystallizerHost panel
                    && panel.getCrystallizationTarget().equals(clusterPos)) return true;
            if (blockEntity instanceof IPartHost host
                    && host.getPart(side.getOpposite()) instanceof WorldCrystallizerHost panel
                    && panel.getCrystallizationTarget().equals(clusterPos)) return true;
        }
        return false;
    }
}
