package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import com.appliedastralsorcery.attunement.ConstellationRelayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import hellfirepvp.astralsorcery.client.lib.RenderTypesAS;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ConstellationRelayRenderer implements BlockEntityRenderer<ConstellationRelayBlockEntity> {
    private final ItemRenderer items;
    private final ConstellationRenderGeometry.Cache geometry = new ConstellationRenderGeometry.Cache();
    private final SingleItemRenderCache renderItems = new SingleItemRenderCache();
    public ConstellationRelayRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(ConstellationRelayBlockEntity relay, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var level = relay.getLevel();
        if (level == null) return;
        float time = level.getGameTime() % 24000 + partialTick;
        var constellation = relay.getConstellation();
        if (constellation != null && relay.isVisible()) {
            float rotation = relay.getStarMapRotation();
            var placement = geometry.get(constellation, rotation);
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.scale(placement.scale(), 1, placement.scale());
            pose.translate(-placement.centerX(), 0, -placement.centerZ());
            pose.mulPose(Axis.YP.rotation(rotation));
            var color = constellation.getConstellationColor();
            float alpha = 0.8F + 0.15F * (float) Math.sin(time * 0.06);
            int rgb = color.getColor() & 0xFFFFFF;
            draw(placement, true, pose.last().pose(), buffers.getBuffer(RenderTypesAS.CONSTELLATION_WORLD_CONNECTION),
                    Math.round(color.getAlpha() * alpha * 0.8F) << 24 | rgb);
            draw(placement, false, pose.last().pose(), buffers.getBuffer(RenderTypesAS.CONSTELLATION_WORLD_STAR),
                    Math.round(color.getAlpha() * alpha) << 24 | rgb);
            pose.popPose();
        }
        var stack = relay.getInventory().getStackInSlot(0);
        if (!stack.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0.85 + Math.sin(time / 16.0) * 0.06, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(time * (relay.isWorking() ? 3 : 1) % 360));
            pose.scale(0.6F, 0.6F, 0.6F);
            items.renderStatic(renderItems.get(stack), ItemDisplayContext.GROUND,
                    relay.isWorking() ? LightTexture.FULL_BRIGHT : light, overlay, pose, buffers, level, 0);
            pose.popPose();
        }
    }

    private static void draw(ConstellationRenderGeometry geometry, boolean connections, Matrix4f pose,
            VertexConsumer buffer, int color) {
        int count = connections ? geometry.connectionVertexCount() : geometry.starVertexCount();
        for (int i = 0; i < count; i++) {
            int corner = i & 3;
            buffer.addVertex(pose, geometry.coordinate(connections, i, 0), geometry.coordinate(connections, i, 1),
                            geometry.coordinate(connections, i, 2))
                    .setUv(corner == 0 || corner == 3 ? 1 : 0, corner >= 2 ? 1 : 0).setColor(color);
        }
    }

    @Override public AABB getRenderBoundingBox(ConstellationRelayBlockEntity relay) {
        return new AABB(relay.getBlockPos()).inflate(1, 0.5, 1);
    }
}
