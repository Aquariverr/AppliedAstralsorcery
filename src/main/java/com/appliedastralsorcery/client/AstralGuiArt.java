package com.appliedastralsorcery.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The marble, gilt and night-sky pieces the mod's consoles are drawn from, after the Auto Chisel's screen. */
final class AstralGuiArt {
    static final ResourceLocation MARBLE = texture("block/marble_raw"), WOOD = texture("block/infused_wood"),
            SKY = texture("screen/tome/background_constellation");
    static final int INK = 0xFF3B3933, MUTED = 0xFF696356, GOLD = 0xFFD4BE76, GOLD_SHADE = 0xFF806630,
            AQUA = 0xFF228CC1, AQUA_LIGHT = 0xFF7AD6E8, STARLIGHT = 0xFF9FE9F2, FADED = 0xFFB9B3A5, CREAM = 0xFFF0E5C6,
            PARCHMENT = 0xFFCDB991, WARNING = 0xFFE08A3C;

    private AstralGuiArt() {}

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/" + path + ".png");
    }

    /** Infused wood around a marble plate, gilt along the inside with stars at its corners. */
    static void frame(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x66000000);
        g.fill(x, y, x + width, y + height, 0xFF3C3020);
        tile(g, WOOD, x + 1, y + 1, width - 2, height - 2);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFAA884A);
        plate(g, x + 3, y + 3, width - 6, height - 6);
        g.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x40FFF8E5);
        g.renderOutline(x + 5, y + 5, width - 10, height - 10, GOLD);
        for (int dx : new int[]{5, width - 6}) for (int dy : new int[]{5, height - 6})
            star(g, x + dx, y + dy, 3, GOLD);
    }

    /** A window onto the Astral Tome's night sky, in a gilt bezel with stars at its corners. */
    static void skyBand(GuiGraphics g, int x, int y, int width, int height, int u, int v) {
        g.fill(x, y, x + width, y + height, 0xFF2B2418);
        g.renderOutline(x + 1, y + 1, width - 2, height - 2, GOLD);
        g.blit(SKY, x + 2, y + 2, u, v, width - 4, height - 4, 450, 300);
        for (int dx : new int[]{1, width - 2}) for (int dy : new int[]{1, height - 2})
            star(g, x + dx, y + dy, 2, GOLD);
    }

    static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        tile(g, texture, x, y, width, height, x, y);
    }

    /** Tiles a 16x16 texture over an area, continuing a tiling that starts at (originX, originY). */
    static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height,
            int originX, int originY) {
        for (int ty = y; ty < y + height; ) {
            int v = Math.floorMod(ty - originY, 16), tileHeight = Math.min(16 - v, y + height - ty);
            for (int tx = x; tx < x + width; ) {
                int u = Math.floorMod(tx - originX, 16), tileWidth = Math.min(16 - u, x + width - tx);
                g.blit(texture, tx, ty, u, v, tileWidth, tileHeight, 16, 16);
                tx += tileWidth;
            }
            ty += tileHeight;
        }
    }

    static void plate(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF6D695E);
        tile(g, MARBLE, x + 1, y + 1, width - 2, height - 2);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFF8F8F5);
        g.fill(x + 1, y + 2, x + 2, y + height - 1, 0xFFE6E8E9);
        g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF999589);
    }

    static void inset(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF66533A);
        tile(g, WOOD, x + 1, y + 1, width - 2, height - 2);
        tile(g, MARBLE, x + 3, y + 2, width - 6, height - 4);
        g.fill(x + 3, y + 2, x + width - 3, y + height - 2, 0x50FFF9E9);
        g.fill(x + 2, y + 1, x + width - 2, y + 2, GOLD);
        g.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, GOLD_SHADE);
    }

    /** A recessed stone slot; gilt ones hold what a machine gives back. */
    static void slot(GuiGraphics g, int x, int y, boolean gilt) {
        g.fill(x - 1, y - 1, x + 17, y + 17, gilt ? GOLD : 0xFFF6F1E4);
        g.fill(x - 1, y - 1, x + 17, y, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x - 1, y, x, y + 16, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x, y, x + 16, y + 16, 0xFF97978F);
    }

    /** A recessed gilt 18x18 socket around a 16x16 icon at (x, y). */
    static void socket(GuiGraphics g, int x, int y, int fill) {
        g.fill(x - 1, y - 1, x + 17, y + 17, GOLD);
        g.fill(x - 1, y - 1, x + 17, y, GOLD_SHADE);
        g.fill(x - 1, y, x, y + 16, GOLD_SHADE);
        g.fill(x, y, x + 16, y + 16, fill);
    }

    static void lamp(GuiGraphics g, int x, int y, int color) {
        // A small faceted aquamarine set in gold, dimmed when the feature is off.
        g.fill(x + 1, y - 1, x + 3, y + 5, GOLD_SHADE);
        g.fill(x - 1, y + 1, x + 5, y + 3, GOLD_SHADE);
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x, y, x + 3, y + 1, 0xBBE7FFFF);
        g.fill(x + 3, y + 1, x + 4, y + 4, 0x550D3844);
    }

    /** A right-pointing arrowhead, four pixels long and seven tall, centred on row y. */
    static void arrowhead(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 4; i++) g.fill(x + i, y - 3 + i, x + i + 1, y + 4 - i, color);
    }

    static void star(GuiGraphics g, int x, int y, int radius, int color) {
        star(g, x, y, radius, color, 0xFFFFFFFF);
    }

    static void star(GuiGraphics g, int x, int y, int radius, int color, int core) {
        g.fill(x - radius, y, x + radius + 1, y + 1, color);
        g.fill(x, y - radius, x + 1, y + radius + 1, color);
        g.fill(x, y, x + 1, y + 1, core);
    }

    static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + Math.round((x2 - x1) * i / (float) steps), y = y1 + Math.round((y2 - y1) * i / (float) steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
