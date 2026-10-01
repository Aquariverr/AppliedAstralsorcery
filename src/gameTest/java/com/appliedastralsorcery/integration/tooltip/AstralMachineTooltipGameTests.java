package com.appliedastralsorcery.integration.tooltip;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import appeng.core.definitions.AEBlocks;
import appeng.integration.modules.igtooltip.GridNodeState;
import appeng.integration.modules.igtooltip.TooltipProviders;
import appeng.integration.modules.igtooltip.blocks.GridNodeStateDataProvider;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chalice.MEChaliceBlock;
import com.appliedastralsorcery.chalice.MEChaliceBlockEntity;
import com.appliedastralsorcery.lumen.MELumenArrayBlock;
import com.appliedastralsorcery.lumen.MELumenArrayBlockEntity;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlock;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.impl.WailaCommonRegistration;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class AstralMachineTooltipGameTests {
    @GameTest(template = "wand_empty")
    public static void jadeServerProviderIdsAreUniqueAcrossAE2AndAstralMachines(GameTestHelper helper) {
        var providers = new HashMap<ResourceLocation, IServerDataProvider<BlockAccessor>>();
        // Jade's sync protocol keys adapters by UID, not by their registered block entity class.
        // The former base-class registration passed direct provider tests but collided here.
        WailaCommonRegistration.instance().blockDataProviders.entries().forEach(entry -> {
            for (var provider : entry.getValue()) {
                var id = provider.getUid();
                if (!id.getNamespace().equals("ae2") && !id.getNamespace().equals("appliedas")) continue;
                var previous = providers.putIfAbsent(id, provider);
                helper.assertTrue(previous == null || previous == provider,
                        "Jade cannot sync different server adapters with the same UID: " + id);
            }
        });
        helper.assertTrue(providers.containsKey(ResourceLocation.parse("ae2:grid_node")),
                "Native AE2 network status must remain registered");
        for (var machine : java.util.List.of("transmutation", "lumen_array", "chalice")) {
            helper.assertTrue(providers.containsKey(ResourceLocation.parse("appliedas:" + machine + "_grid_node")),
                    "Jade must register a distinct data provider for " + machine);
        }
        helper.succeed();
    }

    @GameTest(template = "wand_empty", timeoutTicks = 150)
    public static void ae2ServiceDiscoveryAndNativeTooltipsFollowAllThreeRealNetworks(GameTestHelper helper) {
        var serverProviders = new HashMap<Class<?>, GridNodeStateDataProvider>();
        TooltipProviders.loadCommon(new CommonRegistration() {
            @Override public <T extends BlockEntity> void addBlockEntityData(ResourceLocation id, Class<T> type,
                    ServerDataProvider<? super T> provider) {
                if (provider instanceof GridNodeStateDataProvider nativeProvider) serverProviders.put(type, nativeProvider);
            }
        });
        var clientProviders = new HashMap<Class<?>, GridNodeStateDataProvider>();
        var blockClasses = new HashMap<Class<?>, Class<?>>();
        // Exercise AE2's real service discovery on both sides without loading the client renderer.
        var client = (ClientRegistration) Proxy.newProxyInstance(ClientRegistration.class.getClassLoader(),
                new Class<?>[]{ClientRegistration.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addBlockEntityBody") && args[3] instanceof GridNodeStateDataProvider provider) {
                        clientProviders.put((Class<?>) args[0], provider);
                        blockClasses.put((Class<?>) args[0], (Class<?>) args[1]);
                    }
                    return null;
                });
        TooltipProviders.loadClient(client);
        var expected = Map.of(StarlightTransmutationBlockEntity.class, StarlightTransmutationBlock.class,
                MELumenArrayBlockEntity.class, MELumenArrayBlock.class, MEChaliceBlockEntity.class, MEChaliceBlock.class);
        expected.forEach((entity, block) -> {
            helper.assertTrue(serverProviders.containsKey(entity), "AE2 must discover server data for " + entity.getSimpleName());
            helper.assertTrue(blockClasses.get(entity) == block, "AE2 must register the native Jade body for " + block.getSimpleName());
        });

        var positions = new BlockPos[]{new BlockPos(3, 5, 3), new BlockPos(7, 5, 3), new BlockPos(11, 5, 3)};
        helper.setBlock(positions[0], ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(positions[1], ModContent.ME_LUMEN_ARRAY.get());
        helper.setBlock(positions[2], ModContent.ME_CHALICE.get());
        helper.runAfterDelay(25, () -> {
            assertTooltips(helper, positions, serverProviders, clientProviders, GridNodeState.OFFLINE);
            for (var position : positions) helper.setBlock(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            // AE2 buffers a transition from an unpowered grid for 30 ticks.
            helper.runAfterDelay(40, () -> {
                assertTooltips(helper, positions, serverProviders, clientProviders, GridNodeState.ONLINE);
                for (var position : positions) helper.setBlock(position.below(), Blocks.AIR);
                helper.runAfterDelay(15, () -> {
                    assertTooltips(helper, positions, serverProviders, clientProviders, GridNodeState.OFFLINE);
                    helper.succeed();
                });
            });
        });
    }

    private static void assertTooltips(GameTestHelper helper, BlockPos[] positions,
            Map<Class<?>, GridNodeStateDataProvider> serverProviders,
            Map<Class<?>, GridNodeStateDataProvider> clientProviders, GridNodeState expected) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (var position : positions) {
            var machine = helper.getBlockEntity(position);
            var data = new CompoundTag();
            serverProviders.get(machine.getClass()).provideServerData(player, machine, data);
            var jadeData = new CompoundTag();
            var accessor = (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(),
                    new Class<?>[]{BlockAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getBlockEntity" -> machine;
                        case "getPlayer" -> player;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            int jadeStatusProviders = 0;
            for (var provider : WailaCommonRegistration.instance()
                    .getBlockNBTProviders(machine.getBlockState().getBlock(), machine)) {
                if (provider.getUid().getNamespace().equals("appliedas")
                        && provider.getUid().getPath().endsWith("_grid_node")) {
                    provider.appendServerData(jadeData, accessor);
                    jadeStatusProviders++;
                }
            }
            helper.assertTrue(jadeStatusProviders == 1 && jadeData.equals(data),
                    "Jade must deliver exactly one native network status for " + machine.getClass().getSimpleName());
            var lines = new ArrayList<Component>();
            var builder = new TooltipBuilder() {
                @Override public void addLine(Component line) { lines.add(line); }
                @Override public void addLine(Component line, ResourceLocation id) { lines.add(line); }
            };
            clientProviders.get(machine.getClass()).buildTooltip(machine,
                    new TooltipContext(data, helper.absolutePos(position).getCenter(), player), builder);
            helper.assertTrue(lines.equals(java.util.List.of(expected.textComponent().withStyle(ChatFormatting.GRAY))),
                    machine.getClass().getSimpleName() + " must display AE2's native gray " + expected
                            + " tooltip; received " + data + " / " + lines);
        }
    }
}
