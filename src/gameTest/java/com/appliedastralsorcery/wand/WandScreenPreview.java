package com.appliedastralsorcery.wand;

import com.appliedastralsorcery.client.MEResonatingWandScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in rendering smoke test; uses no world and is excluded from release builds. */
@EventBusSubscriber(modid = "appliedas", value = Dist.CLIENT)
public final class WandScreenPreview {
    private static int phase, ticks;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static final String[] LANGUAGES = {"zh_cn", "en_us"};

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws java.io.IOException {
        if (!Boolean.getBoolean("appliedas.wandPreview")) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null || client.screen == null) return;
        if (phase >= 4) { client.stop(); return; }
        String language = LANGUAGES[phase / 2];
        if (phase % 2 == 0) {
            if (reload == null) {
                client.options.guiScale().set(2);
                client.resizeDisplay();
                client.getLanguageManager().setSelected(language);
                reload = client.reloadResourcePacks();
                return;
            }
            if (!reload.isDone()) return;
            reload.join();
            var inventory = new Inventory(null);
            var menu = new MEResonatingWandMenu(0, inventory);
            menu.setData(0, phase == 0 ? 3 : 6);
            menu.setData(1, 1);
            var title = Component.translatable("gui.appliedas.wand.title");
            var content = new MEResonatingWandScreen(menu, inventory, title);
            // A plain host skips AbstractContainerScreen's player-liveness tick; actual rendering is unchanged.
            client.setScreen(new net.minecraft.client.gui.screens.Screen(title) {
                @Override protected void init() { content.init(client, width, height); }
                @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
                    content.render(graphics, x, y, partial);
                }
            });
            ticks = 0;
            phase++;
        } else if (++ticks >= 20) {
            var path = client.gameDirectory.toPath().resolve("screenshots/wand-settings-" + language + ".png");
            java.nio.file.Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(path); }
            phase++;
            reload = null;
        }
    }
}
