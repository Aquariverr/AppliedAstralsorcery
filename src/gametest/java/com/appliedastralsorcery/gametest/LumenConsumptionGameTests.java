package com.appliedastralsorcery.gametest;

import com.appliedastralsorcery.ModConfig;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chisel.AutoChiselBlockEntity;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class LumenConsumptionGameTests {
    @GameTest(template = "attunement_test")
    public static void chiselInventoryUsesConfiguredCost(GameTestHelper helper) {
        checkChiselCost(helper, false);
    }

    @GameTest(template = "attunement_test")
    public static void chiselDroppedItemsUseConfiguredCost(GameTestHelper helper) {
        checkChiselCost(helper, true);
    }

    private static void checkChiselCost(GameTestHelper helper, boolean drops) {
        var pos = new BlockPos(10, 3, 10);
        helper.setBlock(pos, ModContent.AUTO_CHISEL.get());
        var chisel = (AutoChiselBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        chisel.toggleAutoInput();
        chisel.toggleAutoOutput();
        if (drops) chisel.toggleDroppedItemMode();
        int previousCost = ModConfig.AUTO_CHISEL_LUMEN_PER_CRAFT.get();
        try {
            // Run synchronously and restore the shared config before other GameTests can tick.
            for (int cost : new int[]{37, 9}) {
                ModConfig.AUTO_CHISEL_LUMEN_PER_CRAFT.set(cost);
                ItemEntity input = null;
                if (drops) {
                    var center = chisel.getBlockPos().above().getCenter();
                    input = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, ItemsAS.STARMETAL_INGOT.toStack());
                    var stack = input.getItem();
                    if (stack.getItem().hasCustomEntity(stack))
                        input = (ItemEntity) stack.getItem().createEntity(helper.getLevel(), input, stack);
                    input.setNoGravity(true);
                    input.setDeltaMovement(Vec3.ZERO);
                    helper.assertTrue(helper.getLevel().addFreshEntity(input), "Dropped input must spawn");
                } else {
                    chisel.getInventory().setStackInSlot(0, ItemsAS.STARMETAL_INGOT.toStack());
                }
                var lumen = chisel.getLumenHandler();
                lumen.fill(LumenAS.EVORSIO.stack(cost - 1 - chisel.getLumenAmount()), ILumenHandler.Action.EXECUTE);
                chisel.serverTick();
                helper.assertTrue(chisel.getProgress() == 0 && chisel.getStatus() == AutoChiselBlockEntity.Status.NO_LUMEN,
                        "Insufficient configured cost must pause processing");
                helper.assertTrue(chisel.getLumenAmount() == cost - 1, "Paused processing must retain lumen");
                lumen.fill(LumenAS.EVORSIO.stack(2), ILumenHandler.Action.EXECUTE);
                for (int tick = 1; tick < chisel.getDuration(); tick++) chisel.serverTick();
                helper.assertTrue(chisel.getProgress() == chisel.getDuration() - 1 && chisel.getLumenAmount() == cost + 1,
                        "Progress must not consume lumen before completion");
                chisel.serverTick();
                helper.assertTrue(chisel.getLumenAmount() == 1 && chisel.getProgress() == 0,
                        "Completion must consume the configured cost exactly once");
                if (drops) {
                    helper.assertTrue(input.isRemoved(), "Completed dropped input must be consumed");
                    helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                                    item -> item.getItem().is(ItemsAS.STARDUST)).isEmpty(),
                            "Dropped processing must emit stardust");
                } else {
                    helper.assertTrue(chisel.getInventory().getStackInSlot(0).isEmpty(), "Completed input must leave its slot");
                    boolean foundDust = false;
                    for (int slot = 1; slot <= AutoChiselBlockEntity.OUTPUT_SLOTS; slot++)
                        foundDust |= chisel.getInventory().getStackInSlot(slot).is(ItemsAS.STARDUST);
                    helper.assertTrue(foundDust, "Inventory processing must output stardust");
                }
            }
        } finally {
            ModConfig.AUTO_CHISEL_LUMEN_PER_CRAFT.set(previousCost);
        }
        helper.succeed();
    }
}
