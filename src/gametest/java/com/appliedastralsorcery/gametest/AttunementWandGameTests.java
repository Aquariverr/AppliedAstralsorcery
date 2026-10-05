package com.appliedastralsorcery.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.attunement.IridescentAttunementBlockEntity;
import com.mojang.authlib.GameProfile;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.ObserversAS;
import hellfirepvp.astralsorcery.common.network.play.PktPlayStructurePreview;
import hellfirepvp.observerlib.api.ObserverHelper;
import hellfirepvp.observerlib.common.change.ObserverProviderStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AttunementWandGameTests {
    private static final BlockPos CENTER = new BlockPos(10, 2, 10);

    private static IridescentAttunementBlockEntity placeAltar(GameTestHelper helper) {
        helper.setBlock(CENTER, ModContent.IRIDESCENT_ATTUNEMENT_ALTAR.get());
        return (IridescentAttunementBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CENTER));
    }

    private static ServerPlayer wandUser(GameTestHelper helper, List<PktPlayStructurePreview.Request> previews) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "attunement-wand"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktPlayStructurePreview.Request preview) previews.add(preview);
            }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsAS.WAND.get()));
        return player;
    }

    private static void useWand(ServerPlayer player, BlockPos center, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
        var hit = new BlockHitResult(center.getCenter(), Direction.UP, center, false);
        // Exercise Astral Sorcery's registered interaction handler with the real wand item.
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, center, hit));
    }

    @GameTest(template = "attunement_test")
    public static void nativeWandBuildsAllTwelveRelays(GameTestHelper helper) {
        var altar = placeAltar(helper);
        var player = wandUser(helper, new ArrayList<>());
        player.setGameMode(GameType.CREATIVE);
        useWand(player, altar.getBlockPos(), true);
        helper.assertTrue(AttunementLayout.matches(helper.getLevel(), altar.getBlockPos()),
                "Native creative sneak-use must build the complete platform, pillars and twelve relays");
        helper.assertTrue(helper.getLevel().getBlockEntity(altar.getBlockPos()) == altar,
                "Native auto-build must preserve the central altar entity");
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(altar.hasStructure(), "Auto-built structure must be recognized by the machine");
            helper.assertTrue(altar.getStructureObserver().orElseThrow().isValid(helper.getLevel()),
                    "Native observer must recognize the completed structure");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void nativeAndMEWandsSendResolvablePreviews(GameTestHelper helper) {
        var altar = placeAltar(helper);
        var previews = new ArrayList<PktPlayStructurePreview.Request>();
        var player = wandUser(helper, previews);
        player.setGameMode(GameType.SURVIVAL);
        useWand(player, altar.getBlockPos(), false);
        useWand(player, altar.getBlockPos(), true);
        player.setGameMode(GameType.CREATIVE);
        useWand(player, altar.getBlockPos(), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.ME_RESONATING_WAND.get()));
        useWand(player, altar.getBlockPos(), false);
        helper.assertTrue(previews.size() == 4, "Native survival/creative and ME wand clicks must send previews");
        for (var packet : previews) {
            helper.assertTrue(packet.tilePos().equals(altar.getBlockPos()), "Preview must be centered on the altar");
            var observer = ObserversAS.getByName(packet.observerKey()).orElseThrow();
            helper.assertTrue(observer.equals(altar.getRequiredObserver()), "Client packet lookup must resolve this altar");
            var structure = ((ObserverProviderStructure) observer.observer().get()).getStructure();
            helper.assertTrue(structure == AttunementLayout.structure(), "Preview and construction must share one layout");
            long relays = structure.getContents().values().stream()
                    .filter(state -> state.getDescriptiveState(0).is(ModContent.CONSTELLATION_RELAY.get())).count();
            helper.assertTrue(relays == 12, "Preview must contain all twelve relays");
        }
        helper.assertTrue(helper.getLevel().isEmptyBlock(altar.getBlockPos().below()), "Preview must not build blocks");
        helper.assertTrue(ObserversAS.getByName(ObserversAS.STRUCTURE_ATTUNEMENT_ALTAR.observer().getKey())
                        .orElseThrow().equals(ObserversAS.STRUCTURE_ATTUNEMENT_ALTAR),
                "Original attunement altar observer lookup must remain intact");
        AttunementLayout.structure().place(helper.getLevel(), altar.getBlockPos());
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(altar.hasStructure(), "Completed altar must be valid");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsAS.WAND.get()));
            useWand(player, altar.getBlockPos(), false);
            helper.assertTrue(previews.size() == 4, "Completed structures must not send another preview");
            helper.succeed();
        });
    }

    @GameTest(template = "attunement_test")
    public static void legacyAltarSaveAndObserverCleanup(GameTestHelper helper) {
        var altar = placeAltar(helper);
        var legacy = new CompoundTag();
        legacy.putInt("lumen", 321);
        altar.loadWithComponents(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(altar.getLumenAmount() == 321, "Pre-observer saves must retain stored lumen");
        helper.assertFalse(altar.hasStructure(), "Missing structure cannot become valid after loading old data");
        helper.assertTrue(altar.getStructureObserver().isPresent(), "Structure check must attach the native observer");
        helper.getLevel().removeBlock(altar.getBlockPos(), false);
        helper.assertTrue(altar.getStructureObserver().isEmpty(), "Breaking the altar must release its observer");
        helper.assertTrue(ObserverHelper.getHelper().getSubscriber(helper.getLevel(), altar.getBlockPos()) == null,
                "Breaking the altar must remove the world's observer subscription");
        helper.succeed();
    }
}
