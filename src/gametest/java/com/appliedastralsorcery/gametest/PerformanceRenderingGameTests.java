package com.appliedastralsorcery.gametest;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.attunement.AttunementLayout;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import com.appliedastralsorcery.client.AttunementRingGeometry;
import com.appliedastralsorcery.client.ConstellationRenderGeometry;
import com.appliedastralsorcery.client.SingleItemRenderCache;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.util.data.ColorWrapper;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Only common math/data helpers are loaded: no Renderer, PoseStack, or Minecraft client class. */
@GameTestHolder("appliedas")
@PrefixGameTestTemplate(false)
public final class PerformanceRenderingGameTests {
    @GameTest(template = "attunement_test")
    public static void emptyAltarBatchPreservesExistingResources(GameTestHelper helper) throws ReflectiveOperationException {
        var type = Class.forName("com.appliedastralsorcery.altar.AltarResourceBuffer");
        var constructor = type.getDeclaredConstructor(Runnable.class);
        constructor.setAccessible(true);
        var changes = new int[1];
        var buffer = constructor.newInstance((Runnable) () -> changes[0]++);
        var fluidField = type.getDeclaredField("fluid");
        var lumenField = type.getDeclaredField("lumen");
        fluidField.setAccessible(true);
        lumenField.setAccessible(true);
        var fluid = (IFluidHandler) fluidField.get(buffer);
        var lumen = (ILumenHandler) lumenField.get(buffer);
        fluid.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        lumen.fill(LumenAS.PRISMATIC.stack(100), ILumenHandler.Action.EXECUTE);
        int previousChanges = changes[0];
        var canInsert = type.getDeclaredMethod("canInsert", java.util.List.class);
        canInsert.setAccessible(true);
        helper.assertTrue((Boolean) canInsert.invoke(buffer, java.util.List.of()), "An empty resource batch always fits");
        helper.assertTrue(changes[0] == previousChanges && fluid.getFluidInTank(0).getAmount() == 1000
                        && lumen.drain(LumenAS.PRISMATIC.get(), 100, ILumenHandler.Action.SIMULATE).getAmount() == 100,
                "The empty-batch fast path must leave existing fluid and lumen untouched");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void batchedRingOriginsMatchOriginalTransforms(GameTestHelper helper) {
        var geometry = new AttunementRingGeometry();
        var actual = new Vector3f();
        var expected = new Vector3f();
        int checked = 0;
        for (float clock : new float[]{0, 1, 40, 100, 359, 720, 1799, 3599, 3600}) {
            for (float rise : new float[]{0, 0.1F, 0.5F, 0.9F, 1}) {
                for (float phase : new float[]{0, 17.5F, 359.9F}) {
                    geometry.update(clock, rise, phase, 12);
                    for (int i = 0; i < 12; i++) {
                        // The original PoseStack multiplied these independent rotations for every star.
                        float precession = clock * 0.2F;
                        float tilt = 9 + 5 * Mth.sin(clock * Mth.TWO_PI / 360);
                        var transform = new Matrix4f().translate(0.5F, 4.5F / 16 + 6.5F / 16 * rise, 0.5F)
                                .rotate(new Quaternionf().rotationY(precession * Mth.DEG_TO_RAD))
                                .rotate(new Quaternionf().rotationX(tilt * rise * Mth.DEG_TO_RAD))
                                .rotate(new Quaternionf().rotationY(-precession * Mth.DEG_TO_RAD))
                                .rotate(new Quaternionf().rotationY(-(phase + i * 360F / 12) * Mth.DEG_TO_RAD))
                                .translate(0, rise * 0.35F / 16 * Mth.sin(clock * Mth.TWO_PI / 120 + i * 0.52F),
                                        -Mth.lerp(rise, 16.5F / 16, 17.5F / 16));
                        transform.transformPosition(expected.zero());
                        geometry.starCentre(i, actual);
                        helper.assertTrue(actual.distanceSquared(expected) < 1.0E-10F,
                                "Particle origins must match the original visible ring at station " + i);
                        checked++;
                    }
                }
            }
        }
        System.out.println("Verified " + checked + " cached ring origins against original transforms");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void cachedStarMapVerticesMatchNativeGeometry(GameTestHelper helper) {
        var cache = new ConstellationRenderGeometry.Cache();
        for (var station : AttunementLayout.STATIONS) {
            var constellation = station.constellation().get();
            float rotation = (float) Math.atan2(-station.offset().getX(), -station.offset().getZ());
            var geometry = cache.get(constellation, rotation);
            helper.assertTrue(geometry == cache.get(constellation, rotation), "Unchanged maps must reuse geometry");
            helper.assertTrue(geometry != cache.get(constellation, rotation + 0.5F), "Rotation changes must replace layout");
            geometry = cache.get(constellation, rotation);
            float size = 1F / BaseConstellation.STAR_GRID_WIDTH_HEIGHT * 2.7F;
            var origin = new Vector3(BaseConstellation.STAR_GRID_WIDTH_HEIGHT, 0,
                    BaseConstellation.STAR_GRID_WIDTH_HEIGHT).multiply(-0.5F * size);
            var pos = new Vector3(0, 0.012, 0);
            int vertex = 0;
            for (var link : constellation.getStarConnections()) {
                pos = pos.copy().addY(0.001F);
                var u = link.getRight().asLevelVector().subtract(link.getLeft().asLevelVector()).multiply(size);
                var v = u.copy().crossProduct(Vector3.RotAxis.Y_AXIS.getVector()).setY(0).normalize().multiply(0.45F * size);
                var base = pos.copy().add(link.getLeft().asLevelVector().multiply(size)).subtract(v.copy().divide(2)).add(origin);
                assertQuad(helper, geometry, true, vertex, base, u, v);
                vertex += 4;
            }
            helper.assertTrue(vertex == geometry.connectionVertexCount(), "Every connection quad must remain present");
            pos = pos.copy().addY(0.002F);
            vertex = 0;
            for (var star : constellation.getStars()) {
                var base = pos.copy().add(star.asLevelVector().multiply(size)).add(-size, 0, -size).add(origin);
                assertQuad(helper, geometry, false, vertex, base, new Vector3(size * 2, 0, 0), new Vector3(0, 0, size * 2));
                vertex += 4;
            }
            helper.assertTrue(vertex == geometry.starVertexCount(), "Every star quad must remain present");
            assertPlacement(helper, constellation, rotation, geometry);
        }
        var empty = new BaseConstellation(ColorWrapper.WHITE);
        var geometry = cache.get(empty, 0);
        helper.assertTrue(geometry.starVertexCount() == 0 && geometry.connectionVertexCount() == 0
                        && geometry.centerX() == 0 && geometry.centerZ() == 0 && geometry.scale() == 1,
                "Empty constellations must retain their neutral layout");
        helper.assertTrue(geometry != cache.get(new BaseConstellation(ColorWrapper.WHITE), 0),
                "Replacement constellation objects must never reuse stale geometry");
        helper.succeed();
    }

    private static void assertQuad(GameTestHelper helper, ConstellationRenderGeometry geometry, boolean connection,
            int vertex, Vector3 base, Vector3 u, Vector3 v) {
        for (int i = 0; i < 4; i++) {
            int ui = i == 1 || i == 2 ? 1 : 0, vi = i < 2 ? 1 : 0;
            var expected = base.copy().add(u.copy().multiply(ui)).add(v.copy().multiply(vi));
            helper.assertTrue(Math.abs(geometry.coordinate(connection, vertex + i, 0) - expected.getX()) < 1.0E-6
                            && Math.abs(geometry.coordinate(connection, vertex + i, 1) - expected.getY()) < 1.0E-6
                            && Math.abs(geometry.coordinate(connection, vertex + i, 2) - expected.getZ()) < 1.0E-6,
                    "Cached vertices must preserve native quad ordering, position, and depth");
        }
    }

    private static void assertPlacement(GameTestHelper helper, BaseConstellation constellation, float rotation,
            ConstellationRenderGeometry geometry) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double unit = 2.7F / BaseConstellation.STAR_GRID_WIDTH_HEIGHT;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        for (var star : constellation.getStars()) {
            double x = (star.x() - BaseConstellation.STAR_GRID_WIDTH_HEIGHT / 2.0) * unit;
            double z = (star.y() - BaseConstellation.STAR_GRID_WIDTH_HEIGHT / 2.0) * unit;
            minX = Math.min(minX, cos * x + sin * z);
            maxX = Math.max(maxX, cos * x + sin * z);
            minZ = Math.min(minZ, -sin * x + cos * z);
            maxZ = Math.max(maxZ, -sin * x + cos * z);
        }
        double spriteWidth = 2 * unit * (Math.abs(cos) + Math.abs(sin));
        float expectedScale = (float) Math.min(1, 2.9 / (Math.max(maxX - minX, maxZ - minZ) + spriteWidth));
        helper.assertTrue(geometry.centerX() == (minX + maxX) / 2 && geometry.centerZ() == (minZ + maxZ) / 2
                        && geometry.scale() == expectedScale,
                "Cached layout must preserve centering and the 3x3 map footprint");
    }

    @GameTest(template = "attunement_test")
    public static void singleItemSnapshotsInvalidateOnComponentChanges(GameTestHelper helper) {
        var cache = new SingleItemRenderCache();
        var stack = new ItemStack(Items.DIAMOND, 32);
        var original = cache.get(stack);
        helper.assertTrue(original != stack && original.getCount() == 1 && stack.getCount() == 32,
                "A render snapshot must preserve the inventory and display one item");
        stack.setCount(16);
        helper.assertTrue(cache.get(stack) == original, "Count-only inventory changes must reuse the snapshot");
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Changed crystal"));
        var updated = cache.get(stack);
        helper.assertTrue(updated != original && updated.getHoverName().getString().equals("Changed crystal")
                        && !original.has(DataComponents.CUSTOM_NAME),
                "Component changes must create a fresh snapshot without mutating previous renders");
        updated.setCount(2);
        helper.assertTrue(cache.get(stack).getCount() == 1, "A custom renderer cannot retain a modified snapshot count");
        helper.assertTrue(cache.get(ItemStack.EMPTY).isEmpty(), "Empty inventories must render nothing");
        helper.succeed();
    }

    @GameTest(template = "attunement_test")
    public static void cachedRelayCandidatesStillDetectLayoutChanges(GameTestHelper helper) {
        var center = new BlockPos(10, 2, 10);
        var relayPos = center.offset(AttunementLayout.STATIONS.getFirst().offset());
        var secondCenter = relayPos.subtract(AttunementLayout.STATIONS.get(1).offset());
        helper.setBlock(center, ModContent.IRIDESCENT_ATTUNEMENT_ALTAR.get());
        helper.setBlock(relayPos, ModContent.CONSTELLATION_RELAY.get());
        var relay = (ConstellationRelayBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relayPos));
        var receiver = new ConstellationRelayBlockEntity(relay.getBlockPos(), relay.getBlockState());
        var tag = relay.getUpdateTag(helper.getLevel().registryAccess());
        for (int i = 0; i < AttunementLayout.STATIONS.size(); i++) {
            tag.putInt("station", i);
            receiver.handleUpdateTag(tag, helper.getLevel().registryAccess());
            var offset = AttunementLayout.STATIONS.get(i).offset();
            helper.assertTrue(receiver.getStarMapRotation() == (float) Math.atan2(-offset.getX(), -offset.getZ()),
                    "Every cached station angle must match its original outward rotation");
        }
        relay.serverTick();
        helper.assertTrue(relay.getConstellation() == AttunementLayout.STATIONS.getFirst().constellation().get(),
                "A single altar must bind the relay using its cached candidate position");
        helper.setBlock(secondCenter, ModContent.IRIDESCENT_ATTUNEMENT_ALTAR.get());
        relay.serverTick();
        helper.assertTrue(relay.getConstellation() == null, "Adding an ambiguous altar must immediately unbind the relay");
        helper.setBlock(secondCenter, Blocks.AIR);
        relay.serverTick();
        helper.assertTrue(relay.getConstellation() == AttunementLayout.STATIONS.getFirst().constellation().get(),
                "Removing ambiguity must immediately restore the original binding");
        helper.setBlock(center, Blocks.AIR);
        relay.serverTick();
        helper.assertTrue(relay.getConstellation() == null, "Removing the altar must invalidate the binding immediately");
        helper.succeed();
    }
}
