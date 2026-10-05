package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import hellfirepvp.astralsorcery.client.util.RenderConstellationUtil;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ConstellationRelayRenderer implements BlockEntityRenderer<ConstellationRelayBlockEntity> {
    private static final float STAR_MAP_SCALE = 2.7F;
    private final ItemRenderer items;
    public ConstellationRelayRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(ConstellationRelayBlockEntity relay, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var level = relay.getLevel();
        if (level == null) return;
        float time = level.getGameTime() % 24000 + partialTick;
        var constellation = relay.getConstellation();
        if (constellation != null && relay.isVisible()) {
            float rotation = relay.getStarMapRotation();
            var placement = starMapPlacement(constellation, rotation);
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.scale(placement.scale(), 1, placement.scale());
            pose.translate(-placement.centerX(), 0, -placement.centerZ());
            pose.mulPose(Axis.YP.rotation(rotation));
            RenderConstellationUtil.drawConstellationInWorld(constellation, pose, buffers,
                    new Vector3(0, 0.012, 0), STAR_MAP_SCALE, 0.45F,
                    0.8F + 0.15F * (float) Math.sin(time * 0.06));
            pose.popPose();
        }
        var stack = relay.getInventory().getStackInSlot(0);
        if (!stack.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.85 + Math.sin(time / 16.0) * 0.06, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * (relay.isWorking() ? 3 : 1) % 360));
            pose.scale(0.6F, 0.6F, 0.6F);
            items.renderStatic(stack.copyWithCount(1), ItemDisplayContext.GROUND,
                    relay.isWorking() ? LightTexture.FULL_BRIGHT : light, overlay, pose, buffers, level, 0);
            pose.popPose();
        }
    }

    private record StarMapPlacement(double centerX, double centerZ, float scale) {}

    private static StarMapPlacement starMapPlacement(BaseConstellation constellation, float rotation) {
        var stars = constellation.getStars();
        if (stars.isEmpty()) return new StarMapPlacement(0, 0, 1);
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double gridCenter = BaseConstellation.STAR_GRID_WIDTH_HEIGHT / 2.0;
        double unit = STAR_MAP_SCALE / BaseConstellation.STAR_GRID_WIDTH_HEIGHT;
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        for (var star : stars) {
            double x = (star.x() - gridCenter) * unit;
            double z = (star.y() - gridCenter) * unit;
            double rotatedX = cos * x + sin * z;
            double rotatedZ = -sin * x + cos * z;
            minX = Math.min(minX, rotatedX);
            maxX = Math.max(maxX, rotatedX);
            minZ = Math.min(minZ, rotatedZ);
            maxZ = Math.max(maxZ, rotatedZ);
        }
        // Center the actual rotated pattern, including the equally sized star sprites.
        // Fit diagonal patterns inside the same 3x3 floor area without overlapping neighbors.
        double spriteWidth = 2 * unit * (Math.abs(cos) + Math.abs(sin));
        float fit = (float) Math.min(1, 2.9 / (Math.max(maxX - minX, maxZ - minZ) + spriteWidth));
        return new StarMapPlacement((minX + maxX) / 2, (minZ + maxZ) / 2, fit);
    }

    @Override public AABB getRenderBoundingBox(ConstellationRelayBlockEntity relay) {
        return new AABB(relay.getBlockPos()).inflate(1, 0.5, 1);
    }
}
