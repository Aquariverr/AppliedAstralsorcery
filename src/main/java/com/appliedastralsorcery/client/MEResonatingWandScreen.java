package com.appliedastralsorcery.client;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.wand.MEResonatingWandItem;
import com.appliedastralsorcery.wand.MEResonatingWandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Marble frame, a midnight star chart, and aquamarine seals for the three settings. */
public final class MEResonatingWandScreen extends AbstractContainerScreen<MEResonatingWandMenu> {
    private static final int GOLD = 0xFFD4BE76, GOLD_SHADE = 0xFF806630, INK = 0xFF343A47, BLUE = 0xFF9FE9F2;
    private static final ResourceLocation MARBLE = ResourceLocation.fromNamespaceAndPath(
            "astralsorcery", "textures/block/marble_raw.png");
    private static final ResourceLocation WOOD = ResourceLocation.fromNamespaceAndPath(
            "astralsorcery", "textures/block/infused_wood.png");

    public MEResonatingWandScreen(MEResonatingWandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 232;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.appliedas.wand." + key, args);
    }

    @Override protected void init() {
        super.init();
        addToggle(72, MEResonatingWandItem.USE_ME_ITEMS, "items");
        addToggle(115, MEResonatingWandItem.BUILD_FLUIDS, "fluids");
        addToggle(158, MEResonatingWandItem.REPLACE_BLOCKS, "replace");
    }

    private void addToggle(int y, int option, String key) {
        addRenderableWidget(new SealButton(leftPos + 15, topPos + y, option, key));
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x + 4, y + 5, x + imageWidth + 4, y + imageHeight + 5, 0x88000000);
        tile(g, WOOD, x, y, imageWidth, imageHeight);
        tile(g, MARBLE, x + 3, y + 3, imageWidth - 6, imageHeight - 6);
        g.fill(x + 5, y + 5, x + imageWidth - 5, y + imageHeight - 5, 0x60FFF7E5);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF6E5C3A);
        g.renderOutline(x + 6, y + 6, imageWidth - 12, imageHeight - 12, GOLD);
        // The title sits above a small constellation, like a page from the Astral Tome.
        g.fillGradient(x + 12, y + 12, x + 288, y + 65, 0xFF131E34, 0xFF26344D);
        g.renderOutline(x + 12, y + 12, 276, 53, 0xFF9E8756);
        line(g, x + 166, y + 50, x + 194, y + 39);
        line(g, x + 194, y + 39, x + 227, y + 52);
        line(g, x + 227, y + 52, x + 260, y + 34);
        star(g, x + 166, y + 50, 2, BLUE);
        star(g, x + 194, y + 39, 3, GOLD);
        star(g, x + 227, y + 52, 2, BLUE);
        star(g, x + 260, y + 34, 3, BLUE);
        for (int i = 0; i < 18; i++) {
            int sx = x + 20 + (i * 47 % 260), sy = y + 17 + (i * 19 % 42);
            g.fill(sx, sy, sx + 1, sy + 1, 0x6687B6D1);
        }
        g.renderItem(ModContent.ME_RESONATING_WAND.toStack(), x + 21, y + 23);
        g.drawString(font, title, x + 44, y + 22, 0xFFF0E5C6, false);
        g.drawString(font, tr(menu.isLinked() ? "linked" : "unlinked"), x + 23, y + 47,
                menu.isLinked() ? BLUE : 0xFFD5C5AA, false);
        g.hLine(x + 20, x + 279, y + 207, 0xFFB09A6C);
        g.drawCenteredString(font, tr("saved"), x + imageWidth / 2, y + 215, 0xFF625D50);
        for (int dx : new int[]{7, imageWidth - 8}) for (int dy : new int[]{7, imageHeight - 8})
            star(g, x + dx, y + dy, 3, GOLD);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy += 16) for (int dx = 0; dx < width; dx += 16)
            g.blit(texture, x + dx, y + dy, 0, 0, Math.min(16, width - dx), Math.min(16, height - dy), 16, 16);
    }

    private static void star(GuiGraphics g, int x, int y, int radius, int color) {
        g.hLine(x - radius, x + radius, y, color);
        g.vLine(x, y - radius, y + radius, color);
        g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, 0x665B9AAD);
        }
    }

    private final class SealButton extends Button {
        private final int option;
        private final String key;

        SealButton(int x, int y, int option, String key) {
            super(x, y, 270, 39, tr(key), button -> {
                if (minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, option);
            }, DEFAULT_NARRATION);
            this.option = option;
            this.key = key;
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean enabled = menu.enabled(option), hover = isHoveredOrFocused();
            setMessage(tr("toggle", tr(key), tr(enabled ? "on" : "off")));
            int x = getX(), y = getY();
            // Match the Lumen Array's layered wood/marble button frame, keeping gold edges on hover.
            int border = hover ? GOLD : GOLD_SHADE;
            g.fill(x + 1, y + 1, x + width - 1, y + height, 0xFF98907C);
            g.fill(x, y + 1, x + width, y + height - 2, border);
            g.fill(x + 1, y, x + width - 1, y + height - 1, border);
            tile(g, WOOD, x + 1, y + 1, width - 2, height - 3);
            tile(g, MARBLE, x + 3, y + 2, width - 6, height - 5);
            g.fill(x + 3, y + 2, x + width - 3, y + height - 3, hover ? 0x605FCBDC : 0x58FFF9E8);
            g.fill(x + 2, y + 1, x + width - 2, y + 2, 0xFFFFF2BF);
            g.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, GOLD);
            g.drawString(font, tr(key), x + 13, y + 7, INK, false);
            g.drawString(font, tr(key + "_detail"), x + 13, y + 23, 0xFF6B6C65, false);
            int sealX = x + width - 54;
            g.fill(sealX, y + 6, x + width - 7, y + 20, enabled ? 0xFF203D52 : 0xFF77796F);
            g.renderOutline(sealX, y + 6, 47, 14, enabled ? 0xFF71C2CF : 0xFFB7AA89);
            star(g, sealX + 8, y + 12, 2, enabled ? BLUE : 0xFFC9C4B0);
            g.drawString(font, tr(enabled ? "on" : "off"), sealX + 16, y + 9, enabled ? BLUE : 0xFFE4E0D0, false);
        }
    }
}
