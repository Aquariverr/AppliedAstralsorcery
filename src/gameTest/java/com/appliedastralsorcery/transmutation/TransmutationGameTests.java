package com.appliedastralsorcery.transmutation;

import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.RecipeTypesAS;
import hellfirepvp.astralsorcery.common.linking.Linkable.LinkAction;
import hellfirepvp.astralsorcery.common.recipe.focal.drop.FocalCombineRecipe;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.TileStarlightFocusCrystal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class TransmutationGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void worldEffectsSyncRecipeConstellationWithoutAnOpenMenu(GameTestHelper helper) {
        var machine = setup(helper, false);
        helper.runAfterDelay(25, () -> {
            var registries = helper.getLevel().registryAccess();
            var viewer = new StarlightTransmutationBlockEntity(machine.getBlockPos(), machine.getBlockState());
            tick(machine, 1, packet(ConstellationsAS.AEVITAS.get()));
            viewer.handleUpdateTag(machine.getUpdateTag(registries), registries);
            helper.assertTrue(viewer.getDisplayConstellation() == ConstellationsAS.AEVITAS.get(),
                    "A newly tracking client must receive the constellation without opening the menu");

            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            tick(machine, 1, packet(ConstellationsAS.MINERALIS.get()));
            viewer.onDataPacket(null, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(machine), registries);
            helper.assertTrue(machine.getBlockState().getValue(StarlightTransmutationBlock.WORKING)
                    && viewer.getDisplayConstellation() == ConstellationsAS.MINERALIS.get(),
                    "Live updates must use the working recipe's constellation instead of another incoming beam");
            helper.assertTrue(!machine.saveWithoutMetadata(registries).contains("displayConstellation"),
                    "Visual light state must not persist as an active light source after reloading");

            helper.runAfterDelay(8, () -> {
                viewer.onDataPacket(null, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(machine), registries);
                helper.assertTrue(viewer.getDisplayConstellation() == null
                        && !machine.getBlockState().getValue(StarlightTransmutationBlock.WORKING),
                        "Light loss must clear the visual constellation and stop the working effect");
                tick(machine, 1, packet(null));
                viewer.handleUpdateTag(machine.getUpdateTag(registries), registries);
                helper.assertTrue(viewer.getDisplayConstellation() == null,
                        "Unattuned light must not restore the previous constellation effect");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void menuSyncsLiveConstellationAndClearsItWhenTheBeamExpires(GameTestHelper helper) {
        var machine = setup(helper, false);
        // Let the native starlight receiver and ME node finish their initial registration.
        helper.runAfterDelay(25, () -> checkConstellationSync(helper, machine));
    }

    private static void checkConstellationSync(GameTestHelper helper, StarlightTransmutationBlockEntity machine) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var serverMenu = new StarlightTransmutationMenu(1, player.getInventory(), machine);
        var clientMenu = new StarlightTransmutationMenu(1, player.getInventory());
        serverMenu.addSlotListener(new net.minecraft.world.inventory.ContainerListener() {
            @Override public void slotChanged(net.minecraft.world.inventory.AbstractContainerMenu menu, int slot, ItemStack stack) {}
            @Override public void dataChanged(net.minecraft.world.inventory.AbstractContainerMenu menu, int id, int value) {
                clientMenu.setData(id, value);
            }
        });
        helper.assertTrue(clientMenu.getConstellation() == null, "An unlit chamber must show only the sky");
        tick(machine, 1, packet(null));
        serverMenu.broadcastChanges();
        helper.assertTrue(clientMenu.hasStarlight() && clientMenu.getConstellation() == null,
                "Unattuned starlight must not invent a constellation");
        tick(machine, 1, packet(ConstellationsAS.AEVITAS.get()));
        serverMenu.broadcastChanges();
        helper.assertTrue(clientMenu.getConstellation() == ConstellationsAS.AEVITAS.get(),
                "An idle chamber must sync the actual incoming constellation");

        machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
        tick(machine, 1, packet(ConstellationsAS.MINERALIS.get()));
        serverMenu.broadcastChanges();
        helper.assertTrue(clientMenu.getConstellation() == ConstellationsAS.MINERALIS.get(),
                "With multiple beams the icon must prefer the constellation used by the selected recipe");

        helper.runAfterDelay(8, () -> {
            serverMenu.broadcastChanges();
            helper.assertTrue(!clientMenu.hasStarlight() && clientMenu.getConstellation() == null,
                    "An expired beam must clear the synced icon instead of leaving a stale constellation");
            tick(machine, 1, packet(null));
            serverMenu.broadcastChanges();
            helper.assertTrue(clientMenu.hasStarlight() && clientMenu.getConstellation() == null,
                    "Reconnecting unattuned starlight must retain the sky background");
            tick(machine, 1, packet(ConstellationsAS.AEVITAS.get()));
            serverMenu.broadcastChanges();
            helper.assertTrue(clientMenu.getConstellation() == ConstellationsAS.AEVITAS.get(),
                    "The wrong recipe constellation must still show the actual incoming beam");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty")
    public static void quantitiesAndOverlappingIngredientsAreMatchedWithoutMutation(GameTestHelper helper) {
        var inventory = new ItemStackHandler(9);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        var ambiguous = List.of(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), Ingredient.of(Items.IRON_INGOT));
        var plan = TransmutationPlan.match(ambiguous, inventory, 9);
        helper.assertTrue(plan != null && plan[0] == 1 && plan[1] == 1, "A flexible input must leave iron for the exact input");
        helper.assertTrue(inventory.getStackInSlot(0).getCount() == 1, "Planning must not mutate inventory");
        var repeated = List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT));
        helper.assertTrue(TransmutationPlan.match(repeated, inventory, 9) == null, "One item cannot satisfy two inputs");
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertTrue(TransmutationPlan.match(repeated, inventory, 9)[0] == 2, "Repeated ingredients may share one stack");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void nativeMultiInputRecipeReturnsExactlyOneProductToRealMEStorage(GameTestHelper helper) {
        var machine = setup(helper, true);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(machine.getMainNode().isOnline(), "Machine must have power and a real channel");
            machine.getInventory().setStackInSlot(0, new ItemStack(BlocksAS.MARBLE_RAW.get(), 3));
            machine.getInventory().setStackInSlot(1, new ItemStack(ItemsAS.AQUAMARINE.get(), 2));
            machine.getInventory().setStackInSlot(2, new ItemStack(Items.ENDER_PEARL));
            var recipe = recipe(helper, "astralsorcery:focal_combine/wand");
            tick(machine, recipe.getDuration() - 1, packet(null));
            helper.assertTrue(machine.getProgress() == recipe.getDuration() - 1
                    && machine.getInventory().getStackInSlot(0).getCount() == 3, "Original recipe duration must precede consumption");
            tick(machine, 1, packet(null));
            helper.assertTrue(machine.getInventory().getStackInSlot(0).getCount() == 1
                    && machine.getInventory().getStackInSlot(1).isEmpty() && machine.getInventory().getStackInSlot(2).isEmpty(),
                    "Only the exact native quantities may be consumed");
            var grid = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(grid.extract(AEItemKey.of(ItemsAS.WAND.get()), 10, Actionable.SIMULATE, IActionSource.empty()) == 1,
                    "Completed wand must be in real ME storage");
            for (int slot = 9; slot < 18; slot++) helper.assertTrue(machine.getInventory().getStackInSlot(slot).isEmpty(), "Exported output must leave no duplicate buffer");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void missingLightAndOfflineNetworkNeverConsumeInputs(GameTestHelper helper) {
        var machine = setup(helper, true);
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 2));
        helper.runAfterDelay(25, () -> {
            tick(machine, 40, null);
            helper.assertTrue(machine.getProgress() == 0 && machine.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT,
                    "ME power alone must not transmute");
            machine.receiveStarlight(helper.getLevel(), new StarlightTransmissionPacket(null, 0));
            tick(machine, 5, null);
            helper.assertTrue(!machine.hasStarlight(), "Zero strength must not count as a beam");
            helper.setBlock(POS.below(), Blocks.AIR);
            helper.runAfterDelay(15, () -> {
                tick(machine, 40, packet(null));
                helper.assertTrue(machine.getProgress() == 0 && machine.getStatus() == StarlightTransmutationBlockEntity.Status.OFFLINE
                        && machine.getInventory().getStackInSlot(0).getCount() == 2, "Offline ME must pause without consuming input");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void constellationRequirementsAndMultipleOutputsSurviveNativeReceiver(GameTestHelper helper) {
        var machine = setup(helper, true);
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            tick(machine, 40, packet(ConstellationsAS.AEVITAS.get()));
            helper.assertTrue(machine.getProgress() == 0 && machine.getStatus() == StarlightTransmutationBlockEntity.Status.WRONG_CONSTELLATION,
                    "The wrong constellation must not start the job");
            tick(machine, 20, packet(ConstellationsAS.MINERALIS.get()));
            var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(storage.extract(AEItemKey.of(Items.QUARTZ), 10, Actionable.SIMULATE, IActionSource.empty()) == 2
                            && storage.extract(AEItemKey.of(Items.DIAMOND), 10, Actionable.SIMULATE, IActionSource.empty()) == 3,
                    "Every output and its count must return to ME");
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void fullBufferPausesAndStoredResultsRetryAfterCapacityReturns(GameTestHelper helper) {
        var machine = setup(helper, false);
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            for (int slot = 9; slot < 18; slot++) machine.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            tick(machine, 40, packet(ConstellationsAS.MINERALIS.get()));
            helper.assertTrue(machine.getProgress() == 0 && machine.getStatus() == StarlightTransmutationBlockEntity.Status.OUTPUT_BLOCKED
                    && machine.getInventory().getStackInSlot(0).getCount() == 1, "Blocked outputs must not consume materials");
            machine.getInventory().setStackInSlot(9, ItemStack.EMPTY);
            tick(machine, 20, packet(ConstellationsAS.MINERALIS.get()));
            helper.assertTrue(!machine.getInventory().getStackInSlot(0).isEmpty(), "One free slot cannot hold two different outputs");
            machine.getInventory().setStackInSlot(10, ItemStack.EMPTY);
            tick(machine, 20, packet(ConstellationsAS.MINERALIS.get()));
            helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(), "Complete output space must allow the job");
            var saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(saved.getCompound("inventory").getList("Items", 10).size() == 9,
                    "Buffered products and pre-existing output must be persisted");
            var drive = (DriveBlockEntity) helper.getBlockEntity(POS.east());
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            helper.runAfterDelay(10, () -> {
                var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
                helper.assertTrue(storage.extract(AEItemKey.of(Items.QUARTZ), 10, Actionable.SIMULATE, IActionSource.empty()) == 2
                        && storage.extract(AEItemKey.of(Items.DIAMOND), 10, Actionable.SIMULATE, IActionSource.empty()) == 3,
                        "New capacity must receive previously buffered products exactly once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void reloadPreservesProgressButRequiresFreshStarlight(GameTestHelper helper) {
        var machine = setup(helper, true);
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
            tick(machine, 40, packet(null));
            var registries = helper.getLevel().registryAccess();
            var saved = machine.saveWithoutMetadata(registries);
            var restored = new StarlightTransmutationBlockEntity(machine.getBlockPos(), machine.getBlockState());
            restored.loadWithComponents(saved, registries);
            helper.getLevel().removeBlockEntity(machine.getBlockPos());
            helper.getLevel().setBlockEntity(restored);
            tick(restored, 20, null);
            helper.assertTrue(restored.getProgress() == 40 && !restored.hasStarlight()
                    && restored.getInventory().getStackInSlot(0).getCount() == 1, "Reload must preserve the job without restoring a stale beam");
            helper.runAfterDelay(10, () -> {
                int duration = recipe(helper, "astralsorcery:focal_combine/raw_starmetal").getDuration();
                tick(restored, duration - 40, packet(null));
                var storage = restored.getMainNode().getGrid().getStorageService().getInventory();
                helper.assertTrue(storage.extract(AEItemKey.of(ItemsAS.RAW_STARMETAL.get()), 2, Actionable.SIMULATE, IActionSource.empty()) == 1,
                        "A restored job must complete exactly once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void receiverLinksToNativeCrystalAndIsRemovedWithTheBlock(GameTestHelper helper) {
        var machine = setup(helper, false);
        var sourcePos = POS.west(2);
        helper.setBlock(sourcePos, BlocksAS.STARLIGHT_FOCUS_ROCK_CRYSTAL.get());
        var crystal = (TileStarlightFocusCrystal) helper.getBlockEntity(sourcePos);
        var attributes = ItemsAS.ROCK_CRYSTAL.toStack().get(DataComponentsAS.CRYSTAL_ATTRIBUTES)
                .setAttributeTier(CrystalPropertiesAS.SIZE, 3).setAttributeTier(CrystalPropertiesAS.PURITY, 3);
        crystal.getTileData().setCrystalAttributes(attributes);
        crystal.getTileData().setConstellation(ConstellationsAS.MINERALIS.get());
        crystal.serverTick(helper.getLevel());
        machine.serverTick(helper.getLevel());
        var source = crystal.getNetworkNode().orElseThrow();
        helper.assertTrue(source.tryLinkTo(helper.getLevel(), machine.getBlockPos(), LinkAction.EXECUTE).isSuccess(),
                "Native Linking Tool API must accept the chamber as a target");
        source.updateLinkStateChange(helper.getLevel(), machine.getBlockPos(), true);
        helper.assertTrue(source.isLinkedTo(machine.getBlockPos()), "The source must persist its outgoing chamber link");
        var helperNetwork = StarlightNetworkLevelHelper.get(helper.getLevel());
        helper.assertTrue(helperNetwork.hasNode(machine.getBlockPos()), "The chamber must register a native receiver");
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 3));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), Direction.UP)
                .insertItem(9, new ItemStack(Items.RAW_IRON), false).getCount() == 1, "Automation must not insert into output slots");
        helper.setBlock(POS, Blocks.AIR);
        helper.assertTrue(!helperNetwork.hasNode(machine.getBlockPos()) && machine.getMainNode().getNode() == null,
                "Breaking the block must remove both networks' nodes");
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void liveBeamExpiresAndProcessingResumesAfterReconnect(GameTestHelper helper) {
        var machine = setup(helper, true);
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON));
            tick(machine, 40, packet(null));
            helper.runAfterDelay(8, () -> {
                int paused = machine.getProgress();
                helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT && !machine.hasStarlight(),
                        "A disconnected beam must expire");
                helper.runAfterDelay(8, () -> {
                    helper.assertTrue(machine.getProgress() == paused, "No stale packet may advance progress");
                    tick(machine, 1, packet(null));
                    helper.assertTrue(machine.getProgress() == paused + 1, "Fresh light must resume the paused job");
                    helper.succeed();
                });
            });
        });
    }

    private static StarlightTransmutationBlockEntity setup(GameTestHelper helper, boolean storage) {
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.east(), AEBlocks.DRIVE.block());
        if (storage) ((DriveBlockEntity) helper.getBlockEntity(POS.east())).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        return (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
    }
    private static StarlightTransmissionPacket packet(hellfirepvp.astralsorcery.common.constellation.BaseConstellation constellation) {
        return new StarlightTransmissionPacket(constellation, 10);
    }
    private static void tick(StarlightTransmutationBlockEntity machine, int ticks, StarlightTransmissionPacket packet) {
        var server = (net.minecraft.server.level.ServerLevel) machine.getLevel();
        for (int i = 0; i < ticks; i++) {
            if (packet != null) machine.getNetworkNode().orElseThrow().receiveStarlight(server, packet);
            machine.serverTick(server);
        }
    }
    private static FocalCombineRecipe recipe(GameTestHelper helper, String id) {
        return helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeTypesAS.FOCAL_COMBINE_TYPE.get()).stream()
                .filter(holder -> holder.id().equals(ResourceLocation.parse(id))).findFirst().orElseThrow().value();
    }
}
