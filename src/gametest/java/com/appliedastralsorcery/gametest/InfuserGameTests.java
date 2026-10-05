package com.appliedastralsorcery.gametest;

import java.util.HashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.IStorageProvider;
import appeng.core.definitions.AEBlocks;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.infuser.MEStarlightInfuserBlockEntity;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.recipe.infusion.ActiveInfusionRecipe;
import hellfirepvp.astralsorcery.common.recipe.infusion.InfusionRecipe;
import hellfirepvp.astralsorcery.common.structure.PatternInfuser;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class InfuserGameTests {
    private static final BlockPos CENTER = new BlockPos(10, 3, 10);

    private static MEStarlightInfuserBlockEntity build(GameTestHelper helper) {
        new PatternInfuser().place(helper.getLevel(), helper.absolutePos(CENTER));
        helper.setBlock(CENTER, ModContent.ME_STARLIGHT_INFUSER.get());
        var infuser = (MEStarlightInfuserBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
        var fluid = FluidsAS.LIQUID_STARLIGHT.getSource().get().defaultFluidState().createLegacyBlock();
        for (var offset : TileInfuser.getLiquidOffsets()) helper.setBlock(CENTER.offset(offset), fluid);
        return infuser;
    }

    private static TileChalice chalice(GameTestHelper helper) {
        var pos = CENTER.offset(3, 0, 0);
        helper.setBlock(pos, BlocksAS.CHALICE.get());
        return (TileChalice) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static boolean fullPools(MEStarlightInfuserBlockEntity infuser) {
        var fluid = FluidsAS.LIQUID_STARLIGHT.getSource().get();
        return TileInfuser.getLiquidOffsets().stream().allMatch(offset ->
                infuser.getLevel().getFluidState(infuser.getBlockPos().offset(offset)).is(fluid));
    }

    @GameTest(template = "attunement_test")
    public static void autoStartRequiresNativeStructureAndLiquidRing(GameTestHelper helper) {
        var infuser = build(helper);
        var input = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, infuser.getBlockPos(), null);
        helper.assertTrue(input != null && input.getSlots() == 2, "Infuser must expose input and buffered-output slots");
        helper.assertTrue(input.insertItem(0, new ItemStack(Items.GLASS_PANE, 2), false).getCount() == 1,
                "Native input must accept only one item per infusion");
        var missingPool = CENTER.offset(TileInfuser.getLiquidOffsets().iterator().next());
        helper.setBlock(missingPool, Blocks.AIR);
        infuser.serverTick(helper.getLevel());
        helper.assertTrue(infuser.getTileData().getActiveRecipe().isEmpty(), "Missing liquid pool must prevent auto-start");
        helper.setBlock(missingPool, FluidsAS.LIQUID_STARLIGHT.getSource().get().defaultFluidState().createLegacyBlock());
        helper.setBlock(CENTER.below(), Blocks.AIR);
        helper.runAtTickTime(2, () -> {
            infuser.serverTick(helper.getLevel());
            helper.assertTrue(infuser.getTileData().getActiveRecipe().isEmpty(), "Broken lapis foundation must prevent auto-start");
            helper.setBlock(CENTER.below(), Blocks.LAPIS_BLOCK);
        });
        helper.runAtTickTime(4, () -> {
            infuser.serverTick(helper.getLevel());
            helper.assertTrue(infuser.hasStructure() && infuser.getTileData().getActiveRecipe().isPresent(),
                    "Repaired native structure and pools must automatically start the recipe");
            helper.assertTrue(input.extractItem(0, 1, false).isEmpty(), "Automation must not remove an active input");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void nativeCompletionBuffersOutputAndPersistsWhenOffline(GameTestHelper helper) {
        var infuser = build(helper);
        var chalice = chalice(helper);
        infuser.getInventory().insertItem(0, new ItemStack(Items.GLASS_PANE), false);
        var recipe = infuser.findMatchingRecipe(helper.getLevel()).orElseThrow().value();
        var required = recipe.getChaliceInputFluidStack();
        chalice.getTankView().fill(required, FluidAction.EXECUTE);
        helper.runAtTickTime(2, () -> {
            infuser.serverTick(helper.getLevel());
            for (int tick = 0; tick <= recipe.getDuration(); tick++) infuser.serverTick(helper.getLevel());
            helper.assertTrue(infuser.getInventory().getStackInSlot(0).isEmpty(), "Completion must consume the input");
            helper.assertTrue(infuser.getInventory().getStackInSlot(1).is(ItemsAS.GLASS_LENS), "Offline output must remain buffered");
            helper.assertTrue(chalice.getContainedFluid().isEmpty() && fullPools(infuser),
                    "Completion must consume exactly the native chalice cost and preserve every pool");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(),
                    "ME infusion must not drop its product into the world");
            infuser.getInventory().insertItem(0, new ItemStack(Items.GLASS_PANE), false);
            infuser.serverTick(helper.getLevel());
            helper.assertTrue(infuser.getTileData().getActiveRecipe().isEmpty(), "Blocked output must prevent the next infusion");
            var restored = new MEStarlightInfuserBlockEntity(infuser.getBlockPos(), infuser.getBlockState());
            restored.loadWithComponents(infuser.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(restored.getInventory().getStackInSlot(0).is(Items.GLASS_PANE)
                            && restored.getInventory().getStackInSlot(1).is(ItemsAS.GLASS_LENS),
                    "Reloading must preserve both waiting input and buffered output");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test", timeoutTicks = 100)
    public static void fluidsPreferChalicesThenMEThenWorldAndOutputsRetry(GameTestHelper helper) {
        var infuser = build(helper);
        helper.setBlock(CENTER.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var chalice = chalice(helper);
        infuser.getInventory().insertItem(0, new ItemStack(Items.GLASS_PANE), false);
        var holder = infuser.findMatchingRecipe(helper.getLevel()).orElseThrow();
        var recipe = holder.value();
        var required = recipe.getChaliceInputFluidStack();
        var fluidKey = AEFluidKey.of(required);
        var storage = new TestStorage();
        storage.contents.put(fluidKey, (long) required.getAmount());
        IStorageProvider provider = mounts -> mounts.mount(storage);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(infuser.getMainNode().isOnline(),
                "Infuser must obtain power and a channel from its physical ME connection")).thenExecute(() -> {
            infuser.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
            chalice.getTankView().fill(required, FluidAction.EXECUTE);
            var active = ActiveInfusionRecipe.of(holder);
            helper.assertTrue(active.matches(helper.getLevel(), infuser), "Native matching must discover the chalice");
            helper.assertTrue(active.consumeInputs(infuser, helper.getLevel()), "Chalice-backed infusion payment must succeed");
            helper.assertTrue(chalice.getContainedFluid().isEmpty() && storage.count(fluidKey) == required.getAmount()
                            && fullPools(infuser), "Chalice payment must take precedence over network and pools");
            active.matches(helper.getLevel(), infuser);
            helper.assertTrue(active.consumeInputs(infuser, helper.getLevel()), "ME-backed payment must succeed");
            helper.assertTrue(storage.count(fluidKey) == 0 && fullPools(infuser), "ME payment must preserve every pool");
            var worldRecipe = new InfusionRecipe(recipe.getItemInput(), recipe.getFluidInput(), recipe.getDuration(),
                    recipe.getOutput(), 1.0F, false, true);
            helper.assertTrue(infuser.consumeInfusionInputs(active, worldRecipe, helper.getLevel()) && !fullPools(infuser),
                    "Empty chalices and ME storage must fall back to the native world-fluid consumption");
            storage.acceptItems = false;
            infuser.collectOutput(recipe.getOutput());
            helper.assertTrue(infuser.getInventory().getStackInSlot(1).is(ItemsAS.GLASS_LENS), "Full ME storage must retain output");
            storage.acceptItems = true;
        }).thenWaitUntil(() -> {
            helper.assertTrue(infuser.getInventory().getStackInSlot(1).isEmpty()
                            && storage.count(AEItemKey.of(recipe.getOutput())) == recipe.getOutput().getCount(),
                    "Buffered output must retry and return to ME exactly once");
        }).thenExecute(() -> infuser.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider))
                .thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 100)
    public static void partialNetworkPaymentSurvivesReloadAndRefundsOnCancellation(GameTestHelper helper) {
        var infuser = build(helper);
        helper.setBlock(CENTER.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        infuser.getInventory().insertItem(0, new ItemStack(Items.GLASS_PANE), false);
        var holder = infuser.findMatchingRecipe(helper.getLevel()).orElseThrow();
        var required = holder.value().getChaliceInputFluidStack();
        var key = AEFluidKey.of(required);
        var storage = new TestStorage();
        storage.contents.put(key, (long) required.getAmount());
        storage.extractLimit = required.getAmount() - 1;
        IStorageProvider provider = mounts -> mounts.mount(storage);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(infuser.getMainNode().isOnline(),
                "Infuser must obtain power and a channel from its physical ME connection")).thenExecute(() -> {
            infuser.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
            var active = ActiveInfusionRecipe.of(holder);
            active.matches(helper.getLevel(), infuser);
            helper.assertFalse(active.consumeInputs(infuser, helper.getLevel()), "Partial extraction must never pay for a recipe");
            helper.assertTrue(storage.count(key) == 1 && fullPools(infuser),
                    "Partial ME payment must not additionally consume a world-fluid block");
            var registries = helper.getLevel().registryAccess();
            var restored = new MEStarlightInfuserBlockEntity(infuser.getBlockPos(), infuser.getBlockState());
            restored.loadWithComponents(infuser.saveWithoutMetadata(registries), registries);
            var savedReserve = net.neoforged.neoforge.fluids.FluidStack.parseOptional(registries,
                    restored.saveWithoutMetadata(registries).getCompound("meInfuserReservedFluid"));
            helper.assertTrue(savedReserve.getAmount() == required.getAmount() - 1,
                    "Reloading must retain every unit already extracted from a partial provider");
            infuser.getTileData().setActiveRecipe(null);
            infuser.getInventory().extractItem(0, 1, false);
        }).thenWaitUntil(() -> {
            helper.assertTrue(storage.count(key) == required.getAmount() && fullPools(infuser),
                    "Canceled infusion must refund its partial ME reservation without consuming pools");
            helper.assertTrue(infuser.getInventory().getStackInSlot(1).isEmpty(), "Canceled payment cannot produce an output");
        }).thenExecute(() -> infuser.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider))
                .thenSucceed();
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> contents = new HashMap<>();
        private boolean acceptItems = true;
        private long extractLimit = Long.MAX_VALUE;
        long count(AEKey key) { return contents.getOrDefault(key, 0L); }
        @Override public Component getDescription() { return Component.literal("Infuser GameTest storage"); }
        @Override public void getAvailableStacks(KeyCounter out) { contents.forEach(out::add); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (key instanceof AEItemKey && !acceptItems) return 0;
            if (mode == Actionable.MODULATE) contents.merge(key, amount, Long::sum);
            return amount;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, count(key));
            if (mode == Actionable.MODULATE) extracted = Math.min(extracted, extractLimit);
            if (mode == Actionable.MODULATE) contents.put(key, count(key) - extracted);
            return extracted;
        }
    }
}
