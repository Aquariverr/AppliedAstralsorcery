package com.appliedastralsorcery.client;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chalice.MEChaliceBlock;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import hellfirepvp.astralsorcery.client.ClientProxy;
import hellfirepvp.astralsorcery.client.lib.RenderTypesAS;
import hellfirepvp.astralsorcery.client.resource.UVFrame;
import hellfirepvp.astralsorcery.client.util.RenderSpriteUtil;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;

/** GUI miniatures using the same models, textures and fluid geometry as the world blocks. */
final class AstralMachinePreview {
    private AstralMachinePreview() {}

    static void array(GuiGraphics graphics, int x, int y, int starlight) {
        begin(graphics, x, y, 32F);
        try {
            float fill = Math.clamp(starlight / 2000F, 0F, 1F);
            if (fill > 0F) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0.5F, (6F + 3.5F * fill) / 16F, 0.5F);
                pose.scale(6F / 16F, 7F * fill / 16F, 6F / 16F);
                fluidCube(graphics, new FluidStack(FluidsAS.LIQUID_STARLIGHT.getSource().get(), starlight), 1F, 204);
                pose.popPose();
                // Draw the liquid before the model's translucent glass walls.
                graphics.flush();
            }
            block(graphics, ModContent.ME_LUMEN_ARRAY.get().defaultBlockState());
        } finally {
            end(graphics);
        }
    }

    static void chalice(GuiGraphics graphics, int x, int y, FluidStack fluid, int amount, int capacity,
            float partialTick) {
        begin(graphics, x, y, 34F);
        try {
            block(graphics, ModContent.ME_CHALICE.get().defaultBlockState()
                    .setValue(MEChaliceBlock.TOP_CONNECTED, true)
                    .setValue(MEChaliceBlock.BOTTOM_CONNECTED, true));
            graphics.flush();
            if (!fluid.isEmpty() && amount > 0 && capacity > 0) {
                // TileChaliceRenderer uses a 1/8..1/2 block edge, scaled equally on all axes.
                float size = 0.125F + 0.375F * Math.clamp(amount / (float) capacity, 0F, 1F);
                float rotation = (ClientProxy.getClientTick() + partialTick) % 720F;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0.5F, 1.4F, 0.5F);
                pose.mulPose(Axis.XP.rotationDegrees(rotation * 0.5F));
                pose.mulPose(Axis.YP.rotationDegrees(rotation));
                pose.mulPose(Axis.ZP.rotationDegrees(rotation * 0.5F));
                pose.scale(size, size, size);
                fluidCube(graphics, fluid, size, 255);
                pose.popPose();
            }
        } finally {
            end(graphics);
        }
    }

    private static void begin(GuiGraphics graphics, int x, int y, float scale) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(25F));
        pose.mulPose(Axis.YP.rotationDegrees(225F));
        pose.translate(-0.5F, -0.5F, -0.5F);
        Lighting.setupFor3DItems();
    }

    private static void block(GuiGraphics graphics, BlockState state) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, graphics.pose(),
                graphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
    }

    private static void fluidCube(GuiGraphics graphics, FluidStack fluid, float textureScale, int alpha) {
        var sprite = RenderSpriteUtil.getTexture(fluid);
        float width = sprite.getU1() - sprite.getU0();
        float height = sprite.getV1() - sprite.getV0();
        var uv = new UVFrame(sprite.getU0() + width * (1F - textureScale) / 2F,
                sprite.getV0() + height * (1F - textureScale) / 2F,
                width * textureScale, height * textureScale);
        int color = (alpha << 24) | (IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid) & 0xFFFFFF);
        var buffer = graphics.bufferSource().getBuffer(RenderTypesAS.TER_CHALICE_LIQUID);
        // The world cube helper hardcodes an upward normal for every face. GUI lighting needs
        // the actual transformed face normals to keep the fluid bright and visibly three-dimensional.
        fluidFace(graphics, buffer, uv, color, -0.5F, -0.5F, 0.5F, 1, 0, 0, 0, 0, -1);
        fluidFace(graphics, buffer, uv, color, -0.5F, 0.5F, -0.5F, 1, 0, 0, 0, 0, 1);
        fluidFace(graphics, buffer, uv, color, -0.5F, -0.5F, -0.5F, 1, 0, 0, 0, 1, 0);
        fluidFace(graphics, buffer, uv, color, 0.5F, -0.5F, 0.5F, -1, 0, 0, 0, 1, 0);
        fluidFace(graphics, buffer, uv, color, -0.5F, -0.5F, -0.5F, 0, 1, 0, 0, 0, 1);
        fluidFace(graphics, buffer, uv, color, 0.5F, -0.5F, -0.5F, 0, 0, 1, 0, 1, 0);
    }

    private static void fluidFace(GuiGraphics graphics, VertexConsumer buffer, UVFrame uv, int color,
            float x, float y, float z, int ux, int uy, int uz, int vx, int vy, int vz) {
        int nx = vy * uz - vz * uy;
        int ny = vz * ux - vx * uz;
        int nz = vx * uy - vy * ux;
        fluidVertex(graphics, buffer, color, x, y, z, uv.u(), uv.v(), nx, ny, nz);
        fluidVertex(graphics, buffer, color, x + vx, y + vy, z + vz, uv.u(), uv.v() + uv.vHeight(), nx, ny, nz);
        fluidVertex(graphics, buffer, color, x + ux + vx, y + uy + vy, z + uz + vz,
                uv.u() + uv.uWidth(), uv.v() + uv.vHeight(), nx, ny, nz);
        fluidVertex(graphics, buffer, color, x + ux, y + uy, z + uz, uv.u() + uv.uWidth(), uv.v(), nx, ny, nz);
    }

    private static void fluidVertex(GuiGraphics graphics, VertexConsumer buffer, int color,
            float x, float y, float z, float u, float v, int nx, int ny, int nz) {
        var pose = graphics.pose().last();
        buffer.addVertex(pose.pose(), x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    private static void end(GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }
}
