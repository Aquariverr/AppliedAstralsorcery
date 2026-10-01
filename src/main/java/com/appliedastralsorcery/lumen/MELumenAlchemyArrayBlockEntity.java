package com.appliedastralsorcery.lumen;

import java.util.HashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.appliedastralsorcery.ModContent;
import com.mojang.serialization.Codec;
import hellfirepvp.astralsorcery.common.block.tile.LumenArrayBlock;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenGenerationRecipe;
import hellfirepvp.astralsorcery.common.research.ResearchHelper;
import hellfirepvp.astralsorcery.common.research.ResearchMessageHelper;
import hellfirepvp.astralsorcery.common.util.RecipeUtil;
import hellfirepvp.astralsorcery.common.util.data.TileRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.crafting.RecipeHolder;

/** A source-only AS array: combination ingredients are obtained exclusively from ME. */
public final class MELumenAlchemyArrayBlockEntity extends MELumenArrayBlockEntity {
    // Keep partially extracted inputs if an ME storage changes between simulation and execution.
    // They are never exposed as output, and survive reloads and pending output-type switches.
    private final Map<Lumen, Integer> ingredients = new HashMap<>();

    public MELumenAlchemyArrayBlockEntity(BlockPos pos, BlockState state) {
        super(new TileRegistryObject<>(ModContent.ALCHEMY_ARRAY_ENTITY), pos, state);
    }
    @Override public boolean isAlchemyArray() { return true; }
    @Override public Codec<Data> dataCodec() { return MELumenAlchemyArrayData.CODEC; }
    @Override protected boolean hasPendingIngredients() { return !ingredients.isEmpty(); }

    @Override protected void returnPreviousContents() {
        var grid = getMainNode().getGrid();
        if (grid != null) returnIngredients(grid.getStorageService().getInventory(), IActionSource.ofMachine(this), Map.of());
        super.returnPreviousContents();
    }

    /** Called only for this subclass by the native crafting-cycle hook. All other native ticks still run. */
    public void craftFromNetwork(ServerLevel server, java.util.function.ToIntFunction<LumenGenerationRecipe> generationAttempts) {
        var data = (MELumenAlchemyArrayData) getTileData();
        var catalyst = data.getInventory().getStackInSlot(0);
        if (data.getContainedFluid().isEmpty() || catalyst.isEmpty()) {
            breakCatalyst();
            return;
        }
        if (server.getGameTime() % 20 != 0) return;
        var recipe = data.findMatchingRecipe(catalyst).map(RecipeHolder::value).orElse(null);
        if (recipe == null) return;

        int attempts = generationAttempts.applyAsInt(recipe);
        boolean enabled = getBlockState().getValue(LumenArrayBlock.ENABLED);
        int starlightAttempts = 2;
        if (!enabled) {
            attempts = rand.nextFloat() >= 0.95F ? 1 : 0;
            starlightAttempts = 0;
        } else if (attempts > 0 && getMainNode().isOnline() && getMainNode().getGrid() != null) {
            int produced = produce(recipe, attempts, getMainNode().getGrid().getStorageService().getInventory(),
                    IActionSource.ofMachine(this));
            if (produced > 0) {
                data.getOwner(server).ifPresent(player -> {
                    var discovered = ResearchHelper.discoverLumen(player, RecipeUtil.findAnyLumenMakingUp(server, recipe.getProducedLumen()));
                    if (!discovered.isEmpty()) ResearchMessageHelper.sendLumenDiscovery(player, discovered);
                });
            }
        }
        // Match AS's idle/full/disabled consumption and catalyst wear as well as its fill-dependent attempts.
        int consumption = Math.max(attempts > 0 ? 1 : 0,
                Mth.ceil(Math.max(starlightAttempts, attempts) * recipe.getAttemptStarlightConsumption()));
        if (consumption > 0) data.consumeStarlight(consumption);
        if (recipe.getCatalystShatterMultiplier() > 0) {
            int chance = Mth.ceil(1500F * (1F / recipe.getCatalystShatterMultiplier()));
            if (rand.nextInt(Math.max(chance, 1)) == 0) breakCatalyst();
        }
    }

    /** Completes whole recipe units only; bounded preflight checks avoid wasting partial ingredients. */
    int produce(LumenGenerationRecipe recipe, int attempts, MEStorage storage, IActionSource source) {
        int output = recipe.getProducedLumenAmount();
        var required = recipe.getLumenCombinationInputs();
        if (output <= 0 || attempts <= 0 || required.isEmpty() || required.values().stream().anyMatch(n -> n <= 0)) return 0;
        int units = Math.min(attempts, fillGeneratedLumen(recipe.getProducedLumen().stack(Data.LUMEN_TANK_CAPACITY),
                ILumenHandler.Action.SIMULATE) / output);
        for (int amount : required.values()) units = Math.min(units, Integer.MAX_VALUE / amount);
        if (units <= 0) return 0;
        returnIngredients(storage, source, required);
        for (var input : required.entrySet()) {
            long stored = ingredients.getOrDefault(input.getKey(), 0);
            long missing = Math.max(0L, (long) units * input.getValue() - stored);
            long available = stored + storage.extract(LumenKey.of(input.getKey()), missing, Actionable.SIMULATE, source);
            units = (int) Math.min(units, available / input.getValue());
            if (units == 0) return 0;
        }
        for (var input : required.entrySet()) {
            int stored = ingredients.getOrDefault(input.getKey(), 0);
            long missing = Math.max(0L, (long) units * input.getValue() - stored);
            int extracted = (int) storage.extract(LumenKey.of(input.getKey()), missing, Actionable.MODULATE, source);
            if (extracted > 0) {
                ingredients.put(input.getKey(), stored + extracted);
                setChanged();
            }
        }
        // Actual storage may have supplied fewer units than advertised. Preserve every remainder for retry.
        for (var input : required.entrySet()) units = Math.min(units,
                ingredients.getOrDefault(input.getKey(), 0) / input.getValue());
        if (units <= 0) return 0;
        int produced = fillGeneratedLumen(recipe.getProducedLumen().stack(units * output), ILumenHandler.Action.EXECUTE);
        int completed = produced / output;
        for (var input : required.entrySet()) {
            int left = ingredients.getOrDefault(input.getKey(), 0) - completed * input.getValue();
            if (left == 0) ingredients.remove(input.getKey()); else ingredients.put(input.getKey(), left);
        }
        setChanged();
        return produced;
    }

    private void returnIngredients(MEStorage storage, IActionSource source, Map<Lumen, Integer> keep) {
        var iterator = ingredients.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (keep.containsKey(entry.getKey())) continue;
            int accepted = (int) storage.insert(LumenKey.of(entry.getKey()), entry.getValue(), Actionable.MODULATE, source);
            if (accepted > 0) {
                if (accepted == entry.getValue()) iterator.remove(); else entry.setValue(entry.getValue() - accepted);
                setChanged();
            }
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var pending = new CompoundTag();
        ingredients.forEach((lumen, amount) -> pending.putInt(lumen.getRegistryKey().orElseThrow().location().toString(), amount));
        tag.put("meAlchemyIngredients", pending);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ingredients.clear();
        var pending = tag.getCompound("meAlchemyIngredients");
        for (var name : pending.getAllKeys()) {
            var id = ResourceLocation.tryParse(name);
            var lumen = id == null ? null : RegistriesAS.REGISTRY_LUMEN.get(id);
            if (lumen != null && lumen != LumenAS.NONE.get() && pending.getInt(name) > 0)
                ingredients.put(lumen, pending.getInt(name));
        }
    }
}
