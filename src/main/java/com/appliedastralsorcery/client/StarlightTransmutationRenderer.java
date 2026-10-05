package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.transmutation.StarlightTransmutationBlock;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import hellfirepvp.astralsorcery.client.lib.RenderTypesAS;
import hellfirepvp.astralsorcery.client.util.RenderConstellationUtil;
import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class StarlightTransmutationRenderer implements BlockEntityRenderer<StarlightTransmutationBlockEntity> {
    private final ItemRenderer items;
    public StarlightTransmutationRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @Override public void render(StarlightTransmutationBlockEntity machine, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var stack = machine.getDisplayItem();
        if (stack.isEmpty() || machine.getLevel() == null) return;
        float time = machine.getLevel().getGameTime() % 24000 + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.47 + Math.sin(time / 16.0) * 0.035, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 1.5F % 360));
        pose.scale(0.48F, 0.48F, 0.48F);
        items.renderStatic(stack, ItemDisplayContext.GROUND,
                machine.getBlockState().getValue(StarlightTransmutationBlock.WORKING) ? LightTexture.FULL_BRIGHT : light,
                overlay, pose, buffers, machine.getLevel(), 0);
        pose.popPose();
        var constellation = machine.getDisplayConstellation();
        if (constellation != null && machine.getBlockState().getValue(StarlightTransmutationBlock.WORKING))
            renderStarlight(constellation, time, pose, buffers);
    }

    private static void renderStarlight(BaseConstellation constellation, float time, PoseStack pose, MultiBufferSource buffers) {
        float pulse = 0.72F + 0.18F * (float) Math.sin(time * 0.12);
        pose.pushPose();
        pose.translate(0.5, 0.335, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.35F % 360));
        RenderConstellationUtil.drawConstellationInWorld(constellation, pose, buffers, new Vector3(), 0.52F, 0.65F, pulse);
        pose.popPose();

        int color = constellation.getConstellationColor().copyWithAlpha(180).getColor();
        var glow = buffers.getBuffer(RenderTypesAS.CONSTELLATION_WORLD_STAR);
        for (int i = 0; i < 8; i++) {
            double phase = time * 0.045 + i * Math.PI / 4;
            float size = 0.024F + 0.009F * (float) Math.sin(time * 0.14 + i);
            pose.pushPose();
            pose.translate(0.5 + Math.cos(phase) * 0.23, 0.53 + Math.sin(phase * 2 + i) * 0.17,
                    0.5 + Math.sin(phase) * 0.23);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            var matrix = pose.last().pose();
            glow.addVertex(matrix, -size, -size, 0).setColor(color).setUv(0, 1);
            glow.addVertex(matrix, size, -size, 0).setColor(color).setUv(1, 1);
            glow.addVertex(matrix, size, size, 0).setColor(color).setUv(1, 0);
            glow.addVertex(matrix, -size, size, 0).setColor(color).setUv(0, 0);
            pose.popPose();
        }
    }
}
