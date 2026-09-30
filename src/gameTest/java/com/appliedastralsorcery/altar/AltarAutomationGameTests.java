package com.appliedastralsorcery.altar;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import appeng.api.AECapabilities;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.ingredient.IngredientBridge;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipe;
import hellfirepvp.astralsorcery.common.recipe.altar.AltarRecipeGrid;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AltarAutomationGameTests {
    private static final BlockPos INTERFACE = new BlockPos(8, 3, 8);
    private static final BlockPos ALTAR = INTERFACE.east(3);

    @GameTest(template = "wand_empty")
    public static void nearestBusyAltarDoesNotFallBackOrTakeInputs(GameTestHelper helper) {
        var machine = setup(helper);
        helper.setBlock(ALTAR.east(3), BlocksAS.ALTAR_ILLUMINATION.get());
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        altar.getTileData().getAltarInventory().setStackInSlot(0, new ItemStack(Items.DIRT));
        var supplied = marbleInputs(2);
        helper.assertTrue(machine.findNearestAltar() == altar, "Must target nearest altar");
        helper.assertTrue(!machine.pushPattern(pillarPattern(), supplied, Direction.WEST), "Occupied nearest altar must reject");
        helper.assertTrue(supplied[0].get(marbleKey()) == 2, "Rejected inputs must remain owned by provider");
        helper.assertTrue(altar.getTileData().getAltarInventory().getStackInSlot(0).is(Items.DIRT), "Manual items must not change");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void wrongOutputExtraInputsAndPartialInventoryInsertionAreRejected(GameTestHelper helper) {
        var machine = setup(helper);
        var inputs = marbleInputs(3);
        helper.assertTrue(!machine.pushPattern(pillarPattern(), inputs, Direction.WEST), "Extra inputs must not disappear");
        helper.assertTrue(inputs[0].get(marbleKey()) == 3, "Extra-input rejection must be atomic");
        var wrong = pattern(List.of(new GenericStack(marbleKey(), 2)), new ItemStack(Items.DIAMOND));
        helper.assertTrue(!machine.pushPattern(wrong, marbleInputs(2), Direction.WEST), "Output disambiguates recipes sharing ingredients");
        var incoming = new ItemStack(Items.DIAMOND);
        helper.assertTrue(machine.getOutput().insertItem(0, incoming, false).getCount() == 1,
                "Generic insertion must not accept partial batches when busy");
        helper.assertTrue(helper.getLevel().getCapability(AECapabilities.CRAFTING_MACHINE,
                helper.absolutePos(INTERFACE), Direction.WEST) == machine, "Provider must discover the crafting capability");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 240)
    public static void nativeCraftBuffersReturnsAndRestoresSavedJob(GameTestHelper helper) {
        var machine = setup(helper);
        var inputs = marbleInputs(2);
        helper.assertTrue(machine.pushPattern(pillarPattern(), inputs, Direction.WEST), "Native pillar craft must be accepted");
        helper.assertTrue(inputs[0].isEmpty() && !machine.acceptsPlans(), "Exactly one batch is transferred");
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        helper.assertTrue(altar.getTileData().getAltarInventory().getStackInSlot(1).is(BlocksAS.MARBLE_RAW.get().asItem())
                && altar.getTileData().getAltarInventory().getStackInSlot(4).getCount() == 1, "Grid must be arranged by recipe");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!machine.acceptsPlans(), "Reload must preserve the reservation");
        helper.succeedWhen(() -> {
            var output = machine.getOutput().getStackInSlot(0);
            helper.assertTrue(output.is(BlocksAS.MARBLE_PILLAR.get().asItem()) && output.getCount() == 2,
                    "Native completion must return exactly two pillars without a world drop");
            var again = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            machine.loadWithComponents(again, helper.getLevel().registryAccess());
            helper.assertTrue(machine.getOutput().getStackInSlot(0).getCount() == 2, "Blocked output must survive save/load");
            helper.assertTrue(altar.getTileData().getAltarInventory().getStackInSlot(1).isEmpty(), "Inputs must be consumed");
            helper.assertTrue(machine.getOutput().extractItem(0, 64, false).getCount() == 2, "Import bus may recover buffered output");
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 300)
    public static void realPatternProviderReceivesNativeOutput(GameTestHelper helper) {
        var machine = setup(helper);
        helper.setBlock(INTERFACE.west(), AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(INTERFACE.west().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(INTERFACE.west());
        provider.getLogic().getPatternInv().setItemDirect(0, pillarPattern().getDefinition().toStack());
        helper.runAfterDelay(20, () -> helper.assertTrue(provider.getLogic().pushPattern(
                provider.getLogic().getAvailablePatterns().getFirst(), marbleInputs(2)),
                "Powered provider must discover the interface and push the encoded processing pattern"));
        helper.succeedWhen(() -> {
            var inventory = provider.getLogic().getReturnInv();
            long total = 0;
            for (int i = 0; i < inventory.size(); i++) {
                var stack = inventory.getStack(i);
                if (stack != null && stack.what().equals(AEItemKey.of(BlocksAS.MARBLE_PILLAR.get().asItem()))) total += stack.amount();
            }
            helper.assertTrue(total == 2, "Disconnected AE2 provider must buffer both returned pillars");
            helper.assertTrue(machine.acceptsPlans(), "Interface must unlock after returning the result");
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 400)
    public static void relayGridAndAdditionalWorldInputsCompleteNatively(GameTestHelper helper) {
        setup(helper);
        for (var entry : new hellfirepvp.astralsorcery.common.structure.PatternAltarT2Expanded().getContents().entrySet()) {
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(ALTAR).offset(entry.getKey()),
                    entry.getValue().getDescriptiveState(0));
        }
        helper.setBlock(INTERFACE.above(3), ModContent.ALTAR_AUTOMATION.get());
        var machine = (AltarAutomationBlockEntity) helper.getBlockEntity(INTERFACE.above(3));
        var holder = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("astralsorcery:altar/lumen_array")).orElseThrow();
        var recipe = (AltarRecipe) holder.value();
        var counter = new KeyCounter();
        var ingredients = new java.util.ArrayList<>(recipe.getGrid().getInputs());
        ingredients.addAll(recipe.getGrid().getRelayInputs());
        for (var ingredient : ingredients) {
            if (!ingredient.isEmpty()) counter.add(AEItemKey.of(ingredient.getItems().findFirst().orElseThrow()), 1);
        }
        for (var ingredient : recipe.getRequiredAdditionalInputs()) {
            counter.add(AEItemKey.of(ingredient.getItems().getFirst()), ingredient.count());
        }
        var expected = recipe.getOutputs().getFirst();
        var encoded = pattern(java.util.stream.StreamSupport.stream(counter.spliterator(), false)
                .map(entry -> new GenericStack(entry.getKey(), entry.getLongValue())).toList(), expected);
        helper.assertTrue(machine.pushPattern(encoded, new KeyCounter[]{counter}, Direction.WEST),
                "A native resonance recipe must place all relay and extra inputs");
        helper.succeedWhen(() -> helper.assertTrue(ItemStack.isSameItemSameComponents(
                machine.getOutput().getStackInSlot(0), expected),
                "Native altar must consume relays and additional item entities and complete the lumen array"));
    }

    @GameTest(template = "wand_empty")
    public static void competingInterfacesPreserveReservation(GameTestHelper helper) {
        var machine = setup(helper);
        helper.setBlock(INTERFACE.north(), ModContent.ALTAR_AUTOMATION.get());
        var other = (AltarAutomationBlockEntity) helper.getBlockEntity(INTERFACE.north());
        helper.assertTrue(machine.pushPattern(pillarPattern(), marbleInputs(2), Direction.WEST), "First interface owns the altar");
        var inputs = marbleInputs(2);
        helper.assertTrue(!other.pushPattern(pillarPattern(), inputs, Direction.NORTH)
                && inputs[0].get(marbleKey()) == 2, "Second interface must not overwrite a running job");
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        altar.abortCrafting();
        altar.getTileData().getAltarInventory().clearInventory();
        helper.assertTrue(!other.pushPattern(pillarPattern(), inputs, Direction.NORTH),
                "Reservation must survive a temporarily interrupted native craft");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 240)
    public static void unrelatedManualCraftStillDropsNormally(GameTestHelper helper) {
        setup(helper);
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        altar.getTileData().getAltarInventory().setStackInSlot(1, BlocksAS.MARBLE_RAW.toStack());
        altar.getTileData().getAltarInventory().setStackInSlot(4, BlocksAS.MARBLE_RAW.toStack());
        altar.startCrafting(pillarRecipe(helper), UUID.randomUUID());
        helper.succeedWhen(() -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(ALTAR)).inflate(3),
                item -> item.getItem().is(BlocksAS.MARBLE_PILLAR.get().asItem())).isEmpty(),
                "Nearby interface must not steal manually crafted outputs"));
    }

    @GameTest(template = "wand_empty")
    public static void plannerHandlesOverlappingIngredientsAndAdditionalCounts(GameTestHelper helper) {
        setup(helper);
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        var grid = AltarRecipeGrid.create(Map.of(
                'A', IngredientBridge.of(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)),
                'B', IngredientBridge.of(Ingredient.of(Items.OAK_PLANKS))),
                List.of("AB ", "   ", "   "), List.of("     ", "     ", "     ", "     ", "     "));
        var recipe = new AltarRecipe(TileAltar.AltarType.ILLUMINATION, grid, List.of(new ItemStack(Items.STICK)),
                Optional.empty(), 0, 20, false, false, Set.of(), List.of(), List.of(),
                List.of(new hellfirepvp.astralsorcery.common.util.data.CountIngredient(Ingredient.of(Items.DIAMOND), 3)),
                Set.of(), List.of());
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(Items.OAK_PLANKS), 1);
        counter.add(AEItemKey.of(Items.BIRCH_PLANKS), 1);
        counter.add(AEItemKey.of(Items.DIAMOND), 3);
        var plan = AltarRecipePlan.create(altar,
                new RecipeHolder<>(ResourceLocation.parse("appliedas:test_overlap"), recipe),
                pattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 3)), new ItemStack(Items.STICK)),
                new KeyCounter[]{counter});
        helper.assertTrue(plan != null && plan.grid().get(0).is(Items.BIRCH_PLANKS)
                && plan.grid().get(1).is(Items.OAK_PLANKS), "Broad ingredient must not consume the specific ingredient's item");
        helper.assertTrue(plan.additional().getFirst().getCount() == 3, "Additional ingredients must retain their count");
        helper.assertTrue(counter.get(AEItemKey.of(Items.DIAMOND)) == 3, "Planning must be read-only");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void resourcePlanningIsReadOnlyAndRejectsExcessOrUnrelatedInputs(GameTestHelper helper) {
        setup(helper);
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        var holder = duplicateResourceRecipe(helper);
        var supplied = resourceInputs(1000, 300);
        helper.assertTrue(AltarRecipePlan.create(altar, holder, resourcePattern(), supplied) != null,
                "Duplicate fluid and lumen requirements must accept their summed amounts");
        assertResourceInputs(helper, supplied, 1000, 300);
        var partial = resourceInputs(999, 299);
        helper.assertTrue(AltarRecipePlan.create(altar, holder, resourcePattern(), partial) != null,
                "A partial resource delivery may supplement existing cache or native resources");
        assertResourceInputs(helper, partial, 999, 299);
        var extraFluid = resourceInputs(1001, 300);
        helper.assertTrue(AltarRecipePlan.create(altar, holder, resourcePattern(), extraFluid) == null,
                "A pattern cannot hide fluid exceeding the recipe's combined requirements");
        assertResourceInputs(helper, extraFluid, 1001, 300);
        var extraLumen = resourceInputs(1000, 301);
        helper.assertTrue(AltarRecipePlan.create(altar, holder, resourcePattern(), extraLumen) == null,
                "A pattern cannot hide lumen exceeding the recipe's combined requirements");
        assertResourceInputs(helper, extraLumen, 1000, 301);
        var unrelated = resourceInputs(1000, 300);
        unrelated[0].add(AEFluidKey.of(Fluids.LAVA), 1);
        helper.assertTrue(AltarRecipePlan.create(altar, holder, resourcePattern(), unrelated) == null,
                "Unrelated resources must remain owned by the provider");
        assertResourceInputs(helper, unrelated, 1000, 300);
        helper.assertTrue(unrelated[0].get(AEFluidKey.of(Fluids.LAVA)) == 1, "Rejection must preserve unrelated fluid");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void fullLumenCacheRejectsWholeBatchWithoutTakingItemsOrFluid(GameTestHelper helper) {
        var machine = setup(helper);
        machine.getFluidHandler().fill(new FluidStack(Fluids.WATER, 123), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(machine.getLumenHandler().fill(LumenAS.AEVITAS.get().stack(
                AltarAutomationBlockEntity.LUMEN_CAPACITY), ILumenHandler.Action.EXECUTE)
                == AltarAutomationBlockEntity.LUMEN_CAPACITY, "Cache must accept its declared lumen capacity");
        var inputs = resourceInputs(1000, 300);
        helper.assertTrue(!machine.pushPattern(resourcePattern(), inputs, Direction.WEST),
                "A full resource cache must reject the entire incoming batch");
        assertResourceInputs(helper, inputs, 1000, 300);
        helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 123
                && lumenAmount(machine) == AltarAutomationBlockEntity.LUMEN_CAPACITY,
                "Failed capacity validation must not alter either resource cache");
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        helper.assertTrue(machine.acceptsPlans() && altar.getTileData().getActiveRecipe().isEmpty()
                && altar.getTileData().getAltarInventory().getStackInSlot(4).isEmpty(),
                "Rejected batch must not reserve the altar or place its items");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 240)
    public static void nativeCraftConsumesBufferedResourcesAndPreservesSavedRemainders(GameTestHelper helper) {
        var machine = setup(helper);
        var fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(INTERFACE), Direction.WEST);
        var lumen = helper.getLevel().getCapability(ILumenHandler.BLOCK,
                helper.absolutePos(INTERFACE), Direction.WEST);
        helper.assertTrue(fluid == machine.getFluidHandler() && lumen == machine.getLumenHandler(),
                "Fluid and lumen capabilities must expose the interface's own buffers");
        fluid.fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
        fluid.fill(new FluidStack(Fluids.LAVA, 250), IFluidHandler.FluidAction.EXECUTE);
        lumen.fill(LumenAS.AEVITAS.get().stack(50), ILumenHandler.Action.EXECUTE);
        lumen.fill(LumenAS.EVORSIO.get().stack(75), ILumenHandler.Action.EXECUTE);
        var inputs = resourceInputs(1000, 300);
        helper.assertTrue(machine.pushPattern(resourcePattern(), inputs, Direction.WEST),
                "Provider resources must be buffered with a successfully accepted altar batch");
        helper.assertTrue(inputs[0].isEmpty(), "Successful push transfers every resource and item exactly once");
        var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 1200 && lumenAmount(machine) == 350,
                "Save/load must retain delivered fluid, lumen, and the active reservation");
        helper.assertTrue(machine.getLumenHandler().getContainedLumen(LumenAS.EVORSIO.get())
                .orElseThrow().getAmount() == 75 && fluidAmount(machine, Fluids.LAVA) == 250,
                "Independent fluid and lumen types must survive save/load");
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.getOutput().getStackInSlot(0).is(Items.SLIME_BALL),
                    "Native crafting must finish using buffered resources without world resource sources");
            helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 200 && lumenAmount(machine) == 50,
                    "Native crafting must consume exactly the recipe's summed requirements");
            helper.assertTrue(fluidAmount(machine, Fluids.LAVA) == 250
                    && machine.getLumenHandler().getContainedLumen(LumenAS.EVORSIO.get())
                    .orElseThrow().getAmount() == 75, "Native crafting must preserve unrelated cached resources");
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 240)
    public static void legacyItemOnlyPatternUsesPrefilledFluidAndLumen(GameTestHelper helper) {
        var machine = setup(helper);
        machine.getFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        machine.getLumenHandler().fill(LumenAS.AEVITAS.get().stack(300), ILumenHandler.Action.EXECUTE);
        var items = new KeyCounter();
        items.add(AEItemKey.of(Items.DIAMOND), 1);
        var legacy = pattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), new ItemStack(Items.SLIME_BALL));
        helper.assertTrue(machine.pushPattern(legacy, new KeyCounter[]{items}, Direction.WEST),
                "Existing item-only processing patterns must use resource caches filled through capabilities");
        helper.succeedWhen(() -> {
            helper.assertTrue(machine.getOutput().getStackInSlot(0).is(Items.SLIME_BALL),
                    "Item-only pattern must complete natively from prefilled resource buffers");
            helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 0 && lumenAmount(machine) == 0,
                    "Legacy craft must consume its fluid and lumen once");
        });
    }

    @GameTest(template = "wand_empty", batch = "altar_resource_pause", timeoutTicks = 320)
    public static void mixedCacheAndChaliceDrawSurvivesReloadAndDaylightPause(GameTestHelper helper) {
        var machine = setup(helper);
        helper.setBlock(ALTAR.north(3), BlocksAS.CHALICE.get());
        var chalice = (TileChalice) helper.getBlockEntity(ALTAR.north(3));
        chalice.getTankView().fill(new FluidStack(Fluids.WATER, 875), IFluidHandler.FluidAction.EXECUTE);
        machine.getFluidHandler().fill(new FluidStack(Fluids.WATER, 125), IFluidHandler.FluidAction.EXECUTE);
        machine.getLumenHandler().fill(LumenAS.AEVITAS.get().stack(300), ILumenHandler.Action.EXECUTE);
        var items = new KeyCounter();
        items.add(AEItemKey.of(Items.DIAMOND), 1);
        var legacy = pattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), new ItemStack(Items.SLIME_BALL));
        helper.assertTrue(machine.pushPattern(legacy, new KeyCounter[]{items}, Direction.WEST),
                "Cache and native chalice resources may jointly supply a legacy processing batch");
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        boolean[] resumed = {false};
        helper.runAfterDelay(45, () -> {
            var active = altar.getTileData().getActiveRecipe().orElseThrow();
            helper.assertTrue(active.getDrawnFluid().getOrDefault(0, FluidStack.EMPTY).getAmount() == 250
                    && active.getDrawnLumen().getFirst().getAmount() == 100,
                    "First native intake must track 250 mB fluid and 100 lumen");
            helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 0
                    && chalice.getContainedFluid().getAmount() == 750 && lumenAmount(machine) == 200,
                    "A mixed fluid intake must draw only the 125 mB remainder from the chalice");
            var interfaceSaved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            var altarSaved = altar.saveWithoutMetadata(helper.getLevel().registryAccess());
            machine.loadWithComponents(interfaceSaved, helper.getLevel().registryAccess());
            altar.loadWithComponents(altarSaved, helper.getLevel().registryAccess());
            var restored = altar.getTileData().getActiveRecipe().orElseThrow();
            helper.assertTrue(restored.getDrawnFluid().getOrDefault(0, FluidStack.EMPTY).getAmount() == 250
                    && restored.getDrawnLumen().getFirst().getAmount() == 100,
                    "Reload must preserve the native recipe's already consumed resource counters");
            helper.getLevel().setDayTime(6000);
            helper.runAfterDelay(20, () -> {
                var paused = altar.getTileData().getActiveRecipe().orElseThrow();
                helper.assertTrue(paused.getDrawnFluid().getOrDefault(0, FluidStack.EMPTY).getAmount() == 250
                        && paused.getDrawnLumen().getFirst().getAmount() == 100
                        && lumenAmount(machine) == 200 && chalice.getContainedFluid().getAmount() == 750,
                        "Daylight must pause the reserved job without losing or drawing more resources");
                helper.getLevel().setDayTime(18000);
                resumed[0] = true;
            });
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(resumed[0] && machine.getOutput().getStackInSlot(0).is(Items.SLIME_BALL),
                    "Partially drawn, reloaded job must resume after night returns and finish natively");
            helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 0 && lumenAmount(machine) == 0
                    && chalice.getContainedFluid().isEmpty(),
                    "The completed recipe must consume exactly its cached plus native resources once");
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void manualResourceCraftCannotConsumeNearbyAutomationCache(GameTestHelper helper) {
        var machine = setup(helper);
        machine.getFluidHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        machine.getLumenHandler().fill(LumenAS.AEVITAS.get().stack(300), ILumenHandler.Action.EXECUTE);
        var altar = (TileAltar) helper.getBlockEntity(ALTAR);
        altar.getTileData().getAltarInventory().setStackInSlot(4, new ItemStack(Items.DIAMOND));
        altar.startCrafting(resourceRecipe(helper), UUID.randomUUID());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(fluidAmount(machine, Fluids.WATER) == 1000 && lumenAmount(machine) == 300,
                    "Unreserved manual crafting must not consume a nearby interface's fluid or lumen");
            helper.assertTrue(machine.getOutput().getStackInSlot(0).isEmpty()
                    && altar.getTileData().getAltarInventory().getStackInSlot(4).is(Items.DIAMOND),
                    "Manual resource craft must wait for its own native resource sources");
            helper.succeed();
        });
    }

    private static AltarAutomationBlockEntity setup(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000);
        helper.setBlock(INTERFACE, ModContent.ALTAR_AUTOMATION.get());
        helper.setBlock(ALTAR, BlocksAS.ALTAR_ILLUMINATION.get());
        helper.setBlock(INTERFACE.west(), Blocks.AIR);
        return (AltarAutomationBlockEntity) helper.getBlockEntity(INTERFACE);
    }

    private static AEItemKey marbleKey() { return AEItemKey.of(BlocksAS.MARBLE_RAW.get().asItem()); }

    private static KeyCounter[] marbleInputs(int count) {
        var inputs = new KeyCounter();
        inputs.add(marbleKey(), count);
        return new KeyCounter[]{inputs};
    }

    private static AEProcessingPattern pillarPattern() {
        return pattern(List.of(new GenericStack(marbleKey(), 2)), BlocksAS.MARBLE_PILLAR.toStack(2));
    }

    private static AEProcessingPattern pattern(List<GenericStack> inputs, ItemStack result) {
        var item = AEItems.PROCESSING_PATTERN.stack();
        AEProcessingPattern.encode(item, inputs, List.of(new GenericStack(AEItemKey.of(result), result.getCount())));
        return new AEProcessingPattern(AEItemKey.of(item));
    }

    private static RecipeHolder<AltarRecipe> duplicateResourceRecipe(GameTestHelper helper) {
        var recipe = resourceRecipe(helper).value();
        return new RecipeHolder<>(ResourceLocation.parse("appliedas:test_duplicate_resources"),
                new AltarRecipe(recipe.getRequiredType(), recipe.getGrid(), recipe.getOutputs(),
                        Optional.empty(), 0, 20, false, false, Set.of(),
                        List.of(LumenAS.AEVITAS.get().stack(150), LumenAS.AEVITAS.get().stack(150)),
                        List.of(new FluidStack(Fluids.WATER, 500), new FluidStack(Fluids.WATER, 500)),
                        List.of(), Set.of(), List.of()));
    }

    private static KeyCounter[] resourceInputs(int fluid, int lumen) {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.DIAMOND), 1);
        if (fluid > 0) inputs.add(AEFluidKey.of(Fluids.WATER), fluid);
        if (lumen > 0) inputs.add(LumenKey.of(LumenAS.AEVITAS.get()), lumen);
        return new KeyCounter[]{inputs};
    }

    private static AEProcessingPattern resourcePattern() {
        return pattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1),
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1000),
                new GenericStack(LumenKey.of(LumenAS.AEVITAS.get()), 300)), new ItemStack(Items.SLIME_BALL));
    }

    private static void assertResourceInputs(GameTestHelper helper, KeyCounter[] inputs, int fluid, int lumen) {
        helper.assertTrue(inputs[0].get(AEItemKey.of(Items.DIAMOND)) == 1
                && inputs[0].get(AEFluidKey.of(Fluids.WATER)) == fluid
                && inputs[0].get(LumenKey.of(LumenAS.AEVITAS.get())) == lumen,
                "Planning and rejected pushes must leave every provider-owned amount unchanged");
    }

    private static int fluidAmount(AltarAutomationBlockEntity machine, net.minecraft.world.level.material.Fluid fluid) {
        return machine.getFluidHandler().drain(new FluidStack(fluid, Integer.MAX_VALUE),
                IFluidHandler.FluidAction.SIMULATE).getAmount();
    }

    private static int lumenAmount(AltarAutomationBlockEntity machine) {
        return machine.getLumenHandler().getContainedLumen(LumenAS.AEVITAS.get())
                .map(stack -> stack.getAmount()).orElse(0);
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<AltarRecipe> resourceRecipe(GameTestHelper helper) {
        return (RecipeHolder<AltarRecipe>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("appliedas:altar/resource_buffer_test")).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<AltarRecipe> pillarRecipe(GameTestHelper helper) {
        return (RecipeHolder<AltarRecipe>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.parse("astralsorcery:altar/marble_pillar")).orElseThrow();
    }
}
