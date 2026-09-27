package com.appliedastralsorcery.client;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.items.storage.BasicStorageCell;
import com.appliedastralsorcery.AppliedAstralsorcery;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.lumen.LumenKey;
import com.appliedastralsorcery.lumen.LumenKeyType;
import com.mojang.blaze3d.vertex.PoseStack;
import hellfirepvp.astralsorcery.client.ClientProxy;
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AppliedAstralsorcery.MOD_ID, value = Dist.CLIENT)
public final class LumenClient {
    private LumenClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AEKeyRendering.register(LumenKeyType.INSTANCE, LumenKey.class, new Renderer()));
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModContent.FILAMENT_MENU.get(), MELumenFilamentScreen::new);
        event.register(ModContent.ARRAY_MENU.get(), MELumenArrayScreen::new);
        event.register(ModContent.CHALICE_MENU.get(), MEChaliceScreen::new);
        event.register(ModContent.WAND_MENU.get(), MEResonatingWandScreen::new);
    }

    @SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.ARRAY_ENTITY.get(), context ->
                new hellfirepvp.astralsorcery.client.tile.TileLumenArrayRenderer(context.getItemRenderer()));
        event.registerBlockEntityRenderer(ModContent.CHALICE_ENTITY.get(), context ->
                new hellfirepvp.astralsorcery.client.tile.TileChaliceRenderer());
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        for (var cell : ModContent.LUMEN_CELLS) {
            event.register((stack, index) -> index == 0 ? 0xFFFFFFFF : 0xFF000000 | BasicStorageCell.getColor(stack, index), cell);
        }
    }

    private static final class Renderer implements AEKeyRenderHandler<LumenKey> {
        private TextureAtlasSprite icon(LumenKey key) {
            return Minecraft.getInstance().getModelManager().getAtlas(TexturesAS.ATLAS_LUMEN)
                    .getSprite(key.lumen().getRegistryKey().orElseThrow().location());
        }

        private int color(LumenKey key) {
            return key.lumen().getColor(ClientProxy.getClientTick()).getColor();
        }

        @Override
        public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, LumenKey key) {
            int color = color(key);
            graphics.blit(x, y, 0, 16, 16, icon(key),
                    ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                    (color & 255) / 255F, ((color >>> 24) & 255) / 255F);
        }

        @Override
        public void drawOnBlockFace(PoseStack pose, MultiBufferSource buffers, LumenKey key, float scale,
                int light, Level level) {
            var sprite = icon(key);
            int color = color(key);
            var buffer = buffers.getBuffer(RenderType.entityTranslucent(TexturesAS.ATLAS_LUMEN));
            pose.pushPose();
            pose.translate(0, 0, 0.01F);
            float half = scale / 2;
            var transform = pose.last().pose();
            buffer.addVertex(transform, -half, -half, 0).setColor(color).setUv(sprite.getU0(), sprite.getV1())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
            buffer.addVertex(transform, half, -half, 0).setColor(color).setUv(sprite.getU1(), sprite.getV1())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
            buffer.addVertex(transform, half, half, 0).setColor(color).setUv(sprite.getU1(), sprite.getV0())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
            buffer.addVertex(transform, -half, half, 0).setColor(color).setUv(sprite.getU0(), sprite.getV0())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 0, 1);
            pose.popPose();
        }

        @Override
        public Component getDisplayName(LumenKey key) {
            return key.getDisplayName();
        }
    }
}
