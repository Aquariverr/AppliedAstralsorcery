package com.appliedastralsorcery.transmutation;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.data.level.FocalPointData;
import hellfirepvp.astralsorcery.common.focal.node.BasicFocalPointNode;
import hellfirepvp.astralsorcery.common.lib.ConstellationsAS;
import hellfirepvp.astralsorcery.common.lib.DataAS;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.util.data.ColumnPos;
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
public final class TransmutationFocalPointGameTests {
    private static final BlockPos POS = new BlockPos(3, 5, 3);

    @GameTest(template = "wand_empty", batch = "transmutation_focal", skyAccess = true, timeoutTicks = 100)
    public static void focalPointAloneTransmutesAndExportsToME(GameTestHelper helper) {
        var machine = setup(helper);
        var point = addPoint(helper, POS, ConstellationsAS.MINERALIS.get());
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(machine.hasStarlight() && machine.getDisplayConstellation() == ConstellationsAS.MINERALIS.get(),
                        "An exposed chamber at the focal point must receive its constellation without a crystal");
                helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(), "Focal transmutation must consume the exact input");
                var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
                helper.assertTrue(storage.extract(AEItemKey.of(Items.QUARTZ), 10, Actionable.SIMULATE, IActionSource.empty()) == 2
                        && storage.extract(AEItemKey.of(Items.DIAMOND), 10, Actionable.SIMULATE, IActionSource.empty()) == 3,
                        "Focal transmutation must export every result exactly once");
                data(helper).removeNode(point);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "wand_empty", batch = "transmutation_focal", skyAccess = true, timeoutTicks = 100)
    public static void focalLightPausesImmediatelyAndResumesWithoutLosingProgress(GameTestHelper helper) {
        var machine = setup(helper);
        var point = addPoint(helper, POS, ConstellationsAS.MINERALIS.get());
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            tick(helper, machine, 5);
            helper.assertTrue(machine.getProgress() == 5, "Nighttime focal light must start processing");
            helper.getLevel().setDayTime(6000);
            tick(helper, machine, 5);
            assertPaused(helper, machine);
            helper.getLevel().setDayTime(18000);
            helper.setBlock(POS.above(), Blocks.STONE);
            tick(helper, machine, 5);
            assertPaused(helper, machine);
            helper.setBlock(POS.above(), Blocks.AIR);
            data(helper).removeNode(point);
            tick(helper, machine, 5);
            assertPaused(helper, machine);
            data(helper).addNode(point);
            tick(helper, machine, 1);
            helper.assertTrue(machine.getProgress() == 6 && machine.hasStarlight(), "Restored focal light must resume saved progress");
            data(helper).removeNode(point);
            helper.succeed();
        });
    }

    @GameTest(template = "wand_empty", batch = "transmutation_focal", skyAccess = true, timeoutTicks = 100)
    public static void focalConstellationAndExactColumnAreRequiredButBeamsStillWork(GameTestHelper helper) {
        var machine = setup(helper);
        var point = addPoint(helper, POS.east(2), ConstellationsAS.MINERALIS.get());
        helper.runAfterDelay(25, () -> {
            machine.getInventory().setStackInSlot(0, new ItemStack(Items.AMETHYST_SHARD));
            tick(helper, machine, 1);
            helper.assertTrue(!machine.hasStarlight() && machine.getProgress() == 0, "Nearby focal points must not power another column");
            data(helper).removeNode(point);
            var wrong = addPoint(helper, POS, ConstellationsAS.AEVITAS.get());
            tick(helper, machine, 1);
            helper.assertTrue(machine.getStatus() == StarlightTransmutationBlockEntity.Status.WRONG_CONSTELLATION
                    && machine.getProgress() == 0, "Focal light must respect recipe constellation requirements");
            machine.receiveStarlight(helper.getLevel(), new StarlightTransmissionPacket(ConstellationsAS.MINERALIS.get(), 10));
            tick(helper, machine, 1);
            helper.assertTrue(machine.getProgress() == 1 && machine.getDisplayConstellation() == ConstellationsAS.MINERALIS.get(),
                    "A matching incoming beam must still work alongside another focal constellation");
            data(helper).removeNode(wrong);
            helper.succeed();
        });
    }

    private static StarlightTransmutationBlockEntity setup(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000);
        helper.setBlock(POS, ModContent.STARLIGHT_TRANSMUTATION_CHAMBER.get());
        helper.setBlock(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(POS.east(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) helper.getBlockEntity(POS.east())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        return (StarlightTransmutationBlockEntity) helper.getBlockEntity(POS);
    }

    private static FocalPointData data(GameTestHelper helper) {
        return (FocalPointData) DataAS.DOMAIN_AS.getData(helper.getLevel(), DataAS.KEY_FOCAL_POINT_DATA);
    }

    private static BasicFocalPointNode addPoint(GameTestHelper helper, BlockPos pos, BaseConstellation constellation) {
        var point = new BasicFocalPointNode(ColumnPos.of(helper.absolutePos(pos)), constellation);
        data(helper).addNode(point);
        return point;
    }

    private static void tick(GameTestHelper helper, StarlightTransmutationBlockEntity machine, int count) {
        for (int i = 0; i < count; i++) machine.serverTick(helper.getLevel());
    }

    private static void assertPaused(GameTestHelper helper, StarlightTransmutationBlockEntity machine) {
        helper.assertTrue(!machine.hasStarlight() && machine.getProgress() == 5
                && machine.getStatus() == StarlightTransmutationBlockEntity.Status.NO_STARLIGHT
                && machine.getInventory().getStackInSlot(0).getCount() == 1,
                "Unavailable focal light must pause without stale light or input loss");
    }
}
