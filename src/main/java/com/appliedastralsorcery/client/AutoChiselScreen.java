package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;

import java.util.EnumMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.core.definitions.AEItems;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chisel.AutoChiselBlockEntity;
import com.appliedastralsorcery.chisel.AutoChiselMenu;
import hellfirepvp.astralsorcery.client.ClientProxy;
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A marble workbench with a single processing arrow and a separate page for mode and face settings.
 */
public final class AutoChiselScreen extends AbstractContainerScreen<AutoChiselMenu> {
    private static final int INK = 0xFF3B3933, MUTED = 0xFF696356, GOLD = 0xFFD4BE76, GOLD_SHADE = 0xFF806630,
            AQUA = 0xFF228CC1, AQUA_LIGHT = 0xFF7AD6E8, STARLIGHT = 0xFF9FE9F2, FADED = 0xFFB9B3A5;
    // Evorsio, dark to light, as painted on the machine.
    private static final int EVORSIO = 0xFF9A0E12, EVORSIO_BRIGHT = 0xFFD8302C, EVORSIO_LIGHT = 0xFFFF7A5C,
            EVORSIO_PALE = 0xFFFFD0B8;
    private static final int MARGIN = 12, CONTENT_WIDTH = 232, HEADER_Y = 12, HEADER_HEIGHT = 36;
    private static final int BENCH_Y = 52, BENCH_HEIGHT = 68, LOWER_Y = 124, LOWER_HEIGHT = 84, FOOTER_Y = 213;
    private static final int LUMEN_X = 188, LUMEN_WIDTH = 56, NET_Y = 100, NET_HEIGHT = 108;
    private static final int TAB_Y = 12, TAB_WIDTH = 24, TAB_HEIGHT = 26;
    private static final ResourceLocation MARBLE = texture("block/marble_raw"), WOOD = texture("block/infused_wood"),
            SKY = texture("screen/tome/background_constellation");
    // Evorsio as charted in the Astral Tome; lit while the machine holds lumen for another operation.
    private static final int[][] STARS = {{23, 15}, {14, 22}, {21, 26}, {18, 10}, {8, 0}, {11, 9}, {0, 2}};
    private static final int[][] LINKS = {{0, 1}, {0, 2}, {0, 3}, {3, 4}, {3, 5}, {5, 6}};
    private static final int FOCUS = 0;
    private final EnumMap<Direction, FaceButton> sides = new EnumMap<>(Direction.class);
    private final ItemStack chisel = ModContent.AUTO_CHISEL_ITEM.toStack(), wrench = AEItems.CERTUS_QUARTZ_WRENCH.stack();
    private Button tabButton, modeButton, roundRobinButton, autoInputButton, autoOutputButton;

    public AutoChiselScreen(AutoChiselMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 234;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.appliedas.chisel." + key, args);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/" + path + ".png");
    }

    @Override protected void init() {
        super.init();
        sides.clear();
        tabButton = addRenderableWidget(new TabButton(leftPos + imageWidth, topPos + TAB_Y));
        modeButton = setting(MARGIN, 52, CONTENT_WIDTH, AutoChiselMenu.MODE_BUTTON, null);
        roundRobinButton = setting(MARGIN, 74, CONTENT_WIDTH, AutoChiselMenu.ROUND_ROBIN_BUTTON, menu::isRoundRobin);
        autoInputButton = setting(MARGIN, 74, 114, AutoChiselMenu.AUTO_INPUT_BUTTON, menu::isAutoInput);
        autoOutputButton = setting(130, 74, 114, AutoChiselMenu.AUTO_OUTPUT_BUTTON, menu::isAutoOutput);
        modeButton.setTooltip(Tooltip.create(tr("mode.hint")));
        roundRobinButton.setTooltip(Tooltip.create(tr("round_robin.hint")));
        autoInputButton.setTooltip(Tooltip.create(tr("auto_input.hint")));
        autoOutputButton.setTooltip(Tooltip.create(tr("auto_output.hint")));
        // Compact face layout: S sits diagonally below-right of N. Directions are world-fixed.
        face(Direction.UP, 1, 0);
        face(Direction.WEST, 0, 1);
        face(Direction.NORTH, 1, 1);
        face(Direction.EAST, 2, 1);
        face(Direction.SOUTH, 2, 2);
        face(Direction.DOWN, 1, 2);
        updateSettings();
    }

    /** The configuration tab beside the frame, for JEI to keep clear of. */
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(leftPos + imageWidth, topPos + TAB_Y, TAB_WIDTH + 3, TAB_HEIGHT + 4));
    }

    private void toggleConfiguration() {
        menu.setConfigurationOpen(!menu.isConfigurationOpen());
        setFocused(null);
        updateSettings();
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private Button setting(int x, int y, int width, int id, BooleanSupplier lamp) {
        return addRenderableWidget(new SettingButton(leftPos + x, topPos + y, width, id, lamp));
    }

    private void face(Direction side, int column, int row) {
        sides.put(side, addRenderableWidget(new FaceButton(side, leftPos + 34 + column * 26, topPos + 125 + row * 26)));
    }

    private void updateSettings() {
        boolean open = menu.isConfigurationOpen(), droppedItems = menu.isDroppedItemMode();
        Component tabLabel = tr(open ? "inventory" : "configuration");
        if (!tabLabel.equals(tabButton.getMessage())) {
            tabButton.setMessage(tabLabel);
            tabButton.setTooltip(Tooltip.create(tabLabel));
        }
        modeButton.visible = modeButton.active = open;
        modeButton.setMessage(tr("mode." + (menu.isDroppedItemMode() ? "drops" : "inventory")));
        roundRobinButton.setMessage(tr("round_robin." + (menu.isRoundRobin() ? "on" : "off")));
        autoInputButton.setMessage(tr("auto_input." + (menu.isAutoInput() ? "on" : "off")));
        autoOutputButton.setMessage(tr("auto_output." + (menu.isAutoOutput() ? "on" : "off")));
        roundRobinButton.visible = roundRobinButton.active = open && droppedItems;
        autoInputButton.visible = autoInputButton.active = open && !droppedItems;
        autoOutputButton.visible = autoOutputButton.active = open && !droppedItems;
        sides.values().forEach(face -> face.update(open));
    }

    @ParametersAreNonnullByDefault
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        updateSettings();
        super.render(g, mx, my, partial);
        if (!menu.isConfigurationOpen()) {
            renderTooltip(g, mx, my);
            if (isHovering(LUMEN_X, LOWER_Y, LUMEN_WIDTH, LOWER_HEIGHT, mx, my)) {
                g.renderTooltip(font, tr("lumen", menu.getLumenAmount(), AutoChiselBlockEntity.LUMEN_CAPACITY), mx, my);
            } else if (isHovering(52, 64, 114, 36, mx, my)) {
                g.renderTooltip(font, tr("processing_cost", menu.getDuration() / 20.0,
                        AutoChiselBlockEntity.getLumenCost()), mx, my);
            }
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos, w = imageWidth, h = imageHeight;
        boolean open = menu.isConfigurationOpen();
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x66000000);
        g.fill(x, y, x + w, y + h, 0xFF3C3020);
        tile(g, WOOD, x + 1, y + 1, w - 2, h - 2);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFAA884A);
        plate(g, x + 4, y + 4, w - 8, h - 8);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0x40FFF8E5);
        g.renderOutline(x + 7, y + 7, w - 14, h - 14, GOLD);
        for (int dx : new int[]{7, w - 8}) for (int dy : new int[]{7, h - 8})
            star(g, x + dx, y + dy, 3, GOLD);
        renderHeader(g, x + MARGIN, y + HEADER_Y, open);
        if (open) {
            renderNet(g, x, y);
        } else {
            renderBench(g, x, y);
            inset(g, x + MARGIN, y + LOWER_Y, LUMEN_X - MARGIN - 4, LOWER_HEIGHT);
            g.fill(x + 20, y + 183, x + 176, y + 184, GOLD_SHADE);
            g.fill(x + 20, y + 184, x + 176, y + 185, GOLD);
            renderLumen(g, x + LUMEN_X, y + LOWER_Y);
            for (int i = 0; i < menu.slots.size(); i++) {
                var slot = menu.slots.get(i);
                slot(g, x + slot.x, y + slot.y, i == 0);
            }
        }
        if (open) footer(g, tr("face_controls"));
    }

    private void renderHeader(GuiGraphics g, int x, int y, boolean open) {
        var status = menu.getStatus();
        boolean working = status == AutoChiselBlockEntity.Status.WORKING, idle = status == AutoChiselBlockEntity.Status.IDLE;
        // A window onto the Astral Tome's night sky, set in a gilt bezel.
        g.fill(x, y, x + CONTENT_WIDTH, y + HEADER_HEIGHT, 0xFF2B2418);
        g.renderOutline(x + 1, y + 1, CONTENT_WIDTH - 2, HEADER_HEIGHT - 2, GOLD);
        g.blit(SKY, x + 2, y + 2, 113, 35, CONTENT_WIDTH - 4, HEADER_HEIGHT - 4, 450, 300);
        for (int dx : new int[]{1, CONTENT_WIDTH - 2}) for (int dy : new int[]{1, HEADER_HEIGHT - 2})
            star(g, x + dx, y + dy, 2, GOLD);
        constellation(g, x + CONTENT_WIDTH - 34, y + 4,
                working || menu.getLumenAmount() >= AutoChiselBlockEntity.getLumenCost());
        // The chisel rests in a gilt socket that glows with Evorsio while it works.
        socket(g, x + 10, y + 10, working ? 0xFF5A1E1C : 0xFF33363D);
        g.renderItem(chisel, x + 10, y + 10);
        fitted(g, open ? tr("configuration") : title, x + 34, y + 7, 158, 0xFFF0E5C6);
        lamp(g, x + 35, y + 22, working ? AQUA_LIGHT : idle ? 0xFF6D6A63 : 0xFFE08A3C);
        fitted(g, status.label(), x + 44, y + 20, 150, working ? STARLIGHT : idle ? 0xFFCDB991 : 0xFFF2B872);
    }

    private static void constellation(GuiGraphics g, int x, int y, boolean lit) {
        for (int[] link : LINKS) {
            int[] from = STARS[link[0]], to = STARS[link[1]];
            line(g, x + from[0], y + from[1], x + to[0], y + to[1], lit ? 0x70FF7A5C : 0x30A6B0C4);
        }
        float time = Util.getMillis() / 600F;
        for (int i = 0; i < STARS.length; i++) {
            int sx = x + STARS[i][0], sy = y + STARS[i][1], radius = i == FOCUS ? 3 : i % 2 == 0 ? 2 : 1;
            int color = lit ? i == FOCUS ? GOLD : EVORSIO_LIGHT : i == FOCUS ? 0xFF8F846C : 0xFF647088;
            if (lit && radius > 1) {
                int halo = (int) (0x30 + 0x20 * Mth.sin(time + i * 1.7F));
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, halo << 24 | color & 0xFFFFFF);
            }
            star(g, sx, sy, radius, color, !lit ? 0xFFA7AFBE : i == FOCUS ? 0xFFFFFFFF : EVORSIO_PALE);
        }
    }

    private void renderBench(GuiGraphics g, int x, int y) {
        inset(g, x + MARGIN, y + BENCH_Y, CONTENT_WIDTH, BENCH_HEIGHT);
        var input = menu.slots.getFirst();
        Component label = tr("input");
        g.drawString(font, label, x + input.x + 8 - font.width(label) / 2, y + input.y - 12, MUTED, false);
        float progress = menu.getDuration() <= 0 ? 0F
                : Math.clamp(menu.getProgress() / (float) menu.getDuration(), 0F, 1F);
        String percentage = Math.round(progress * 100) + "%";
        g.drawString(font, percentage, x + 110 - font.width(percentage) / 2, y + 68, MUTED, false);
        channel(g, x + 52, x + 168, y + 84, progress);
    }

    /** A groove ending in an arrowhead that Evorsio fills as the work advances, with a spark at the head of the flow. */
    private static void channel(GuiGraphics g, int x1, int x2, int y, float fill) {
        int end = x2 - 4, length = Math.round((end - x1 - 2) * fill), head = x1 + 1 + length;
        g.fill(x1, y, end, y + 5, 0xFF777568);
        g.fill(x1 + 1, y + 1, end - 1, y + 4, 0xFFACA99A);
        g.fill(x1 + 1, y + 4, end - 1, y + 5, 0xFFF9F3DF);
        if (length > 0) {
            g.fill(x1 + 1, y + 1, head, y + 4, EVORSIO_BRIGHT);
            g.fill(x1 + 1, y + 1, head, y + 2, EVORSIO_LIGHT);
            g.fill(x1 + 1, y + 3, head, y + 4, EVORSIO);
        }
        arrowhead(g, end, y + 2, fill >= 1F ? EVORSIO_BRIGHT : GOLD_SHADE);
        if (length > 0 && fill < 1F) star(g, head, y + 2, 2, EVORSIO_LIGHT, EVORSIO_PALE);
    }

    private void renderLumen(GuiGraphics g, int x, int y) {
        inset(g, x, y, LUMEN_WIDTH, LOWER_HEIGHT);
        // The rune of Evorsio on parchment, where its dark red reads clearly.
        var evorsio = LumenAS.EVORSIO.get();
        int color = 0xFF000000 | evorsio.getColor(ClientProxy.getClientTick()).getColor();
        g.fill(x + 19, y + 5, x + 37, y + 23, 0xFFEDE3CF);
        g.renderOutline(x + 19, y + 5, 18, 18, GOLD);
        var sprite = Minecraft.getInstance().getModelManager().getAtlas(TexturesAS.ATLAS_LUMEN)
                .getSprite(evorsio.getRegistryKey().orElseThrow().location());
        g.blit(x + 20, y + 6, 0, 16, 16, sprite, ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                (color & 255) / 255F, 1F);
        // A vial in gilt caps, filling from the bottom.
        int left = x + 23, right = left + 10, top = y + 28, bottom = y + 66, height = bottom - top;
        g.fill(left, top, right, bottom, 0xFF777568);
        g.fill(left + 1, top, right - 1, bottom, 0xFFACA99A);
        g.fill(right - 1, top, right, bottom, 0xFFF9F3DF);
        int amount = Math.clamp(menu.getLumenAmount(), 0, AutoChiselBlockEntity.LUMEN_CAPACITY);
        int level = height * amount / AutoChiselBlockEntity.LUMEN_CAPACITY, surface = bottom - level;
        if (level > 0) {
            g.fill(left + 1, surface, right - 1, bottom, EVORSIO_BRIGHT);
            g.fill(left + 5, surface, right - 1, bottom, EVORSIO);
            g.fill(left + 1, surface, right - 1, surface + 1, EVORSIO_LIGHT);
            if (menu.getStatus() == AutoChiselBlockEntity.Status.WORKING && level > 6) {
                long time = Util.getMillis() / 80L;
                for (int i = 0; i < 2; i++) {
                    int bubble = bottom - 2 - (int) ((time + i * 13L) % (level - 4));
                    g.fill(left + 3 + i * 3, bubble, left + 4 + i * 3, bubble + 1, 0xB0FFD0B8);
                }
            }
        }
        g.fill(left + 2, top + 2, left + 3, bottom - 2, 0x50FFFFFF);
        g.fill(left - 1, top - 2, right + 1, top, GOLD);
        g.fill(left - 1, top - 1, right + 1, top, GOLD_SHADE);
        g.fill(left - 1, bottom, right + 1, bottom + 2, GOLD);
        g.fill(left - 1, bottom + 1, right + 1, bottom + 2, GOLD_SHADE);
        String stored = amount + " Lm";
        g.drawString(font, stored, x + 28 - font.width(stored) / 2, y + 71, INK, false);
    }

    private void renderNet(GuiGraphics g, int x, int y) {
        inset(g, x + MARGIN, y + NET_Y, CONTENT_WIDTH, NET_HEIGHT);
        fitted(g, tr("faces"), x + 24, y + NET_Y + 9, CONTENT_WIDTH - 24, INK);
        // A legend of the face colours beside the net.
        int legendY = y + 140;
        for (var mode : AutoChiselBlockEntity.SideMode.values()) {
            faceTile(g, x + 124, legendY, 10, faceColor(mode), 0xFF4A4034);
            fitted(g, mode.label(), x + 138, legendY + 1, 98, INK);
            legendY += 16;
        }
    }

    /** Gilt rules ending in small stars frame the footer, like the Astral Tome's underline. */
    private void footer(GuiGraphics g, Component text) {
        int x = leftPos, w = imageWidth, textWidth = Math.min(font.width(text), w - 80);
        int textX = x + (w - textWidth) / 2, textY = topPos + FOOTER_Y;
        fitted(g, text, textX, textY, textWidth, MUTED);
        g.fill(x + 20, textY + 3, textX - 9, textY + 4, GOLD);
        g.fill(textX + textWidth + 8, textY + 3, x + w - 20, textY + 4, GOLD);
        star(g, textX - 6, textY + 3, 2, GOLD_SHADE);
        star(g, textX + textWidth + 4, textY + 3, 2, GOLD_SHADE);
    }

    @ParametersAreNonnullByDefault
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {}

    private static int faceColor(AutoChiselBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> 0xFF508AB9;
            case OUTPUT -> 0xFFC88642;
            case INPUT_OUTPUT -> 0xFF9466AE;
            case NONE -> 0xFFFFFFFF;
        };
    }

    /** A bevelled tile in the colour of a face mode, lit from the top left. */
    private static void faceTile(GuiGraphics g, int x, int y, int size, int color, int frame) {
        g.fill(x, y, x + size, y + size, frame);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, FastColor.ARGB32.lerp(0.4F, color, 0xFFFFFFFF));
        g.fill(x + 2, y + 2, x + size - 1, y + size - 1, FastColor.ARGB32.lerp(0.35F, color, 0xFF000000));
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, color);
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int width, int color) {
        if (width <= 0) return;
        String label = text.getString();
        if (font.width(label) > width) {
            int ellipsisWidth = font.width("…");
            label = width < ellipsisWidth ? "" : font.plainSubstrByWidth(label, width - ellipsisWidth) + "…";
        }
        g.drawString(font, label, x, y, color, false);
    }

    private static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height) {
        tile(g, texture, x, y, width, height, x, y);
    }

    /** Tiles a 16x16 texture over an area, continuing a tiling that starts at (originX, originY). */
    private static void tile(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height,
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

    private static void plate(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF6D695E);
        tile(g, MARBLE, x + 1, y + 1, width - 2, height - 2);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFF8F8F5);
        g.fill(x + 1, y + 2, x + 2, y + height - 1, 0xFFE6E8E9);
        g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF999589);
    }

    private static void inset(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF66533A);
        tile(g, WOOD, x + 1, y + 1, width - 2, height - 2);
        tile(g, MARBLE, x + 3, y + 2, width - 6, height - 4);
        g.fill(x + 3, y + 2, x + width - 3, y + height - 2, 0x50FFF9E9);
        g.fill(x + 2, y + 1, x + width - 2, y + 2, GOLD);
        g.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, GOLD_SHADE);
    }

    /** A recessed stone slot; the input's is gilt. */
    private static void slot(GuiGraphics g, int x, int y, boolean gilt) {
        g.fill(x - 1, y - 1, x + 17, y + 17, gilt ? GOLD : 0xFFF6F1E4);
        g.fill(x - 1, y - 1, x + 17, y, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x - 1, y, x, y + 16, gilt ? GOLD_SHADE : 0xFF625F54);
        g.fill(x, y, x + 16, y + 16, 0xFF97978F);
    }

    /** A recessed gilt 18x18 socket around a 16x16 icon at (x, y). */
    private static void socket(GuiGraphics g, int x, int y, int fill) {
        g.fill(x - 1, y - 1, x + 17, y + 17, GOLD);
        g.fill(x - 1, y - 1, x + 17, y, GOLD_SHADE);
        g.fill(x - 1, y, x, y + 16, GOLD_SHADE);
        g.fill(x, y, x + 16, y + 16, fill);
    }

    private static void lamp(GuiGraphics g, int x, int y, int color) {
        // A small faceted aquamarine set in gold, dimmed when the feature is off.
        g.fill(x + 1, y - 1, x + 3, y + 5, GOLD_SHADE);
        g.fill(x - 1, y + 1, x + 5, y + 3, GOLD_SHADE);
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x, y, x + 3, y + 1, 0xBBE7FFFF);
        g.fill(x + 3, y + 1, x + 4, y + 4, 0x550D3844);
    }

    /** A right-pointing arrowhead, four pixels long and seven tall, centred on row y. */
    private static void arrowhead(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 4; i++) g.fill(x + i, y - 3 + i, x + i + 1, y + 4 - i, color);
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

    /** A wooden tab grown out of the frame's edge, turning between the workbench and its configuration. */
    private final class TabButton extends Button {
        private TabButton(int x, int y) {
            super(x, y, TAB_WIDTH, TAB_HEIGHT, Component.empty(), ignored -> toggleConfiguration(),
                    DEFAULT_NARRATION);
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean open = menu.isConfigurationOpen(), hover = isHoveredOrFocused();
            int x = getX(), y = getY(), right = x + width, bottom = y + height;
            g.fill(x + 3, y + 4, right + 3, bottom + 4, 0x66000000);
            // The frame's wood runs on across its outer border into the tab.
            g.fill(x - 1, y, right, bottom, 0xFF3C3020);
            tile(g, WOOD, x - 4, y + 1, width + 3, height - 2, leftPos + 1, topPos + 1);
            g.fill(x - 1, y + 1, right - 1, y + 2, 0xFFAA884A);
            int iconX = x + 3, iconY = y + 5;
            g.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, hover ? AQUA_LIGHT : GOLD);
            g.fill(iconX - 1, iconY - 1, iconX + 17, iconY, hover ? AQUA : GOLD_SHADE);
            g.fill(iconX - 1, iconY, iconX, iconY + 16, hover ? AQUA : GOLD_SHADE);
            g.fill(iconX, iconY, iconX + 16, iconY + 16, open ? 0xFF2C5866 : 0xFF33363D);
            g.renderItem(open ? chisel : wrench, iconX, iconY);
        }
    }

    /** A wood-framed marble plaque like the Lumen Array's buttons: toggles carry a lamp, cycling settings an arrow. */
    private final class SettingButton extends Button {
        private final BooleanSupplier lamp;

        private SettingButton(int x, int y, int width, int id, BooleanSupplier lamp) {
            super(x, y, width, 18, Component.empty(), ignored -> send(id), DEFAULT_NARRATION);
            this.lamp = lamp;
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY(), w = width, h = height;
            boolean hover = active && isHoveredOrFocused();
            int border = hover ? AQUA : GOLD_SHADE;
            g.fill(x + 1, y + 1, x + w - 1, y + h, 0xFF98907C);
            g.fill(x, y + 1, x + w, y + h - 2, border);
            g.fill(x + 1, y, x + w - 1, y + h - 1, border);
            tile(g, WOOD, x + 1, y + 1, w - 2, h - 3);
            tile(g, MARBLE, x + 3, y + 2, w - 6, h - 5);
            // Settings the current mode ignores fade into the stone.
            g.fill(x + 3, y + 2, x + w - 3, y + h - 3, hover ? 0x605FCBDC : active ? 0x58FFF9E8 : 0xA0D8D3C5);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFF2BF);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GOLD);
            if (lamp != null) lamp(g, x + w - 12, y + (h - 4) / 2, !active ? FADED : lamp.getAsBoolean() ? AQUA : 0xFF9C9685);
            else arrowhead(g, x + w - 11, y + (h - 3) / 2 + 1, !active ? FADED : hover ? AQUA : GOLD_SHADE);
            fitted(g, getMessage(), x + 7, y + (h - font.lineHeight) / 2, w - 24, active ? INK : 0xFF91897A);
        }
    }

    private final class FaceButton extends Button {
        private final Direction side;
        private AutoChiselBlockEntity.SideMode lastMode;

        private FaceButton(Direction side, int x, int y) {
            super(x, y, 24, 24, Component.empty(), ignored -> send(side.ordinal()), DEFAULT_NARRATION);
            this.side = side;
        }

        private void update(boolean open) {
            visible = active = open;
            var mode = menu.getSideMode(side);
            if (mode != lastMode) {
                lastMode = mode;
                setMessage(tr("side_label", tr("direction." + side.getName()), mode.label()));
            }
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible || !active || (button != 0 && button != 1) || !clicked(mouseX, mouseY)) return false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            send(button == 0 ? side.ordinal() : AutoChiselMenu.REVERSE_SIDE_BASE + side.ordinal());
            return true;
        }

        @ParametersAreNonnullByDefault
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            int x = getX(), y = getY();
            boolean hover = isHoveredOrFocused();
            if (hover) g.renderOutline(x - 1, y - 1, width + 2, height + 2, 0x90FFF2BF);
            var mode = menu.getSideMode(side);
            faceTile(g, x, y, width, faceColor(mode), hover ? GOLD : 0xFF4A4034);
            Component label = tr("face_short." + side.getName());
            boolean disabled = mode == AutoChiselBlockEntity.SideMode.NONE;
            g.drawString(font, label, x + (width - font.width(label) + 1) / 2, y + 8,
                    disabled ? INK : 0xFFFFFFFF, !disabled);
        }
    }
}
