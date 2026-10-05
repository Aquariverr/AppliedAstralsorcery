package com.appliedastralsorcery.gametest;

import java.util.HashMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.crystallizer.MELumenCrystallizerBlockEntity;
import com.appliedastralsorcery.crystallizer.WorldCrystallization;
import com.appliedastralsorcery.lumen.LumenKey;
import hellfirepvp.astralsorcery.common.block.tile.LumenCrystalClusterBlock;
import hellfirepvp.astralsorcery.common.item.LumenCrystalItem;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.tile.TileLumenCrystalCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class CrystallizerGameTests {
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "attunement_test")
    public static void legacyStageThreeMigratesToWorldWithoutPayingAgain(GameTestHelper helper) {
        var panel = placeLegacyPanel(helper);
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var catalyst = LumenCrystalItem.getCrystal(LumenAS.AEVITAS);
        var holder = panel.findRecipe(catalyst);
        int cost = holder.value().getLumenConsumedPerOperation();
        var payload = new CompoundTag();
        payload.put("marker", catalyst.save(registries));
        payload.putString("recipe", holder.id().toString());
        // Write the actual old format by hand so this test does not depend on the removed implementation.
        var oldJob = legacyJob(catalyst, cost, helper);
        oldJob.putInt("stage", 3);
        payload.put("job", oldJob);
        var savedBlock = panel.saveWithoutMetadata(registries);
        savedBlock.put("crystallizer", payload);
        panel.loadWithComponents(savedBlock, registries);

        var inventory = new Storage();
        var lumen = LumenKey.of(LumenAS.AEVITAS.get());
        var catalystKey = AEItemKey.of(catalyst);
        inventory.amounts.put(lumen, 10_000L);
        inventory.amounts.put(catalystKey, 2L);
        var target = panel.getCrystallizationTarget();
        panel.getWorldCrystallization().tick(level, target, inventory, SOURCE, bound -> 0);
        assertCluster(helper, target, 3);
        helper.assertTrue(inventory.amount(lumen) == 10_000 && inventory.amount(catalystKey) == 2,
                "Materializing a previously paid stage-three crystal must consume no new resources");
        helper.assertTrue(level.getBlockEntity(panel.getBlockPos()) == panel,
                "Migrating the real crystal must retain the existing legacy panel block");

        panel.loadWithComponents(panel.saveWithoutMetadata(registries), registries);
        panel.getWorldCrystallization().tick(level, target, inventory, SOURCE, bound -> {
            helper.assertTrue(bound == WorldCrystallization.GROWTH_CHANCE,
                    "An already formed crystal must perform a growth roll rather than reseeding or shattering");
            return 0;
        });
        assertCluster(helper, target, 4);
        for (int tick = 0; tick < 8; tick++)
            panel.getWorldCrystallization().tick(level, target, inventory, SOURCE, bound -> 0);
        helper.assertTrue(inventory.amount(lumen) == 10_000 - cost && inventory.amount(catalystKey) == 2,
                "Only the final growth operation may consume lumen; the mature crystal must not become an ME item");
        assertCluster(helper, target, 4);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(),
                "Migration and maturation must not duplicate the world crystal into dropped items");
        var persisted = panel.saveWithoutMetadata(registries).getCompound("crystallizer");
        helper.assertTrue(persisted.contains("world_job") && !persisted.contains("job")
                        && persisted.getCompound("world_job").getInt("stored") == 0,
                "A migrated save must retain the new world-job format and refund its unused reserve");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void legacyFinishedOutputsRefundPartiallyAcrossReloadWithoutDuplication(GameTestHelper helper) {
        var panel = placeLegacyPanel(helper);
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var crystal = LumenCrystalItem.getCrystal(LumenAS.AEVITAS);
        var outputs = new ListTag();
        outputs.add(crystal.copyWithCount(3).save(registries));
        outputs.add(new ItemStack(Items.BUCKET, 2).save(registries));
        var oldJob = legacyJob(crystal, 10, helper);
        oldJob.putInt("stage", 4);
        oldJob.putInt("stored", 35);
        oldJob.putBoolean("finished", true);
        oldJob.put("outputs", outputs);
        var payload = new CompoundTag();
        payload.put("job", oldJob);
        var engine = new WorldCrystallization();
        engine.load(payload, registries, true);
        var inventory = new Storage();
        inventory.itemInsertionBudget = 1;
        inventory.lumenInsertionBudget = 7;
        var target = panel.getCrystallizationTarget();
        java.util.function.IntUnaryOperator noNewRoll = bound -> {
            throw new AssertionError("Already finished legacy output must never reroll seed, growth or loot");
        };
        engine.tick(level, target, inventory, SOURCE, noNewRoll);
        helper.assertTrue(inventory.amount(AEItemKey.of(crystal)) == 1
                        && inventory.amount(AEItemKey.of(Items.BUCKET)) == 0
                        && inventory.amount(LumenKey.of(LumenAS.AEVITAS.get())) == 7,
                "Partial network acceptance must deliver only the accepted old outputs and lumen");
        var restored = new WorldCrystallization();
        restored.load(engine.save(registries, true), registries, true);
        for (int tick = 0; tick < 8; tick++) {
            inventory.itemInsertionBudget = 1;
            inventory.lumenInsertionBudget = 7;
            restored.tick(level, target, inventory, SOURCE, noNewRoll);
        }
        var completed = new WorldCrystallization();
        completed.load(restored.save(registries, true), registries, true);
        for (int tick = 0; tick < 3; tick++) completed.tick(level, target, inventory, SOURCE, noNewRoll);
        helper.assertTrue(inventory.amount(AEItemKey.of(crystal)) == 3
                        && inventory.amount(AEItemKey.of(Items.BUCKET)) == 2
                        && inventory.amount(LumenKey.of(LumenAS.AEVITAS.get())) == 35,
                "Reloading both partial and completed refunds must return each historical resource exactly once");
        helper.assertTrue(level.getBlockState(target).isAir(),
                "A finished legacy job already contains its loot and must not also materialize a world crystal");
        helper.assertTrue(completed.save(registries, true).getCompound("world_job").getInt("stored") == 0,
                "Completed legacy refunds must leave no retained lumen to duplicate on another reload");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void crystallizerBlockInteractionMarksAndClearsWithoutMenu(GameTestHelper helper) {
        var panel = placeLegacyPanel(helper);
        var level = helper.getLevel();
        var absolute = panel.getBlockPos();
        var state = level.getBlockState(absolute);
        var player = FakePlayerFactory.getMinecraft(level);
        var previous = player.getMainHandItem();
        boolean wasSneaking = player.isShiftKeyDown();
        try {
            var catalyst = LumenCrystalItem.getCrystal(LumenAS.AEVITAS);
            catalyst.setCount(8);
            player.setItemInHand(InteractionHand.MAIN_HAND, catalyst);
            player.setShiftKeyDown(false);
            var hit = new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false);
            var result = state.useItemOn(catalyst, level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result == ItemInteractionResult.CONSUME && catalyst.getCount() == 8
                            && ItemStack.isSameItemSameComponents(panel.getMarker(), catalyst),
                    "Actual block item-use dispatch must mark the held catalyst without consuming it");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            var cleared = state.useWithoutItem(level, player, hit);
            helper.assertTrue(cleared == InteractionResult.CONSUME && panel.getMarker().isEmpty(),
                    "Actual empty-hand sneak-use dispatch must clear the marker");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Panel interaction must never open a menu");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            player.setShiftKeyDown(wasSneaking);
        }
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void legacyBlockLootPreservesReservationsOnNativePartItem(GameTestHelper helper) {
        var panel = placeLegacyPanel(helper);
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var catalyst = LumenCrystalItem.getCrystal(LumenAS.AEVITAS);
        var holder = panel.findRecipe(catalyst);
        var payload = new CompoundTag();
        payload.put("marker", catalyst.save(registries));
        payload.putString("recipe", holder.id().toString());
        var oldJob = legacyJob(catalyst, holder.value().getLumenConsumedPerOperation(), helper);
        oldJob.putInt("stage", -1);
        oldJob.putInt("stored", 35);
        oldJob.put("held", catalyst.save(registries));
        payload.put("job", oldJob);
        var savedBlock = panel.saveWithoutMetadata(registries);
        savedBlock.put("crystallizer", payload);
        panel.loadWithComponents(savedBlock, registries);

        var drops = Block.getDrops(panel.getBlockState(), level, panel.getBlockPos(), panel);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 1
                        && drops.getFirst().is(ModContent.ME_LUMEN_CRYSTALLIZER_ITEM.get())
                        && drops.getFirst().getItem() instanceof IPartItem<?>,
                "Normal legacy block loot must produce exactly one native AE2 part item");
        var settings = drops.getFirst().get(DataComponents.CUSTOM_DATA);
        helper.assertTrue(settings != null, "The actual loot path must export legacy settings onto the part item");
        var transferred = settings.copyTag().getCompound("crystallizer");
        var transferredJob = transferred.getCompound("world_job");
        helper.assertTrue(ItemStack.matches(catalyst, ItemStack.parseOptional(registries, transferred.getCompound("marker")))
                        && ItemStack.matches(catalyst, ItemStack.parseOptional(registries, transferredJob.getCompound("held")))
                        && transferredJob.getInt("stored") == 35,
                "Mining an old block must preserve its marker, reserved catalyst and lumen on the new part item");
        helper.succeed();
    }
    private static MELumenCrystallizerBlockEntity placeLegacyPanel(GameTestHelper helper) {
        var pos = new BlockPos(10, 3, 10);
        helper.setBlock(pos, ModContent.ME_LUMEN_CRYSTALLIZER.get());
        return (MELumenCrystallizerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static CompoundTag legacyJob(ItemStack catalyst, int cost, GameTestHelper helper) {
        var tag = new CompoundTag();
        tag.putString("lumen", LumenAS.AEVITAS.get().getRegistryKey().orElseThrow().location().toString());
        tag.put("catalyst", catalyst.save(helper.getLevel().registryAccess()));
        tag.putInt("cost", cost);
        tag.putFloat("shatter", 0);
        tag.putInt("stored", 0);
        tag.putInt("ticks", 0);
        tag.putBoolean("finished", false);
        tag.put("outputs", new ListTag());
        return tag;
    }

    private static void assertCluster(GameTestHelper helper, BlockPos target, int stage) {
        var state = helper.getLevel().getBlockState(target);
        helper.assertTrue(state.is(BlocksAS.LUMEN_CRYSTAL_CLUSTER.get())
                        && state.getValue(LumenCrystalClusterBlock.STAGE) == stage
                        && helper.getLevel().getBlockEntity(target) instanceof TileLumenCrystalCluster tile
                        && tile.getTileData().getLumen() == LumenAS.AEVITAS.get(),
                "The migrated world crystal must have its native type and stage " + stage);
    }

    private static final class Storage implements MEStorage {
        final Map<AEKey, Long> amounts = new HashMap<>();
        long itemInsertionBudget = Long.MAX_VALUE;
        long lumenInsertionBudget = Long.MAX_VALUE;
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        @Override public Component getDescription() { return Component.literal("Legacy crystallizer test storage"); }
        @Override public void getAvailableStacks(KeyCounter out) { amounts.forEach(out::add); }
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long taken = Math.min(amount(key), requested);
            if (mode == Actionable.MODULATE) amounts.put(key, amount(key) - taken);
            return taken;
        }
        @Override public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            long inserted = Math.min(requested, key instanceof LumenKey ? lumenInsertionBudget : itemInsertionBudget);
            if (mode == Actionable.MODULATE) {
                amounts.merge(key, inserted, Long::sum);
                if (key instanceof LumenKey) lumenInsertionBudget -= inserted;
                else itemInsertionBudget -= inserted;
            }
            return inserted;
        }
    }
}


