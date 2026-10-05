package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;

import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.wand.MEResonatingWandItem;
import com.appliedastralsorcery.wand.MEResonatingWandMenu;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class MEResonatingWandScreen extends AbstractContainerScreen<MEResonatingWandMenu> {
    private static final int INK = 0xFF3B3933, MUTED = 0xFF696356, GOLD = 0xFFD4BE76, GOLD_SHADE = 0xFF806630,
            AQUA = 0xFF228CC1, AQUA_LIGHT = 0xFF7AD6E8, EDGE = 0xFF9D9073, STARLIGHT = 0xFF9FE9F2;
    private static final int HEADER_X = 14, HEADER_Y = 13, CONTENT_WIDTH = 292, HEADER_HEIGHT = 50;
    private static final int ROW_Y = 69, ROW_HEIGHT = 40, ROW_GAP = 4;
    private static final ResourceLocation MARBLE = texture("block/marble_raw"), WOOD = texture("block/infused_wood"),
            SKY = texture("screen/tome/background_constellation");
    private static final int[][] STARS = {{0, 38}, {20, 31}, {40, 25}, {61, 18}, {52, 8}, {79, 12}, {75, 32}};
    private static final int[][] LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {3, 5}, {3, 6}};
    private static final int FOCUS = 3;

    public MEResonatingWandScreen(MEResonatingWandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 232;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.appliedas.wand." + key, args);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/" + path + ".png");
    }

    @Override protected void init() {
        super.init();
        addToggle(0, MEResonatingWandItem.USE_ME_ITEMS, "items", AEItems.ITEM_CELL_1K.stack());
        addToggle(1, MEResonatingWandItem.BUILD_FLUIDS, "fluids", FluidsAS.LIQUID_STARLIGHT.getBucket().toStack());
        addToggle(2, MEResonatingWandItem.REPLACE_BLOCKS, "replace", ItemsAS.EXCHANGE_WAND.toStack());
    }

    private void addToggle(int row, int option, String key, ItemStack icon) {
        int y = topPos + ROW_Y + row * (ROW_HEIGHT + ROW_GAP);
        addRenderableWidget(new SealButton(leftPos + HEADER_X, y, option, key, icon));
    }

    @ParametersAreNonnullByDefault
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (!menu.isLinked() && isHovering(HEADER_X + 35, HEADER_Y + 27, font.width(tr("unlinked")) + 13, 11, mx, my))
            g.renderTooltip(font, font.split(Component.translatable("tooltip.appliedas.wand.bind"), 200), mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xFF3C3020);
        tile(g, WOOD, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFAA884A);
        plate(g, x + 4, y + 4, w - 8, h - 8);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0x40FFF8E5);
        g.renderOutline(x + 7, y + 7, w - 14, h - 14, GOLD);
        for (int dx : new int[]{7, w - 8}) for (int dy : new int[]{7, h - 8})
            star(g, x + dx, y + dy, 3, GOLD);
        renderHeader(g, x + HEADER_X, y + HEADER_Y);
        Component saved = tr("saved");
        int textWidth = font.width(saved), textX = x + (w - textWidth) / 2, textY = y + 207;
        g.drawString(font, saved, textX, textY, MUTED, false);
        g.fill(x + 20, textY + 3, textX - 9, textY + 4, GOLD);
        g.fill(textX + textWidth + 8, textY + 3, x + w - 20, textY + 4, GOLD);
        star(g, textX - 6, textY + 3, 2, GOLD_SHADE);
        star(g, textX + textWidth + 4, textY + 3, 2, GOLD_SHADE);
    }

    private void renderHeader(GuiGraphics g, int x, int y) {
        boolean linked = menu.isLinked();
        g.fill(x, y, x + CONTENT_WIDTH, y + HEADER_HEIGHT, 0xFF2B2418);
        g.renderOutline(x + 1, y + 1, CONTENT_WIDTH - 2, HEADER_HEIGHT - 2, GOLD);
        g.blit(SKY, x + 2, y + 2, 113, 35, CONTENT_WIDTH - 4, HEADER_HEIGHT - 4, 450, 300);
        for (int dx : new int[]{1, CONTENT_WIDTH - 2}) for (int dy : new int[]{1, HEADER_HEIGHT - 2})
            star(g, x + dx, y + dy, 2, GOLD);
        constellation(g, x + CONTENT_WIDTH - 94, y, linked);
        socket(g, x + 12, y + 17, true, linked ? 0xFF2C5866 : 0xFF33363D);
        g.renderItem(MEResonatingWandClient.icon(linked), x + 12, y + 17);
        g.drawString(font, title, x + 36, y + 13, 0xFFF0E5C6, false);
        lamp(g, x + 37, y + 30, linked ? AQUA_LIGHT : 0xFF6D6A63);
        g.drawString(font, tr(linked ? "linked" : "unlinked"), x + 46, y + 28, linked ? STARLIGHT : 0xFFCDB991, false);
    }

    private static void constellation(GuiGraphics g, int x, int y, boolean linked) {
        for (int[] link : LINKS) {
            int[] from = STARS[link[0]], to = STARS[link[1]];
            line(g, x + from[0], y + from[1], x + to[0], y + to[1], linked ? 0x7098D8EA : 0x30A6B0C4);
        }
        float time = Util.getMillis() / 600F;
        for (int i = 0; i < STARS.length; i++) {
            int sx = x + STARS[i][0], sy = y + STARS[i][1], radius = i == FOCUS ? 3 : i % 2 == 0 ? 2 : 1;
            int color = linked ? i == FOCUS ? GOLD : STARLIGHT : i == FOCUS ? 0xFF8F846C : 0xFF647088;
            if (linked && radius > 1) {
                int halo = (int) (0x30 + 0x20 * Mth.sin(time + i * 1.7F));
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, halo << 24 | color & 0xFFFFFF);
            }
            star(g, sx, sy, radius, color, linked ? 0xFFFFFFFF : 0xFFA7AFBE);
        }
    }

    @ParametersAreNonnullByDefault
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private void fitted(GuiGraphics g, Component text, int x, int y, int width, int color) {
        String label = text.getString();
        if (font.width(label) > width) label = font.plainSubstrByWidth(label, width - font.width("…")) + "…";
        g.drawString(font, label, x, y, color, false);
    }

    private static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy += 16) for (int dx = 0; dx < width; dx += 16)
            g.blit(texture, x + dx, y + dy, 0, 0, Math.min(16, width - dx), Math.min(16, height - dy), 16, 16);
    }

    private static void plate(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF6D695E);
        tile(g, MARBLE, x + 1, y + 1, width - 2, height - 2);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFF8F8F5);
        g.fill(x + 1, y + 2, x + 2, y + height - 1, 0xFFE6E8E9);
        g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF999589);
    }

    private static void socket(GuiGraphics g, int x, int y, boolean gilt, int fill) {
        g.fill(x - 1, y - 1, x + 17, y + 17, gilt ? GOLD : 0xFFF6F1E4);
        g.fill(x - 1, y - 1, x + 17, y, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x - 1, y, x, y + 16, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x, y, x + 16, y + 16, fill);
    }

    private static void lamp(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y - 1, x + 3, y + 5, GOLD_SHADE);
        g.fill(x - 1, y + 1, x + 5, y + 3, GOLD_SHADE);
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x, y, x + 3, y + 1, 0xBBE7FFFF);
        g.fill(x + 3, y + 1, x + 4, y + 4, 0x550D3844);
    }

    private static void star(GuiGraphics g, int x, int y, int radius, int color) {
        star(g, x, y, radius, color, 0xFFFFFFFF);
    }

    private static void star(GuiGraphics g, int x, int y, int radius, int color, int core) {
        g.fill(x - radius, y, x + radius + 1, y + 1, color);
        g.fill(x, y - radius, x + 1, y + radius + 1, color);
        g.fill(x, y, x + 1, y + 1, core);
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round((x2 - x1) * i / (float) steps), y = y1 + Math.round((y2 - y1) * i / (float) steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private final class SealButton extends Button {
        private final int option;
        private final String key;
        private final ItemStack icon;

        SealButton(int x, int y, int option, String key, ItemStack icon) {
            super(x, y, CONTENT_WIDTH, ROW_HEIGHT, tr(key), button -> {
                if (minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, option);
            }, DEFAULT_NARRATION);
            this.option = option;
            this.key = key;
            this.icon = icon;
            setTooltip(Tooltip.create(tr(key + "_hint")));
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean on = menu.enabled(option), hover = isHoveredOrFocused();
            setMessage(tr("toggle", tr(key), tr(on ? "on" : "off")));
            int x = getX(), y = getY(), w = width, h = height;
            int border = hover ? AQUA : GOLD_SHADE;
            g.fill(x + 1, y + 1, x + w - 1, y + h, 0xFF98907C);
            g.fill(x, y + 1, x + w, y + h - 2, border);
            g.fill(x + 1, y, x + w - 1, y + h - 1, border);
            tile(g, WOOD, x + 1, y + 1, w - 2, h - 3);
            tile(g, MARBLE, x + 3, y + 2, w - 6, h - 5);
            g.fill(x + 3, y + 2, x + w - 3, y + h - 3, hover ? 0x445FCBDC : 0x58FFF9E8);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFF2BF);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GOLD);
            socket(g, x + 10, y + 11, on, on ? 0xFF628A88 : 0xFF97978F);
            g.renderItem(icon, x + 10, y + 11);
            if (!on) g.fill(x + 10, y + 11, x + 26, y + 27, 200, 0x7097978F);
            int sealWidth = Math.max(font.width(tr("on")), font.width(tr("off"))) + 22;
            int sealX = x + w - sealWidth - 10, textWidth = sealX - x - 40;
            fitted(g, tr(key), x + 34, y + 10, textWidth, INK);
            fitted(g, tr(key + "_detail"), x + 34, y + 22, textWidth, MUTED);
            seal(g, sealX, y + 12, sealWidth, on);
        }

        private void seal(GuiGraphics g, int x, int y, int width, boolean on) {
            g.fill(x, y, x + width, y + 16, on ? 0xFF1D6F91 : 0xFF8E8676);
            g.fill(x + 1, y + 1, x + width - 1, y + 15, on ? 0xFFD3ECEA : 0xFFE6E1D3);
            g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xB0FFFFFF);
            g.fill(x + 1, y + 13, x + width - 1, y + 15, on ? AQUA : EDGE);
            lamp(g, x + 5, y + 5, on ? AQUA : 0xFF9C9685);
            Component state = tr(on ? "on" : "off");
            int left = x + 13, right = x + width - 3;
            g.drawString(font, state, left + (right - left - font.width(state)) / 2, y + 4, on ? 0xFF14506A : MUTED, false);
        }
    }
}
