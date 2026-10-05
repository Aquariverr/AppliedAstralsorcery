package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.gateway.MECelestialGatewayMenu;
import com.appliedastralsorcery.gateway.SpatialCellAccess;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import static com.appliedastralsorcery.client.AstralGuiArt.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MECelestialGatewayScreen extends AbstractContainerScreen<MECelestialGatewayMenu> {
    private static final int WINDOW_X = 12, WINDOW_Y = 34, WINDOW_WIDTH = 44, WINDOW_HEIGHT = 28;
    private static final int[][] STARS = {{18, 40}, {50, 39}, {17, 53}, {49, 55}, {22, 47}, {46, 46}};
    private final ItemStack gateway = ModContent.ME_CELESTIAL_GATEWAY_ITEM.toStack();

    public MECelestialGatewayScreen(MECelestialGatewayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 184;
    }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.appliedas.gateway." + key, args); }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        boolean active = menu.active(), formatting = SpatialCellAccess.isFormatting(menu.getSlot(0).getItem());
        frame(g, x, y, imageWidth, imageHeight);

        skyBand(g, x + 8, y + 8, imageWidth - 16, 22, 150, 64);
        socket(g, x + 13, y + 11, active ? 0xFF1E3A5A : 0xFF33363D);
        g.renderItem(gateway, x + 13, y + 11);
        fitted(g, title, x + 34, y + 15, imageWidth - 50, CREAM);

        renderWindow(g, x + WINDOW_X, y + WINDOW_Y, active, formatting);
        inset(g, x + 60, y + WINDOW_Y, imageWidth - 70, WINDOW_HEIGHT);
        var lines = font.split(tr("use_star"), imageWidth - 82);
        int lineY = y + WINDOW_Y + (WINDOW_HEIGHT - Math.min(lines.size(), 2) * font.lineHeight) / 2 + 1;
        for (int i = 0; i < Math.min(lines.size(), 2); i++)
            g.drawString(font, lines.get(i), x + 66, lineY + i * font.lineHeight, INK, false);

        renderStatus(g, x, y + 67, active, formatting);
        var size = tr("size");
        int sizeWidth = Math.min(font.width(size), imageWidth - 40), sizeX = x + (imageWidth - sizeWidth) / 2;
        fitted(g, size, sizeX, y + 80, sizeWidth, MUTED);
        star(g, sizeX - 6, y + 83, 1, GOLD_SHADE);
        star(g, sizeX + sizeWidth + 4, y + 83, 1, GOLD_SHADE);

        int labelWidth = Math.min(font.width(playerInventoryTitle), 120);
        fitted(g, playerInventoryTitle, x + 8, y + 91, labelWidth, MUTED);
        g.fill(x + 12 + labelWidth, y + 95, x + imageWidth - 8, y + 96, GOLD);
        g.fill(x + 8, y + 156, x + imageWidth - 8, y + 157, GOLD_SHADE);
        g.fill(x + 8, y + 157, x + imageWidth - 8, y + 158, GOLD);
        for (int slot = 1; slot < menu.slots.size(); slot++) {
            var position = menu.getSlot(slot);
            slot(g, x + position.x, y + position.y, false);
        }
    }

    private void renderWindow(GuiGraphics g, int x, int y, boolean active, boolean formatting) {
        skyBand(g, x, y, WINDOW_WIDTH, WINDOW_HEIGHT, 210, 150);
        float time = Util.getMillis() / 600F;
        for (int i = 0; i < STARS.length; i++) {
            int sx = leftPos + STARS[i][0], sy = topPos + STARS[i][1], radius = i < 4 ? 1 : 0;
            if (active) {
                int halo = (int) (0x28 + 0x20 * Mth.sin(time + i * 1.9F));
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, halo << 24 | AQUA_LIGHT & 0xFFFFFF);
            }
            star(g, sx, sy, radius, active ? AQUA_LIGHT : 0xFF647088, active ? 0xFFFFFFFF : 0xFFA7AFBE);
        }
        var cell = menu.getSlot(0);
        socket(g, leftPos + cell.x, topPos + cell.y, active ? 0xFF1E3A5A : 0xFF33363D);
        if (!cell.hasItem()) star(g, leftPos + cell.x + 7, topPos + cell.y + 7, 2, 0xFF55607A, 0xFF8C96AE);
        if (formatting) {
            int left = x + 3, right = x + WINDOW_WIDTH - 3, head = left + Math.round((right - left) * menu.progress() / 100F);
            g.fill(left, y + WINDOW_HEIGHT - 4, right, y + WINDOW_HEIGHT - 2, 0x80161D48);
            g.fill(left, y + WINDOW_HEIGHT - 4, head, y + WINDOW_HEIGHT - 3, 0xFFDDF9FF);
            g.fill(left, y + WINDOW_HEIGHT - 3, head, y + WINDOW_HEIGHT - 2, AQUA);
        }
    }

    private void renderStatus(GuiGraphics g, int x, int y, boolean active, boolean formatting) {
        Component text = formatting ? tr("progress", menu.progress()) : tr(active ? "ready" : "inactive");
        int textWidth = Math.min(font.width(text), imageWidth - 60), total = textWidth + 9, left = x + (imageWidth - total) / 2;
        int lamp = formatting ? STARLIGHT : active ? AQUA_LIGHT : WARNING;
        lamp(g, left + 1, y + 2, lamp);
        fitted(g, text, left + 9, y, textWidth, formatting || active ? 0xFF276D84 : 0xFF9A5A1E);
        g.fill(x + 12, y + 3, left - 9, y + 4, GOLD);
        g.fill(left + total + 8, y + 3, x + imageWidth - 12, y + 4, GOLD);
        star(g, left - 6, y + 3, 2, GOLD_SHADE);
        star(g, left + total + 4, y + 3, 2, GOLD_SHADE);
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

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var cell = menu.getSlot(0);
        if (!cell.hasItem() && menu.getCarried().isEmpty() && isHovering(cell.x, cell.y, 16, 16, mouseX, mouseY))
            graphics.renderTooltip(font, font.split(tr("requirements"), 180), mouseX, mouseY);
        else renderTooltip(graphics, mouseX, mouseY);
    }
}
