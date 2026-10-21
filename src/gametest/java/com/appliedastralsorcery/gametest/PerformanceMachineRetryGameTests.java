package com.appliedastralsorcery.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chisel.AutoChiselBlockEntity;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import hellfirepvp.astralsorcery.common.recipe.lumen.LumenGenerationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceMachineRetryGameTests {
    @GameTest(template = "attunement_test")
    public static void catalystIndexTracksStockVariantsSelectionAndReloads(GameTestHelper helper)
            throws ReflectiveOperationException {
        var type = Class.forName("com.appliedastralsorcery.lumen.LumenCatalystSearch");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var search = constructor.newInstance();
        var find = method(type, "candidates", Level.class, Lumen.class, boolean.class, KeyCounter.class);
        var manager = helper.getLevel().getRecipeManager();
        var original = new ArrayList<>(manager.getRecipes());
        var id = ResourceLocation.fromNamespaceAndPath("appliedas", "performance_catalyst");
        var ordinary = AEItemKey.of(Items.DIAMOND);
        var named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("catalyst"));
        var namedKey = AEItemKey.of(named);
        var available = new KeyCounter();
        available.add(ordinary, 1);
        available.add(namedKey, 1);
        for (int i = 0; i < 100; i++) {
            var unrelated = new ItemStack(Items.PAPER);
            unrelated.set(DataComponents.CUSTOM_NAME, Component.literal("unrelated " + i));
            available.add(AEItemKey.of(unrelated), 1);
        }
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(id, lumenRecipe(Ingredient.of(Items.DIAMOND)))));
            var candidates = (List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), false, available);
            helper.assertTrue(candidates.size() == 2 && candidates.contains(ordinary) && candidates.contains(namedKey),
                    "Simple catalyst lookup must return all component variants and exclude 100 unrelated keys");
            available.remove(ordinary);
            candidates = (List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), false, available);
            helper.assertTrue(candidates.size() == 1 && candidates.contains(namedKey),
                    "The recipe index must not cache network stock amounts");
            helper.assertTrue(((List<?>) find.invoke(search, helper.getLevel(), LumenAS.AION.get(), false, available)).isEmpty(),
                    "Changing selected lumen must invalidate catalyst types");
            helper.assertTrue(((List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), true, available)).isEmpty(),
                    "Alchemy searches must not reuse ordinary generation recipes");

            manager.replaceRecipes(List.of(new RecipeHolder<>(id, lumenRecipe(new HiddenComponentIngredient().toVanilla()))));
            candidates = (List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), false, available);
            helper.assertTrue(candidates.size() == 1 && candidates.contains(namedKey),
                    "Custom component ingredients must find accepted items absent from their display alternatives");
            var replacementNetwork = new KeyCounter();
            replacementNetwork.add(AEItemKey.of(Items.EMERALD), 1);
            manager.replaceRecipes(List.of(new RecipeHolder<>(id, lumenRecipe(Ingredient.of(Items.EMERALD)))));
            candidates = (List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), false, replacementNetwork);
            helper.assertTrue(candidates.size() == 1 && candidates.contains(AEItemKey.of(Items.EMERALD)),
                    "Recipe replacement and a different network snapshot must take effect immediately");
            manager.replaceRecipes(List.of());
            helper.assertTrue(((List<?>) find.invoke(search, helper.getLevel(), LumenAS.EVORSIO.get(), false, replacementNetwork)).isEmpty(),
                    "Deleted catalyst recipes must not remain cached");
        } finally {
            manager.replaceRecipes(original);
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void blockedOutputCacheDetectsInPlaceEditsAndRecipeReplacement(GameTestHelper helper)
            throws ReflectiveOperationException {
        var sample = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).getFirst().value();
        var recipe = new OutputCountingRecipe(sample, new ItemStack(Items.DIAMOND));
        var type = Class.forName("com.appliedastralsorcery.transmutation.TransmutationOutputCache");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var cache = constructor.newInstance();
        var plan = method(type, "plan", FocalCombineRecipe.class, ItemStackHandler.class);
        var inventory = new ItemStackHandler(18);
        for (int i = 9; i < 18; i++) inventory.setStackInSlot(i, new ItemStack(Items.DIAMOND, 64));
        for (int i = 0; i < 20; i++) helper.assertTrue(plan.invoke(cache, recipe, inventory) == null, "Full output must stay blocked");
        helper.assertTrue(recipe.outputChecks == 1, "An unchanged blocked inventory must be planned only once");
        inventory.getStackInSlot(9).shrink(1);
        helper.assertTrue(plan.invoke(cache, recipe, inventory) != null && recipe.outputChecks == 2,
                "An in-place count edit must immediately unblock the output plan");
        inventory.getStackInSlot(9).set(DataComponents.CUSTOM_NAME, Component.literal("different components"));
        helper.assertTrue(plan.invoke(cache, recipe, inventory) == null && recipe.outputChecks == 3,
                "An in-place component edit must invalidate previously available merge space");
        var replacement = new OutputCountingRecipe(sample, inventory.getStackInSlot(9).copyWithCount(1));
        helper.assertTrue(plan.invoke(cache, replacement, inventory) != null && replacement.outputChecks == 1,
                "A reloaded recipe object must invalidate the old blocked plan");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void chiselSelectsStableAndRoundRobinTargetsWithoutSorting(GameTestHelper helper)
            throws ReflectiveOperationException {
        var chisel = chisel(helper);
        var select = method(AutoChiselBlockEntity.class, "selectDrop", ServerLevel.class);
        var last = AutoChiselBlockEntity.class.getDeclaredField("lastTarget");
        last.setAccessible(true);
        var middle = drop(helper, chisel, 2);
        var high = drop(helper, chisel, 3);
        var low = drop(helper, chisel, 1);
        helper.assertTrue(select.invoke(chisel, helper.getLevel()) == low, "Initial selection must use the lowest UUID");
        last.set(chisel, middle.getUUID());
        helper.assertTrue(select.invoke(chisel, helper.getLevel()) == middle, "Stable mode must retain its previous target");
        chisel.toggleRoundRobin();
        last.set(chisel, middle.getUUID());
        helper.assertTrue(select.invoke(chisel, helper.getLevel()) == high, "Round robin must choose the next UUID");
        last.set(chisel, high.getUUID());
        helper.assertTrue(select.invoke(chisel, helper.getLevel()) == low, "Round robin must wrap to the lowest UUID");
        low.discard(); middle.discard(); high.discard();
        helper.succeed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 30)
    public static void idleChiselResumesWithinFiveTicks(GameTestHelper helper) throws ReflectiveOperationException {
        var chisel = chisel(helper);
        chisel.serverTick();
        var deadline = AutoChiselBlockEntity.class.getDeclaredField("nextDropSearchTick");
        deadline.setAccessible(true);
        helper.assertTrue(deadline.getLong(chisel) == helper.getLevel().getGameTime() + 5,
                "An empty search must schedule exactly five ticks of backoff");
        drop(helper, chisel, 4);
        chisel.serverTick();
        helper.assertTrue(chisel.getStatus() == AutoChiselBlockEntity.Status.IDLE,
                "The same-tick empty search must not be repeated");
        helper.runAfterDelay(5, () -> {
            chisel.serverTick();
            helper.assertTrue(chisel.getStatus() == AutoChiselBlockEntity.Status.NO_LUMEN,
                    "New input must be discovered within five ticks even without an entity notification");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test", timeoutTicks = 100)
    public static void chamberRetriesExternalStockWithoutPollingWholeInventory(GameTestHelper helper) {
        var pos = new BlockPos(10, 3, 10);
        helper.setBlock(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(pos, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        var chamber = (StarlightTransmutationBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var sample = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .map(RecipeHolder::value).filter(recipe -> !recipe.getInputs().isEmpty()
                        && recipe.getInputs().getFirst().isSimple()
                        && recipe.getInputs().getFirst().getItems().length > 0
                        && recipe.getInputs().getFirst().getItems()[0].getMaxStackSize() >= 2).findFirst().orElseThrow();
        var marker = sample.getInputs().getFirst().getItems()[0].copyWithCount(1);
        marker.set(DataComponents.CUSTOM_NAME, Component.literal("exact component variant"));
        var key = AEItemKey.of(marker);
        var storage = new CountingStorage();
        var initialReports = new int[1];
        IStorageProvider provider = mounts -> mounts.mount(storage);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(chamber.getMainNode().isOnline(), "Chamber must join ME"))
                .thenExecute(() -> {
                    chamber.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
                    helper.assertTrue(chamber.setPullMarker(0, marker), "The chosen component variant must be a valid input");
                    chamber.toggleAutoPull();
                    chamber.serverTick(helper.getLevel());
                    int calls = storage.extractions;
                    for (int i = 0; i < 10; i++) chamber.serverTick(helper.getLevel());
                    helper.assertTrue(calls > 0 && storage.extractions == calls,
                            "Repeated ticks before the retry deadline must not repeat failed ME extraction");
                    storage.contents.put(AEItemKey.of(marker.getItem()), 8L);
                    initialReports[0] = storage.inventoryReports;
                }).thenExecuteAfter(5, () -> {
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(chamber.getInventory().getStackInSlot(0).isEmpty(),
                            "Polling must not substitute a stock item with different components");
                    helper.assertTrue(storage.inventoryReports == initialReports[0],
                            "Waiting for missing input must not make AE2 scan the whole inventory each tick");
                    storage.contents.put(key, 1L);
                    chamber.setPullMarker(0, marker.copyWithCount(2));
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(ItemStack.isSameItemSameComponents(chamber.getInventory().getStackInSlot(0), marker),
                            "A local marker change must immediately wake exact-component extraction");
                    // The successful partial transfer wakes another attempt; its missing remainder backs off.
                    chamber.serverTick(helper.getLevel());
                    int calls = storage.extractions;
                    storage.contents.put(key, 1L); // External stock edits are observed by bounded polling.
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(storage.extractions == calls,
                            "An external stock edit must not bypass the bounded failed-transfer retry");
                }).thenExecuteAfter(5, () -> {
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(chamber.getInventory().getStackInSlot(0).getCount() == 2,
                            "An external stock change must be retried within five ticks");
                    chamber.getInventory().setStackInSlot(9, new ItemStack(Items.DIAMOND));
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(!chamber.getInventory().getStackInSlot(9).isEmpty(),
                            "A full ME provider must retain the output locally");
                    int calls = storage.insertions;
                    storage.acceptInserts = true;
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(storage.insertions == calls,
                            "An unchanged blocked output must not retry before its deadline");
                }).thenExecuteAfter(5, () -> {
                    chamber.serverTick(helper.getLevel());
                    helper.assertTrue(chamber.getInventory().getStackInSlot(9).isEmpty(),
                            "New external ME capacity must resume output within five ticks");
                }).thenExecuteAfter(10, () -> {
                    helper.assertTrue(storage.inventoryReports == initialReports[0],
                            "Satisfied markers and successful transfers must not enable whole-network inventory polling");
                    chamber.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider);
                }).thenSucceed();
    }

    private static LumenGenerationRecipe lumenRecipe(Ingredient ingredient) {
        return new LumenGenerationRecipe(ingredient, LumenAS.EVORSIO.get(), 1, 1, 1, 1, Map.of());
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        var method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static AutoChiselBlockEntity chisel(GameTestHelper helper) {
        var pos = new BlockPos(10, 3, 10);
        helper.setBlock(pos, ModContent.AUTO_CHISEL.get());
        var chisel = (AutoChiselBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        chisel.toggleDroppedItemMode();
        return chisel;
    }

    private static ItemEntity drop(GameTestHelper helper, AutoChiselBlockEntity chisel, long id) {
        var center = chisel.getBlockPos().above().getCenter();
        var entity = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, ItemsAS.STARMETAL_INGOT.toStack());
        var stack = entity.getItem();
        // AS ingots use a custom entity; spawning the generic one is cancelled by NeoForge's replacement hook.
        if (stack.getItem().hasCustomEntity(stack))
            entity = (ItemEntity) stack.getItem().createEntity(helper.getLevel(), entity, stack);
        entity.setUUID(new UUID(chisel.getBlockPos().asLong(), id));
        entity.setNoGravity(true);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "Test input must spawn");
        return entity;
    }

    private record HiddenComponentIngredient() implements ICustomIngredient {
        @Override public boolean test(ItemStack stack) {
            return stack.is(Items.DIAMOND) && Component.literal("catalyst").equals(stack.get(DataComponents.CUSTOM_NAME));
        }
        @Override public Stream<ItemStack> getItems() { return Stream.of(new ItemStack(Items.STONE)); }
        @Override public boolean isSimple() { return false; }
        @Override public IngredientType<?> getType() { return NeoForgeMod.DATA_COMPONENT_INGREDIENT_TYPE.get(); }
    }

    private static final class OutputCountingRecipe extends FocalCombineRecipe {
        int outputChecks;
        OutputCountingRecipe(FocalCombineRecipe sample, ItemStack output) {
            super(20, sample.getColor(), List.of(Ingredient.of(Items.DIRT)), List.of(output), Optional.empty());
        }
        @Override public List<ItemStack> getOutputs() { outputChecks++; return super.getOutputs(); }
    }

    private static final class CountingStorage implements MEStorage {
        final Map<AEKey, Long> contents = new HashMap<>();
        int extractions, insertions, inventoryReports;
        boolean acceptInserts;
        @Override public Component getDescription() { return Component.literal("Performance test storage"); }
        @Override public void getAvailableStacks(KeyCounter out) { inventoryReports++; contents.forEach(out::add); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            insertions++;
            return acceptInserts ? amount : 0;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            extractions++;
            long extracted = Math.min(amount, contents.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE) contents.put(key, contents.getOrDefault(key, 0L) - extracted);
            return extracted;
        }
    }
}
