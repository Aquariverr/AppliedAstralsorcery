package com.appliedastralsorcery.parts;

import java.util.List;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.FluidPickupStrategy;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.util.SettingsFrom;
import com.appliedastralsorcery.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class NonEmptyAnnihilationPlaneGameTests {
    private static final BlockPos TARGET = new BlockPos(8, 3, 8);
    private static final IEnergySource POWER = (amount, mode, multiplier) -> amount;

    @GameTest(template = "wand_empty")
    public static void emptyDropsKeepGlassAndDoNotSpendPower(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.GLASS);
        var strategy = strategy(helper, ItemEnchantments.EMPTY);
        var result = strategy.tryPickup((amount, mode, multiplier) -> {
            helper.fail("Empty drops must be rejected before requesting break energy");
            return 0;
        }, (key, amount, mode) -> {
            helper.fail("Empty drops must not touch network storage");
            return 0;
        });
        helper.assertTrue(result == PickupStrategy.Result.CANT_PICKUP, "Empty loot must be skipped");
        helper.assertBlockPresent(Blocks.GLASS, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void originalPlaneStillBreaksGlass(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.GLASS);
        var strategy = new ItemPickupStrategy(helper.getLevel(), helper.absolutePos(TARGET), Direction.WEST,
                null, ItemEnchantments.EMPTY, null);
        helper.assertTrue(strategy.tryPickup(POWER, (key, amount, mode) -> amount) == PickupStrategy.Result.PICKED_UP,
                "Original AE2 behavior must remain unchanged");
        helper.assertBlockPresent(Blocks.AIR, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void silkTouchProducesGlassAndKeepsNativeEnergyCost(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.GLASS);
        long[] stored = {0};
        double[] spent = {0};
        var result = strategy(helper, silkTouch(helper)).tryPickup((amount, mode, multiplier) -> {
            if (mode == Actionable.MODULATE) spent[0] += amount;
            return amount;
        }, (key, amount, mode) -> {
            helper.assertTrue(key.equals(AEItemKey.of(Items.GLASS)), "Silk touch must produce glass");
            if (mode == Actionable.MODULATE) stored[0] += amount;
            return amount;
        });
        helper.assertTrue(result == PickupStrategy.Result.PICKED_UP && stored[0] == 1, "Exactly one glass is stored");
        helper.assertTrue(Math.abs(spent[0] - 18.4) < 0.001, "Native enchanted break energy must be retained");
        helper.assertBlockPresent(Blocks.AIR, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void fullStorageAndNoPowerLeaveNonEmptyBlocksIntact(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        var strategy = strategy(helper, ItemEnchantments.EMPTY);
        helper.assertTrue(strategy.tryPickup(POWER, (key, amount, mode) -> 0) == PickupStrategy.Result.CANT_STORE,
                "Full storage must prevent destruction");
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        strategy.reset();
        helper.assertTrue(strategy.tryPickup(IEnergySource.empty(), (key, amount, mode) -> amount)
                == PickupStrategy.Result.CANT_STORE, "Missing power must prevent destruction");
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        strategy.reset();
        long[] stored = {0};
        helper.assertTrue(strategy.tryPickup(POWER, (key, amount, mode) -> {
            helper.assertTrue(key.equals(AEItemKey.of(Items.COBBLESTONE)), "Native tool must yield cobblestone");
            if (mode == Actionable.MODULATE) stored[0] += amount;
            return amount;
        }) == PickupStrategy.Result.PICKED_UP && stored[0] == 1, "Recovery must store exactly one drop");
        helper.assertBlockPresent(Blocks.AIR, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void lootIsRolledOnlyOnceAndAirStacksAreSkipped(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        int[] rolls = {0};
        var strategy = new NonEmptyItemPickupStrategy(helper.getLevel(), helper.absolutePos(TARGET), Direction.WEST,
                null, ItemEnchantments.EMPTY, null) {
            @Override
            protected List<ItemStack> obtainBlockDrops(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
                rolls[0]++;
                return rolls[0] == 1 ? List.of(new ItemStack(Items.DIAMOND)) : List.of();
            }
        };
        long[] stored = {0};
        helper.assertTrue(strategy.tryPickup(POWER, (key, amount, mode) -> {
            helper.assertTrue(key.equals(AEItemKey.of(Items.DIAMOND)), "Must store the actual first loot roll");
            if (mode == Actionable.MODULATE) stored[0] += amount;
            return amount;
        }) == PickupStrategy.Result.PICKED_UP && rolls[0] == 1 && stored[0] == 1,
                "Checking and collecting must share a single loot roll");
        helper.setBlock(TARGET, Blocks.STONE);
        var empty = new NonEmptyItemPickupStrategy(helper.getLevel(), helper.absolutePos(TARGET), Direction.WEST,
                null, ItemEnchantments.EMPTY, null) {
            @Override
            protected List<ItemStack> obtainBlockDrops(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
                return List.of(ItemStack.EMPTY, new ItemStack(Items.AIR));
            }
        };
        helper.assertTrue(empty.tryPickup(POWER, (key, amount, mode) -> amount) == PickupStrategy.Result.CANT_PICKUP,
                "Air-only output must be treated as empty");
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void droppedItemsStillCollectBesideAnEmptyDropBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.GLASS);
        var strategy = strategy(helper, ItemEnchantments.EMPTY);
        strategy.tryPickup(POWER, (key, amount, mode) -> amount);
        var pos = helper.absolutePos(TARGET);
        var entity = new ItemEntity(helper.getLevel(), pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND, 3));
        long[] stored = {0};
        helper.assertTrue(strategy.canPickUpEntity(entity) && strategy.pickUpEntity(POWER, (key, amount, mode) -> {
            if (mode == Actionable.MODULATE) stored[0] += amount;
            return amount;
        }, entity), "Entity collection must remain enabled after skipping a block");
        helper.assertTrue(!entity.isAlive() && stored[0] == 3, "Dropped items must be collected exactly once");
        helper.assertBlockPresent(Blocks.GLASS, TARGET);
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 200)
    public static void realPlaneSkipsGlassThenWakesForStone(GameTestHelper helper) {
        var cablePos = TARGET.west();
        helper.setBlock(cablePos, AEBlocks.CABLE_BUS.block());
        var host = (IPartHost) helper.getBlockEntity(cablePos);
        host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
        var plane = host.addPart(ModContent.NON_EMPTY_ANNIHILATION_PLANE.get(), Direction.EAST, null);
        helper.assertTrue(plane != null, "Registered plane must be placeable on AE2 cable");
        helper.setBlock(cablePos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(cablePos.west(), AEBlocks.DRIVE.block());
        var drive = (DriveBlockEntity) helper.getBlockEntity(cablePos.west());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        helper.setBlock(TARGET, Blocks.GLASS);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(plane.isActive(), "Test network must have power and a channel");
            helper.assertBlockPresent(Blocks.GLASS, TARGET);
            helper.assertTrue(plane.getPickupStrategies().stream().anyMatch(NonEmptyItemPickupStrategy.class::isInstance),
                    "Real plane must install the guarded item strategy");
            helper.assertTrue(plane.getPickupStrategies().stream().anyMatch(FluidPickupStrategy.class::isInstance),
                    "Native fluid collection must be retained");
            helper.setBlock(TARGET, Blocks.STONE);
        });
        helper.succeedWhen(() -> {
            var inventory = drive.getCellInventory(0);
            helper.assertTrue(inventory != null
                    && inventory.getAvailableStacks().get(AEItemKey.of(Items.COBBLESTONE)) == 1,
                    "Neighbor update must wake the sleeping plane and store one cobblestone");
            helper.assertBlockPresent(Blocks.AIR, TARGET);
        });
    }

    @GameTest(template = "wand_empty")
    public static void enchantmentsPersistAndAreSupportedByNativeTags(GameTestHelper helper) {
        var item = ModContent.NON_EMPTY_ANNIHILATION_PLANE.get();
        var stack = item.getDefaultInstance();
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var key : List.of(Enchantments.SILK_TOUCH, Enchantments.FORTUNE, Enchantments.EFFICIENCY, Enchantments.UNBREAKING)) {
            helper.assertTrue(registry.getHolderOrThrow(key).value().isSupportedItem(stack), "Native enchantment must accept new plane: " + key);
        }
        var plane = item.createPart();
        var enchants = silkTouch(helper);
        plane.importSettings(SettingsFrom.DISMANTLE_ITEM,
                DataComponentMap.builder().set(DataComponents.ENCHANTMENTS, enchants).build(), null);
        var saved = new CompoundTag();
        plane.writeToNBT(saved, helper.getLevel().registryAccess());
        var restored = item.createPart();
        restored.readFromNBT(saved, helper.getLevel().registryAccess());
        var output = DataComponentMap.builder();
        restored.exportSettings(SettingsFrom.DISMANTLE_ITEM, output);
        helper.assertTrue(enchants.equals(output.build().get(DataComponents.ENCHANTMENTS)),
                "Enchantments must survive save/load and dismantling");
        helper.succeed();
    }

    private static NonEmptyItemPickupStrategy strategy(GameTestHelper helper, ItemEnchantments enchantments) {
        return new NonEmptyItemPickupStrategy(helper.getLevel(), helper.absolutePos(TARGET), Direction.WEST,
                null, enchantments, null);
    }

    private static ItemEnchantments silkTouch(GameTestHelper helper) {
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        return enchantments.toImmutable();
    }
}
