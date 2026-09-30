package com.appliedastralsorcery.chisel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.appliedastralsorcery.ModContent;
import com.mojang.authlib.GameProfile;
import hellfirepvp.astralsorcery.common.entity.item.ItemEntityCrystal;
import hellfirepvp.astralsorcery.common.entity.ItemEntityChiselAttackable;
import hellfirepvp.astralsorcery.common.item.ArtifactItem;
import hellfirepvp.astralsorcery.common.item.crystal.RockCrystalItem;
import hellfirepvp.astralsorcery.common.lib.CrystalPropertiesAS;
import hellfirepvp.astralsorcery.common.lib.DataComponentsAS;
import hellfirepvp.astralsorcery.common.lib.EntitiesAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ChiselEnchantmentGameTests {
    private static final BlockPos POS = new BlockPos(5, 4, 5);

    @GameTest(template = "wand_empty")
    public static void enchantmentTableMatchesNativeChoicesAndLevels(GameTestHelper helper) {
        var automatic = ModContent.AUTO_CHISEL_ITEM.toStack();
        var nativeChisel = ItemsAS.CHISEL.toStack();
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        helper.assertTrue(automatic.isEnchantable() && !automatic.isDamageableItem()
                && automatic.getEnchantmentValue() == nativeChisel.getEnchantmentValue(),
                "The machine must be enchantable with the native value but no durability");
        registry.holders().forEach(enchantment -> helper.assertTrue(
                automatic.supportsEnchantment(enchantment) == nativeChisel.supportsEnchantment(enchantment),
                "Enchantment support must match the native chisel: " + enchantment));
        for (int seed = 0; seed < 32; seed++) {
            var expected = EnchantmentHelper.selectEnchantment(RandomSource.create(seed), nativeChisel, 30,
                    registry.holders().map(holder -> (Holder<Enchantment>) holder));
            var actual = EnchantmentHelper.selectEnchantment(RandomSource.create(seed), automatic, 30,
                    registry.holders().map(holder -> (Holder<Enchantment>) holder));
            helper.assertTrue(!actual.isEmpty() && actual.size() == expected.size(), "Table offers must match the native chisel");
            for (int i = 0; i < actual.size(); i++) helper.assertTrue(
                    actual.get(i).enchantment.equals(expected.get(i).enchantment) && actual.get(i).level == expected.get(i).level,
                    "Table enchantment and level must match for the same roll");
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void anvilBooksApplyFortuneAndUnbreakingButRejectSilkTouch(GameTestHelper helper) {
        var player = player(helper);
        var menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)));
        var stack = ModContent.AUTO_CHISEL_ITEM.toStack();
        for (var key : List.of(Enchantments.FORTUNE, Enchantments.UNBREAKING)) {
            var holder = helper.getLevel().holderOrThrow(key);
            var book = new ItemStack(Items.ENCHANTED_BOOK);
            var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(holder, 3);
            book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
            menu.getSlot(0).set(stack.copy());
            menu.getSlot(1).set(book);
            menu.createResult();
            stack = menu.getSlot(2).getItem().copy();
            helper.assertTrue(!stack.isEmpty() && stack.getEnchantmentLevel(holder) == 3,
                    "Anvil books must apply " + key);
        }
        helper.assertTrue(stack.getEnchantmentLevel(helper.getLevel().holderOrThrow(Enchantments.FORTUNE)) == 3,
                "Adding Unbreaking must retain Fortune");
        var incompatible = new ItemStack(Items.ENCHANTED_BOOK);
        var silk = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        silk.set(helper.getLevel().holderOrThrow(Enchantments.SILK_TOUCH), 1);
        incompatible.set(DataComponents.STORED_ENCHANTMENTS, silk.toImmutable());
        menu.getSlot(0).set(ModContent.AUTO_CHISEL_ITEM.toStack());
        menu.getSlot(1).set(incompatible);
        menu.createResult();
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Unsupported native chisel enchantments must be rejected");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void placementSaveBreakAndReplacePreserveEnchantments(GameTestHelper helper) {
        helper.setBlock(POS.below(), Blocks.STONE);
        var player = player(helper);
        var item = enchanted(helper, ModContent.AUTO_CHISEL_ITEM.toStack());
        item.set(DataComponents.REPAIR_COST, 7);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Enchanted chisel"));
        var expected = item.get(DataComponents.ENCHANTMENTS);
        place(helper, player, item);
        var machine = (AutoChiselBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(machine.getFortuneLevel() == 3, "Real placement must import Fortune");
        var restored = new AutoChiselBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(machine.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.getFortuneLevel() == 3 && expected.equals(restored.components().get(DataComponents.ENCHANTMENTS)),
                "World save/load must retain all enchantments");
        machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(100), ILumenHandler.Action.EXECUTE);
        helper.getLevel().destroyBlock(machine.getBlockPos(), true, player);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(1),
                entity -> entity.getItem().is(ModContent.AUTO_CHISEL_ITEM));
        helper.assertTrue(drops.size() == 1, "Breaking must drop one machine");
        var dropped = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        helper.assertTrue(expected.equals(dropped.get(DataComponents.ENCHANTMENTS))
                && dropped.getOrDefault(DataComponents.REPAIR_COST, 0) == 7
                && Component.literal("Enchanted chisel").equals(dropped.get(DataComponents.CUSTOM_NAME)),
                "Loot must retain enchantments, anvil cost and name");
        place(helper, player, dropped);
        var replacement = (AutoChiselBlockEntity) helper.getBlockEntity(POS);
        helper.assertTrue(replacement.getFortuneLevel() == 3 && replacement.getLumenAmount() == 0,
                "Replacement retains enchantments without copying the old lumen buffer");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void crystalFortuneMatchesNativeSplitForIdenticalRolls(GameTestHelper helper) {
        var captured = new ArrayList<ItemStack>();
        var center = helper.absolutePos(POS).getCenter();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getEntity() instanceof ItemEntity entity && entity.position().distanceToSqr(center) < 4) {
                captured.add(entity.getItem().copy());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, capture);
        try {
            for (var item : List.of(ItemsAS.ROCK_CRYSTAL.get(), ItemsAS.ATTUNED_ROCK_CRYSTAL.get(), ModContent.ASTRAL_FLUIX_CRYSTAL.get())) {
                for (int fortune : new int[]{0, 1, 3, 10, 11}) for (int seed = 0; seed < 16; seed++) {
                    captured.clear();
                    var input = crystal(new ItemStack(item));
                    var nativeCrystal = new NativeCrystal(helper.getLevel());
                    nativeCrystal.setPos(center);
                    nativeCrystal.setItem(input.copy());
                    nativeCrystal.getRandom().setSeed(seed);
                    helper.assertTrue(nativeCrystal.split(fortune), "Native split must succeed");
                    var actual = ChiselProcessing.process(input, RandomSource.create(seed), fortune);
                    helper.assertTrue(captured.size() == 1 && ItemStack.matches(actual.getFirst(), nativeCrystal.getItem())
                            && ItemStack.matches(actual.getLast(), captured.getFirst()),
                            "Fortune must exactly match both native crystal halves at level " + fortune);
                }
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void metalAndArtifactFortuneMatchNativeConsumption(GameTestHelper helper) {
        var center = helper.absolutePos(POS).getCenter();
        var player = player(helper);
        var captured = new ArrayList<ItemStack>();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getEntity() instanceof ItemEntity entity && entity.position().distanceToSqr(center) < 4) {
                captured.add(entity.getItem().copy());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, capture);
        try {
            for (var input : List.of(ItemsAS.STARMETAL_INGOT.toStack(), ArtifactItem.defaultStack().orElseThrow())) {
                int checked = 0;
                for (int fortune : new int[]{0, 1, 3, 10, 11}) for (int seed = 0; seed < 200; seed++) {
                    var random = RandomSource.create(seed * 7919L);
                    if (random.nextFloat() >= 0.4F) continue; // Match a successful native strike.
                    checked++;
                    captured.clear();
                    var base = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, input.copy());
                    var nativeDrop = (ItemEntityChiselAttackable) input.getItem().createEntity(helper.getLevel(), base, input.copy());
                    nativeDrop.getRandom().setSeed(seed * 7919L);
                    var tool = ItemsAS.CHISEL.toStack();
                    if (fortune > 0) tool.enchant(helper.getLevel().holderOrThrow(Enchantments.FORTUNE), fortune);
                    nativeDrop.onAttack(player, tool);
                    var actual = ChiselProcessing.process(input, random, fortune);
                    helper.assertTrue(captured.size() == 1 && ItemStack.matches(actual.getLast(), captured.getFirst())
                            && (actual.size() == 1) == nativeDrop.getItem().isEmpty(),
                            "Native material retention and product must match at Fortune " + fortune);
                }
                helper.assertTrue(checked > 100, "Parity test must exercise successful native strikes");
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void bothMachineModesUseFortuneAndKeepTimeAndLumenCost(GameTestHelper helper) {
        helper.setBlock(POS, ModContent.AUTO_CHISEL.get());
        var machine = (AutoChiselBlockEntity) helper.getBlockEntity(POS);
        machine.applyComponentsFromItemStack(enchanted(helper, ModContent.AUTO_CHISEL_ITEM.toStack()));
        for (boolean dropped : new boolean[]{false, true}) {
            var input = crystal(ModContent.ASTRAL_FLUIX_CRYSTAL.toStack());
            if (dropped) {
                machine.toggleDroppedItemMode();
                var center = helper.absolutePos(POS.above()).getCenter();
                var entity = new NativeCrystal(helper.getLevel());
                entity.setPos(center);
                entity.setItem(input.copy());
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
                helper.getLevel().addFreshEntity(entity);
            } else machine.getInventory().setStackInSlot(0, input.copy());
            machine.getLumenHandler().fill(LumenAS.EVORSIO.stack(25), ILumenHandler.Action.EXECUTE);
            for (int tick = 0; tick < 39; tick++) machine.serverTick();
            helper.assertTrue(machine.getLumenAmount() == 25 && machine.getProgress() == 39,
                    "Enchantments must not change the 40-tick operation time");
            helper.getLevel().random.setSeed(4781);
            var expected = ChiselProcessing.process(input, RandomSource.create(4781), 3);
            machine.serverTick();
            var actual = dropped
                    ? helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS.below())))
                            .stream().map(ItemEntity::getItem).toList()
                    : List.of(machine.getInventory().getStackInSlot(1), machine.getInventory().getStackInSlot(2));
            helper.assertTrue(actual.size() == 2 && expected.stream().allMatch(stack -> actual.stream().anyMatch(other -> ItemStack.matches(stack, other))),
                    "Both modes must apply the machine's Fortune to their outputs");
            helper.assertTrue(machine.getLumenAmount() == 0, "Fortune and Unbreaking must keep the cost at 25 Lm");
        }
        helper.succeed();
    }

    private static ItemStack enchanted(GameTestHelper helper, ItemStack stack) {
        stack.enchant(helper.getLevel().holderOrThrow(Enchantments.FORTUNE), 3);
        stack.enchant(helper.getLevel().holderOrThrow(Enchantments.UNBREAKING), 3);
        return stack;
    }

    private static ItemStack crystal(ItemStack stack) {
        stack.set(DataComponentsAS.CRYSTAL_ATTRIBUTES, stack.get(DataComponentsAS.CRYSTAL_ATTRIBUTES)
                .setAttributeTier(CrystalPropertiesAS.SIZE, 2).setAttributeTier(CrystalPropertiesAS.PURITY, 2)
                .setAttributeTier(CrystalPropertiesAS.CUT, 2));
        return stack;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chisel-enchant"));
        player.setPos(helper.absolutePos(POS).getCenter().add(3, 0, 0));
        return player;
    }

    private static void place(GameTestHelper helper, ServerPlayer player, ItemStack item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var floor = helper.absolutePos(POS.below());
        var hit = new BlockHitResult(floor.getCenter().add(0, 0.5, 0), Direction.UP, floor, false);
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(ModContent.AUTO_CHISEL_ITEM.get().place(context).consumesAction(), "Enchanted machine must be placeable");
    }

    private static final class NativeCrystal extends ItemEntityCrystal {
        private NativeCrystal(ServerLevel level) { super(EntitiesAS.ITEM_CRYSTAL.get(), level); }
        private boolean split(int fortune) {
            return splitCrystal((RockCrystalItem) getItem().getItem(), getItem().get(DataComponentsAS.CRYSTAL_ATTRIBUTES), fortune);
        }
    }
}
