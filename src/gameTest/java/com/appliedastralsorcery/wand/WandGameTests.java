package com.appliedastralsorcery.wand;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.appliedastralsorcery.ModContent;
import com.mojang.authlib.GameProfile;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.structure.PatternInfuser;
import hellfirepvp.astralsorcery.common.tile.TileInfuser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class WandGameTests {
    @GameTest(template = "wand_empty")
    public static void infuserConsumesBlocksAndFluidsExactlyOnce(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.build();
        helper.assertTrue(new PatternInfuser().matches(helper.getLevel(), fixture.center), "Infuser structure must match");
        for (var offset : TileInfuser.getLiquidOffsets()) {
            var fluid = helper.getLevel().getFluidState(fixture.center.offset(offset));
            helper.assertTrue(fluid.isSource() && FluidsAS.LIQUID_STARLIGHT.isSource(fluid), "Pool must contain starlight sources");
        }
        helper.assertTrue(fixture.storage.total() == 0, "All and only required materials must be consumed");
        helper.assertTrue(!fixture.wand.has(MEResonatingWandItem.RETURN_BUFFER), "No unused escrow after success");
        int withdrawals = fixture.storage.withdrawals;
        fixture.build();
        helper.assertTrue(fixture.storage.withdrawals == withdrawals, "Complete structure must not consume twice");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void missingMaterialsDoNotPartiallyBuild(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.storage.amounts.remove(AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get()));
        long before = fixture.storage.total();
        fixture.build();
        helper.assertTrue(fixture.storage.total() == before && fixture.storage.withdrawals == 0, "Missing fluid must not withdraw blocks");
        assertEmptyFoundation(helper, fixture);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void obstructionDoesNotDestroyOrWithdraw(GameTestHelper helper) {
        var fixture = setup(helper);
        var obstruction = fixture.center.offset(2, -2, 2);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState());
        long before = fixture.storage.total();
        fixture.build();
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK), "Obstacle must survive");
        helper.assertTrue(fixture.storage.total() == before && fixture.storage.withdrawals == 0, "Obstacle must block extraction");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void cancelledPlacementRefundsAllMaterials(GameTestHelper helper) {
        var fixture = setup(helper);
        long before = fixture.storage.total();
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == fixture.player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            fixture.build();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(fixture.storage.withdrawals > 0, "Test must reach real extraction");
        helper.assertTrue(fixture.storage.total() == before, "Cancelled build must refund all materials");
        assertEmptyFoundation(helper, fixture);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void failedRefundSurvivesSerialization(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.storage.acceptReturns = false;
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == fixture.player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            fixture.build();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(fixture.wand.has(MEResonatingWandItem.RETURN_BUFFER), "Failed refunds must stay in wand");
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (net.minecraft.nbt.CompoundTag) fixture.wand.save(helper.getLevel().registryAccess()));
        helper.assertTrue(restored.get(MEResonatingWandItem.RETURN_BUFFER).equals(fixture.wand.get(MEResonatingWandItem.RETURN_BUFFER)),
                "Items and fluid refunds must survive save/load");
        fixture.storage.acceptReturns = true;
        new WandMaterialBuffer(restored, fixture.grid(), fixture.player).refund();
        helper.assertTrue(!restored.has(MEResonatingWandItem.RETURN_BUFFER), "Refund buffer must clear once accepted");
        helper.assertTrue(fixture.storage.total() > 12000, "Blocks and all fluid must return");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void powerFailureDoesNotWithdraw(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.storage.powered = false;
        fixture.build();
        helper.assertTrue(fixture.storage.withdrawals == 0, "Unpowered builds must not extract");
        assertEmptyFoundation(helper, fixture);
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void fluidAlwaysUsesStarlight(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        fixture.storage.amounts.put(AEFluidKey.of(Fluids.WATER), 12000L);
        fixture.build();
        for (var offset : TileInfuser.getLiquidOffsets()) {
            var fluid = helper.getLevel().getFluidState(fixture.center.offset(offset));
            helper.assertTrue(fluid.isSource() && FluidsAS.LIQUID_STARLIGHT.isSource(fluid), "Pool must always use starlight");
        }
        helper.assertTrue(fixture.storage.total() == 12000, "Water must not be extracted");
        helper.assertTrue(fixture.player.getOffhandItem().is(Items.WATER_BUCKET), "Selector must remain intact");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void inventoryModeAndDisabledPoolsNeedNoNetwork(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, 0);
        stockBackpack(fixture);
        long before = fixture.storage.total();
        WandStructureBuilder.build(fixture.player, fixture.wand, fixture.tile, Direction.UP);
        helper.assertTrue(new PatternInfuser().matches(helper.getLevel(), fixture.center), "Backpack must supply the structure");
        helper.assertTrue(fixture.storage.total() == before && fixture.storage.withdrawals == 0, "ME items must not be used");
        for (var offset : TileInfuser.getLiquidOffsets())
            helper.assertTrue(helper.getLevel().getFluidState(fixture.center.offset(offset)).isEmpty(), "Disabled pools must remain empty");
        helper.assertTrue(fixture.player.getInventory().items.stream().allMatch(stack -> stack.isEmpty()
                || stack.is(ModContent.ME_RESONATING_WAND)), "Required backpack blocks must be consumed");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void replacementRecoversStoneAndFluid(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, 7);
        var obstruction = fixture.center.offset(-2, -2, -2);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        var pool = fixture.center.offset(TileInfuser.getLiquidOffsets().iterator().next());
        helper.getLevel().setBlockAndUpdate(pool, Blocks.WATER.defaultBlockState());
        fixture.build();
        helper.assertTrue(new PatternInfuser().matches(helper.getLevel(), fixture.center), "Replacement must finish structure");
        helper.assertTrue(fixture.storage.amounts.getOrDefault(AEItemKey.of(Items.STONE), 0L) == 1,
                "Stone must return as stone, not cobblestone");
        helper.assertTrue(fixture.storage.amounts.getOrDefault(AEFluidKey.of(Fluids.WATER), 0L) == 1000,
                "Replaced source fluid must be recovered");
        helper.assertTrue(FluidsAS.LIQUID_STARLIGHT.isSource(helper.getLevel().getFluidState(pool)), "Pool must become starlight");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void fullNetworkDoesNotReplaceBlock(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, 7);
        var obstruction = fixture.center.offset(-2, -2, -2);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState());
        fixture.storage.acceptReturns = false;
        fixture.build();
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK), "Full ME must leave original block intact");
        helper.assertTrue(fixture.storage.amounts.getOrDefault(AEItemKey.of(Items.DIAMOND_BLOCK), 0L) == 0,
                "Unbroken block must not be duplicated");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void cancelledReplacementRestoresOriginalAndInventory(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, MEResonatingWandItem.REPLACE_BLOCKS);
        stockBackpack(fixture);
        var obstruction = fixture.center.offset(-2, -2, -2);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState());
        int before = fixture.player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getEntity() == fixture.player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { fixture.build(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK), "Cancellation must restore the old block");
        helper.assertTrue(fixture.player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum() == before,
                "Backpack reservations must return to backpack");
        helper.assertTrue(fixture.storage.amounts.getOrDefault(AEItemKey.of(Items.DIAMOND_BLOCK), 0L) == 0,
                "Cancelled replacement must not recover the still-existing block");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void breakProtectionAndUnbreakableBlocksAreRespected(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, 7);
        var obstruction = fixture.center.offset(-2, -2, -2);
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
        long before = fixture.storage.total();
        Consumer<BlockEvent.BreakEvent> cancel = event -> {
            if (event.getPlayer() == fixture.player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { fixture.build(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.STONE), "Protected block must survive");
        helper.assertTrue(fixture.storage.total() == before, "Protected build must refund materials");
        helper.getLevel().setBlockAndUpdate(obstruction, Blocks.BEDROCK.defaultBlockState());
        fixture.build();
        helper.assertTrue(helper.getLevel().getBlockState(obstruction).is(Blocks.BEDROCK), "Unbreakable block must survive");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void menuChangesOnlyTheOpeningWand(GameTestHelper helper) {
        var fixture = setup(helper);
        var menu = new MEResonatingWandMenu(1, fixture.player.getInventory(), InteractionHand.MAIN_HAND, fixture.wand);
        for (int option : new int[]{1, 2, 4}) helper.assertTrue(menu.clickMenuButton(fixture.player, option), "Valid toggles must work");
        helper.assertTrue(MEResonatingWandItem.options(fixture.wand) == 4, "All three toggles must persist on the wand");
        helper.assertTrue(!menu.clickMenuButton(fixture.player, 7), "Invalid button ids must be rejected");
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (net.minecraft.nbt.CompoundTag) fixture.wand.save(helper.getLevel().registryAccess()));
        helper.assertTrue(MEResonatingWandItem.options(restored) == 4, "Settings must survive save/load");
        fixture.player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        helper.assertTrue(!menu.stillValid(fixture.player) && !menu.clickMenuButton(fixture.player, 1),
                "Switching stacks must invalidate the menu");
        helper.assertTrue(MEResonatingWandItem.options(restored) == 4, "Replacement stack must not be edited");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void meModeCombinesNetworkAndBackpackMaterials(GameTestHelper helper) {
        var fixture = setup(helper);
        fixture.storage.amounts.replaceAll((key, amount) -> {
            if (!(key instanceof AEItemKey item)) return amount;
            long local = (amount + 1) / 2;
            fixture.player.getInventory().add(item.toStack((int) local));
            return amount - local;
        });
        fixture.build();
        helper.assertTrue(new PatternInfuser().matches(helper.getLevel(), fixture.center),
                "ME and backpack must jointly supply the structure");
        helper.assertTrue(fixture.storage.total() == 0, "Only the remaining required materials must be extracted from ME");
        helper.assertTrue(fixture.player.getInventory().items.stream().allMatch(stack -> stack.isEmpty()
                || stack.is(ModContent.ME_RESONATING_WAND)), "Backpack shortages must be consumed exactly once");
        for (var offset : TileInfuser.getLiquidOffsets()) {
            helper.assertTrue(FluidsAS.LIQUID_STARLIGHT.isSource(helper.getLevel().getFluidState(fixture.center.offset(offset))),
                    "Mixed item sources must still use ME starlight for pools");
        }
        helper.assertTrue(!fixture.wand.has(MEResonatingWandItem.RETURN_BUFFER), "Completed build must leave no escrow");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void mixedReservationsPreferMeAndRefundOriginalSources(GameTestHelper helper) {
        var fixture = setup(helper);
        var key = AEItemKey.of(Items.STONE);
        fixture.storage.amounts.clear();
        fixture.storage.amounts.put(key, 3L);
        fixture.player.getInventory().add(new ItemStack(Items.STONE, 4));
        var buffer = new WandMaterialBuffer(fixture.wand, fixture.grid(), fixture.player);
        helper.assertTrue(buffer.reserve(Map.of(key, 4L), fixture.player), "Combined sources must reserve successfully");
        helper.assertTrue(fixture.storage.total() == 0 && fixture.player.getInventory().countItem(Items.STONE) == 3,
                "Take all three ME blocks and only one backpack block");
        buffer.consume(key, 2);
        buffer.refund();
        helper.assertTrue(fixture.storage.total() == 1 && fixture.player.getInventory().countItem(Items.STONE) == 4,
                "After partial construction, refund one ME block and the unused backpack block to their sources");
        helper.assertTrue(!fixture.wand.has(MEResonatingWandItem.RETURN_BUFFER), "Refunded escrow must be empty");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void combinedShortageDoesNotWithdrawEitherSource(GameTestHelper helper) {
        var fixture = setup(helper);
        var key = AEItemKey.of(Items.STONE);
        fixture.storage.amounts.clear();
        fixture.storage.amounts.put(key, 2L);
        fixture.player.getInventory().add(new ItemStack(Items.STONE));
        var buffer = new WandMaterialBuffer(fixture.wand, fixture.grid(), fixture.player);
        helper.assertTrue(!buffer.reserve(Map.of(key, 4L), fixture.player), "Combined shortage must reject the reservation");
        buffer.refund();
        helper.assertTrue(fixture.storage.total() == 2 && fixture.storage.withdrawals == 0
                && fixture.player.getInventory().countItem(Items.STONE) == 1,
                "Neither source may change when the combined amount is insufficient");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void backpackFallbackDoesNotChargeMeExtractionPower(GameTestHelper helper) {
        var fixture = setup(helper);
        var key = AEItemKey.of(Items.STONE);
        fixture.storage.amounts.clear();
        fixture.storage.powered = false;
        fixture.player.getInventory().add(new ItemStack(Items.STONE, 4));
        var buffer = new WandMaterialBuffer(fixture.wand, fixture.grid(), fixture.player);
        helper.assertTrue(buffer.reserve(Map.of(key, 4L), fixture.player),
                "Backpack-only reservations must not require ME extraction power even with ME items enabled");
        buffer.consume(key, 4);
        buffer.refund();
        helper.assertTrue(fixture.storage.withdrawals == 0 && fixture.player.getInventory().countItem(Items.STONE) == 0,
                "Backpack must supply all four blocks without ME extraction");
        helper.assertTrue(!fixture.wand.has(MEResonatingWandItem.RETURN_BUFFER), "Consumed materials must not be refunded");
        helper.succeed();
    }

    private static void stockBackpack(Fixture fixture) {
        fixture.storage.amounts.forEach((key, amount) -> {
            if (key instanceof AEItemKey item) fixture.player.getInventory().add(item.toStack(amount.intValue()));
        });
    }

    @GameTest(template = "wand_empty")
    public static void altarInventoryModeDoesNotRequireFluidNetwork(GameTestHelper helper) {
        var fixture = setup(helper);
        MEResonatingWandItem.setOptions(fixture.wand, MEResonatingWandItem.BUILD_FLUIDS);
        helper.getLevel().setBlockAndUpdate(fixture.center, BlocksAS.ALTAR_RESONANCE.get().defaultBlockState());
        var pattern = new hellfirepvp.astralsorcery.common.structure.PatternAltarT2();
        var required = new LinkedHashMap<AEItemKey, Integer>();
        pattern.getContents().forEach((offset, matcher) -> {
            if (!offset.equals(BlockPos.ZERO)) required.merge(AEItemKey.of(matcher.getDescriptiveState(0).getBlock().asItem()), 1, Integer::sum);
        });
        required.forEach((item, count) -> fixture.player.getInventory().add(item.toStack(count)));
        var tile = (hellfirepvp.astralsorcery.common.tile.base.TileEntityTick<?>) helper.getLevel().getBlockEntity(fixture.center);
        WandStructureBuilder.build(fixture.player, fixture.wand, tile, Direction.UP);
        helper.assertTrue(pattern.matches(helper.getLevel(), fixture.center), "A solid-only altar must use inventory without a network");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void nativeActivationAndLinking(GameTestHelper helper) {
        var fixture = setup(helper);
        var handler = GridLinkables.get(fixture.wand.getItem());
        helper.assertTrue(handler != null && handler.canLink(fixture.wand), "Wireless linking slot must accept wand");
        var target = GlobalPos.of(helper.getLevel().dimension(), fixture.center);
        handler.link(fixture.wand, target);
        helper.assertTrue(target.equals(fixture.wand.get(AEComponents.WIRELESS_LINK_TARGET)), "Link target must persist");
        handler.unlink(fixture.wand);
        helper.assertTrue(!fixture.wand.has(AEComponents.WIRELESS_LINK_TARGET), "Unlink must clear target");
        helper.assertTrue(WandNetwork.findGrid(fixture.player, fixture.wand) == null, "Unlinked wand must not access ME");
        fixture.player.setShiftKeyDown(false);
        var altarPos = fixture.center.offset(0, 0, 4);
        helper.getLevel().setBlockAndUpdate(altarPos, BlocksAS.ALTAR_RESONANCE.get().defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(altarPos), Direction.UP, altarPos, false);
        var menu = fixture.player.containerMenu;
        helper.getLevel().getBlockState(altarPos).useItemOn(fixture.wand, helper.getLevel(), fixture.player,
                InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(fixture.player.containerMenu == menu, "ME wand must activate altar, not open its menu");
        var infuserHit = new BlockHitResult(Vec3.atCenterOf(fixture.center), Direction.UP, fixture.center, false);
        helper.getLevel().getBlockState(fixture.center).useItemOn(fixture.wand, helper.getLevel(), fixture.player,
                InteractionHand.MAIN_HAND, infuserHit);
        helper.assertTrue(fixture.tile.getTileData().getInventory().getStackInSlot(0).isEmpty(), "Wand must not be inserted into infuser");
        helper.succeed();
    }

    @GameTest(template = "wand_empty")
    public static void recipeUsesRequestedIngredients(GameTestHelper helper) {
        var input = CraftingInput.of(2, 2, java.util.List.of(
                appeng.core.definitions.AEItems.ENTROPY_MANIPULATOR.stack(),
                hellfirepvp.astralsorcery.common.lib.ItemsAS.WAND.toStack(),
                appeng.core.definitions.AEItems.WIRELESS_RECEIVER.stack(),
                appeng.core.definitions.AEItems.ENGINEERING_PROCESSOR.stack()));
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent() && recipe.get().value().assemble(input, helper.getLevel().registryAccess())
                .is(ModContent.ME_RESONATING_WAND), "Requested four ingredients must craft ME wand");
        helper.succeed();
    }

    private static void assertEmptyFoundation(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(helper.getLevel().getBlockState(fixture.center.offset(-2, -2, -2)).isAir(), "Foundation must remain empty");
    }

    private static Fixture setup(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 4, 8));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wand-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(center.getX() + 5, center.getY() + 1, center.getZ() + 5);
        var wand = ModContent.ME_RESONATING_WAND.toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.getLevel().setBlockAndUpdate(center, BlocksAS.INFUSER.get().defaultBlockState());
        var storage = new TestStorage();
        for (var entry : new PatternInfuser().getContents().entrySet()) {
            if (entry.getKey().equals(BlockPos.ZERO)) continue;
            storage.amounts.merge(AEItemKey.of(entry.getValue().getDescriptiveState(0).getBlock().asItem()), 1L, Long::sum);
        }
        storage.amounts.put(AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get()), 12000L);
        return new Fixture(player, wand, center, (TileInfuser) helper.getLevel().getBlockEntity(center), storage);
    }

    private record Fixture(ServerPlayer player, ItemStack wand, BlockPos center, TileInfuser tile, TestStorage storage) {
        IGrid grid() {
            var energy = proxy(IEnergyService.class, (method, args) -> switch (method) {
                case "extractAEPower" -> storage.powered ? (double) args[0] : 0.0;
                case "isNetworkPowered" -> storage.powered;
                default -> throw new UnsupportedOperationException(method);
            });
            var service = proxy(IStorageService.class, (method, args) -> {
                if (method.equals("getInventory")) return storage;
                throw new UnsupportedOperationException(method);
            });
            return proxy(IGrid.class, (method, args) -> switch (method) {
                case "getEnergyService" -> energy;
                case "getStorageService" -> service;
                default -> throw new UnsupportedOperationException(method);
            });
        }
        void build() { WandStructureBuilder.build(player, wand, tile, Direction.UP, grid()); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> function) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> function.apply(method.getName(), args));
    }

    private static final class TestStorage implements MEStorage {
        final Map<AEKey, Long> amounts = new LinkedHashMap<>();
        boolean acceptReturns = true;
        boolean powered = true;
        int withdrawals;
        long total() { return amounts.values().stream().mapToLong(Long::longValue).sum(); }
        @Override public Component getDescription() { return Component.literal("Wand test storage"); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long available = Math.min(amount, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE && available > 0) {
                amounts.merge(key, -available, Long::sum);
                withdrawals++;
            }
            return available;
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!acceptReturns) return 0;
            if (mode == Actionable.MODULATE) amounts.merge(key, amount, Long::sum);
            return amount;
        }
    }
}
