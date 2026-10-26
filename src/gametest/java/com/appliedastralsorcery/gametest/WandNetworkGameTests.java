package com.appliedastralsorcery.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class WandNetworkGameTests {
    private static final BlockPos ACCESS_POINT = new BlockPos(9, 2, 9);
    private static final BlockPos ENERGY_CELL = ACCESS_POINT.south();
    private static final BlockPos SECOND_ACCESS_POINT = ENERGY_CELL.east();
    private static final Method FIND_GRID = findGridMethod();

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void wandHonorsOrdinaryWirelessRangeAndBoosters(GameTestHelper helper) {
        var accessPoint = accessPoint(helper, ACCESS_POINT, BlockOrientation.NORTH_UP);
        helper.setBlock(ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block());
        var player = player(helper.getLevel());
        var wand = linkedWand(helper.getLevel(), accessPoint.getBlockPos());
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(accessPoint.isActive(), "The access point must finish joining the powered network");
        }).thenExecute(() -> {
            var pos = accessPoint.getBlockPos();
            player.setPos(pos.getX(), pos.getY(), pos.getZ());
            helper.assertTrue(findGrid(player, wand) == accessPoint.getGrid(),
                    "A nearby wand must connect through an ordinary access point");

            double normalRange = accessPoint.getRange();
            player.setPos(pos.getX() + normalRange + 1, pos.getY(), pos.getZ());
            helper.assertTrue(findGrid(player, wand) == null, "A wand outside ordinary range must be rejected");
            accessPoint.getInternalInventory().setItemDirect(0, AEItems.WIRELESS_BOOSTER.stack(64));
            helper.assertTrue(accessPoint.getRange() > normalRange + 1,
                    "Ordinary AE2 boosters must increase the access point's range");
            helper.assertTrue(findGrid(player, wand) == accessPoint.getGrid(),
                    "A wand within boosted range must connect with ordinary AE2 boosters");
            accessPoint.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            helper.assertTrue(findGrid(player, wand) == null, "Removing ordinary boosters must restore the range limit");

            var otherDimension = otherDimension(helper);
            var remotePlayer = player(otherDimension);
            remotePlayer.setPos(pos.getX(), pos.getY(), pos.getZ());
            helper.assertTrue(findGrid(remotePlayer, wand) == null,
                    "Matching coordinates in another dimension must not satisfy ordinary wireless range");
        }).thenSucceed();
    }

    @GameTest(template = "attunement_test", timeoutTicks = 200)
    public static void wandRejectsUnavailableLinksWithoutLoadingChunks(GameTestHelper helper) {
        var player = player(helper.getLevel());
        var wand = new ItemStack(ModContent.ME_RESONATING_WAND.get());
        helper.assertTrue(findGrid(player, wand) == null, "An unbound wand must not connect");

        var missingPos = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.assertTrue(findGrid(player, linkedWand(helper.getLevel(), missingPos)) == null,
                "A loaded position without an access point must not connect");
        var unloadedPos = new BlockPos(25_000_000, 64, 25_000_000);
        helper.assertFalse(helper.getLevel().isLoaded(unloadedPos), "The remote target must start unloaded");
        helper.assertTrue(findGrid(player, linkedWand(helper.getLevel(), unloadedPos)) == null,
                "An unloaded linked access point must be unavailable");
        helper.assertFalse(helper.getLevel().isLoaded(unloadedPos), "Resolving a link must not load the target chunk");

        var accessPoint = accessPoint(helper, ACCESS_POINT, BlockOrientation.NORTH_UP);
        var linked = linkedWand(helper.getLevel(), accessPoint.getBlockPos());
        player.setPos(accessPoint.getBlockPos().getX(), accessPoint.getBlockPos().getY(), accessPoint.getBlockPos().getZ());
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(accessPoint.getGrid() != null, "The unpowered access point must initialize its grid");
        }).thenExecute(() -> {
            helper.assertFalse(accessPoint.isActive(), "An isolated unpowered access point must be inactive");
            helper.assertTrue(findGrid(player, linked) == null, "A nearby inactive access point must not connect");
        }).thenSucceed();
    }

    @GameTestGenerator
    public static List<TestFunction> optionalAEInfinityBoosterTests() {
        if (!ModList.get().isLoaded("aeinfinitybooster")) return List.of();
        return List.of(new TestFunction("wand_booster_compatibility", "wandhonorsaeinfinityboostercards",
                "appliedas:attunement_test", 300, 0, true,
                WandNetworkGameTests::wandHonorsAEInfinityBoosterCards));
    }

    private static void wandHonorsAEInfinityBoosterCards(GameTestHelper helper) {
        var linkedAccessPoint = accessPoint(helper, ACCESS_POINT, BlockOrientation.NORTH_UP);
        var boostedAccessPoint = accessPoint(helper, SECOND_ACCESS_POINT, BlockOrientation.EAST_UP);
        helper.setBlock(ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block());
        var wand = linkedWand(helper.getLevel(), linkedAccessPoint.getBlockPos());
        var player = player(helper.getLevel());
        var remotePlayer = player(otherDimension(helper));
        var pos = linkedAccessPoint.getBlockPos();
        player.setPos(pos.getX() + 100_000, pos.getY(), pos.getZ());
        remotePlayer.setPos(pos.getX() + 100_000, pos.getY(), pos.getZ());

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(linkedAccessPoint.isActive() && boostedAccessPoint.isActive(),
                    "Both access points must finish joining the powered network");
            helper.assertTrue(linkedAccessPoint.getGrid() == boostedAccessPoint.getGrid(),
                    "The linked and upgraded access points must share the same grid");
        }).thenExecute(() -> {
            helper.assertTrue(findGrid(player, wand) == null, "The distant wand must be out of ordinary range");
            boostedAccessPoint.getInternalInventory().setItemDirect(0, boosterCard(helper, "infinity_card"));
            helper.assertTrue(findGrid(player, wand) == linkedAccessPoint.getGrid(),
                    "An infinity card in another access point of the same network must remove the distance limit");
            helper.assertTrue(findGrid(remotePlayer, wand) == null,
                    "An infinity card must not allow access from another dimension");

            boostedAccessPoint.getInternalInventory().setItemDirect(0, boosterCard(helper, "dimension_card"));
            helper.assertTrue(findGrid(player, wand) == linkedAccessPoint.getGrid(),
                    "A dimension card must also allow arbitrary distance in the same dimension");
            helper.assertTrue(findGrid(remotePlayer, wand) == linkedAccessPoint.getGrid(),
                    "A dimension card in another access point of the same network must allow cross-dimensional access");
            boostedAccessPoint.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            helper.assertTrue(findGrid(player, wand) == null && findGrid(remotePlayer, wand) == null,
                    "Removing a dimension card must immediately restore distance and dimension limits");

            boostedAccessPoint.getInternalInventory().setItemDirect(0, boosterCard(helper, "dimension_card"));
            helper.setBlock(ENERGY_CELL, Blocks.AIR);
        }).thenWaitUntil(() -> {
            helper.assertFalse(linkedAccessPoint.isActive() || boostedAccessPoint.isActive(),
                    "Both disconnected access points must become inactive");
        }).thenExecute(() -> {
            helper.assertTrue(findGrid(player, linkedWand(helper.getLevel(), boostedAccessPoint.getBlockPos())) == null,
                    "A dimension card must not bypass an inactive access point in the same dimension");
            helper.assertTrue(findGrid(remotePlayer, linkedWand(helper.getLevel(), boostedAccessPoint.getBlockPos())) == null,
                    "A dimension card must not bypass an inactive access point across dimensions");
        }).thenSucceed();
    }

    private static WirelessAccessPointBlockEntity accessPoint(GameTestHelper helper, BlockPos relative,
            BlockOrientation orientation) {
        helper.setBlock(relative, AEBlocks.WIRELESS_ACCESS_POINT.block());
        var accessPoint = (WirelessAccessPointBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        orientation.setOn(accessPoint);
        return accessPoint;
    }

    private static ItemStack linkedWand(ServerLevel level, BlockPos pos) {
        var wand = new ItemStack(ModContent.ME_RESONATING_WAND.get());
        wand.set(AEComponents.WIRELESS_LINK_TARGET, GlobalPos.of(level.dimension(), pos));
        return wand;
    }

    private static FakePlayer player(ServerLevel level) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), "wand-network"));
    }

    private static ServerLevel otherDimension(GameTestHelper helper) {
        var other = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(other != null && other != helper.getLevel(), "A second server dimension must be available");
        return other;
    }

    private static ItemStack boosterCard(GameTestHelper helper, String path) {
        var id = ResourceLocation.fromNamespaceAndPath("aeinfinitybooster", path);
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The real AEInfinityBooster card must be registered: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static Method findGridMethod() {
        try {
            var method = Class.forName("com.appliedastralsorcery.wand.WandNetwork")
                    .getDeclaredMethod("findGrid", ServerPlayer.class, ItemStack.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static IGrid findGrid(ServerPlayer player, ItemStack wand) {
        try {
            return (IGrid) FIND_GRID.invoke(null, player, wand);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Wand network lookup failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot invoke wand network lookup", e);
        }
    }
}
