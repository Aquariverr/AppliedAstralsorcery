package com.appliedastralsorcery.crystallizer;

import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MELumenCrystallizerRenderer implements BlockEntityRenderer<MELumenCrystallizerBlockEntity> {
    private final ItemRenderer items;
    public MELumenCrystallizerRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(MELumenCrystallizerBlockEntity panel, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (panel.getLevel() == null) return;
        var stack = panel.getMarker();
        if (stack.isEmpty()) return;
        float time = panel.getLevel().getGameTime() % 24000 + partialTick;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        switch (panel.getBlockState().getValue(MELumenCrystallizerBlock.BASE_FACE)) {
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case WEST -> {
                pose.mulPose(Axis.YP.rotationDegrees(90));
                pose.mulPose(Axis.XP.rotationDegrees(90));
            }
            case EAST -> {
                pose.mulPose(Axis.YP.rotationDegrees(-90));
                pose.mulPose(Axis.XP.rotationDegrees(90));
            }
            case DOWN -> { }
        }
        pose.translate(0, -0.16 + Math.sin(time / 18) * 0.02, 0);
        pose.mulPose(Axis.YP.rotationDegrees(time % 360));
        float scale = 0.45F;
        pose.scale(scale, scale, scale);
        items.renderStatic(stack, ItemDisplayContext.GROUND, light,
                overlay, pose, buffers, panel.getLevel(), 0);
        pose.popPose();
    }

    @Override public AABB getRenderBoundingBox(MELumenCrystallizerBlockEntity panel) {
        return new AABB(panel.getBlockPos());
    }
}
