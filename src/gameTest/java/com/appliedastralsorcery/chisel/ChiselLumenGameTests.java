package com.appliedastralsorcery.chisel;

import java.util.Set;

import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNode;
import hellfirepvp.astralsorcery.common.tile.TileLumenArray;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class ChiselLumenGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 5, 4), SOURCE = new BlockPos(10, 5, 4);

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void idleAndBlockedModesRefillFromNativeArray(GameTestHelper helper) {
        var machine = machine(helper);
        var source = source(helper);
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(machine.getLumenAmount() > 0 && machine.getProgress() == 0,
                    "An empty inventory must still request Evorsio from the native network");
            int previous = machine.getLumenAmount();
            machine.toggleDroppedItemMode();
            helper.runAfterDelay(22, () -> {
                helper.assertTrue(machine.getLumenAmount() > previous,
                        "Dropped-item mode must refill even without a target");
                int beforeBlocked = machine.getLumenAmount();
                machine.toggleDroppedItemMode();
                machine.getInventory().setStackInSlot(0, ItemsAS.STARMETAL_INGOT.toStack());
                for (int slot = 1; slot <= 9; slot++) machine.getInventory().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
                helper.runAfterDelay(22, () -> {
                    helper.assertTrue(machine.getLumenAmount() > beforeBlocked && machine.getProgress() == 0,
                            "Blocked output must not block network refill");
                    helper.assertTrue(machine.getLumenAmount() + source.getContainedLumen(LumenAS.EVORSIO).orElseThrow().getAmount() == 4000,
                            "Network transfer must conserve lumen");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "wand_empty", timeoutTicks = 100)
    public static void nativeRelaysRouteAroundBlockedDirectPath(GameTestHelper helper) {
        var machine = machine(helper);
        var source = source(helper);
        helper.setBlock(new BlockPos(6, 5, 4), Blocks.STONE);
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(machine.getLumenAmount() == 0, "A blocked direct path must not transmit");
            var relay = new BlockPos(6, 5, 8);
            helper.setBlock(relay, BlocksAS.LUMEN_FILAMENT.get());
            var relayPos = helper.absolutePos(relay);
            LumenNetworkHelper.createNode(helper.getLevel(), relayPos, relayPos.getCenter(), LumenNode.ConnectionType.TRANSMISSION);
            machine.getInventory().setStackInSlot(0, ItemsAS.STARMETAL_INGOT.toStack());
            helper.runAfterDelay(62, () -> {
                helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty()
                        && !machine.getInventory().getStackInSlot(1).isEmpty(),
                        "A real relay path must power a complete operation");
                helper.assertTrue(machine.getLumenAmount() + source.getContainedLumen(LumenAS.EVORSIO).orElseThrow().getAmount() == 3975,
                        "Relayed processing must consume exactly 25 Lm");
                helper.succeed();
            });
        });
    }

    private static AutoChiselBlockEntity machine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModContent.AUTO_CHISEL.get());
        return (AutoChiselBlockEntity) helper.getBlockEntity(MACHINE);
    }

    private static ILumenHandler source(GameTestHelper helper) {
        helper.setBlock(SOURCE, BlocksAS.LUMEN_ARRAY.get());
        var source = ((TileLumenArray) helper.getBlockEntity(SOURCE)).getTileData().getLumenHandler();
        source.fill(LumenAS.EVORSIO.stack(4000), ILumenHandler.Action.EXECUTE);
        var pos = helper.absolutePos(SOURCE);
        var node = LumenNetworkHelper.createNode(helper.getLevel(), pos, pos.getCenter(), LumenNode.ConnectionType.SOURCE).orElseThrow();
        LumenNetworkHelper.setNodeProvidedLumenTypes(helper.getLevel(), node, Set.of(LumenAS.EVORSIO.get()));
        return source;
    }
}
