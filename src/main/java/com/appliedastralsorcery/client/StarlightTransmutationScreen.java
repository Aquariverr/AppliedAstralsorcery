package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.transmutation.TransmutationFilterSelection;
import com.appliedastralsorcery.transmutation.TransmutationMarkerAmount;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity.Status;
import com.appliedastralsorcery.transmutation.StarlightTransmutationMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import hellfirepvp.astralsorcery.client.util.RenderConstellationUtil;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import static com.appliedastralsorcery.client.AstralGuiArt.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class StarlightTransmutationScreen extends AbstractContainerScreen<StarlightTransmutationMenu> {
    public static final int RECIPE_X = 86, RECIPE_Y = 34, RECIPE_WIDTH = 92, RECIPE_HEIGHT = 78;
    // JEI's recipe hint stays above the pointer; place the constellation name below it.
    private static final ClientTooltipPositioner CONSTELLATION_TOOLTIP = (width, height, mouseX, mouseY, tipWidth, tipHeight) ->
            DefaultTooltipPositioner.INSTANCE.positionTooltip(width, height, mouseX, mouseY + 28, tipWidth, tipHeight);
    private static final int NAVY = 0xFF161D48, DUSK = 0xFF202B66, DORMANT = 0xFF3F6FB8;
    private static final int[] GLOW = {0xFF2D3D88, 0xFF3F6FB8, 0xFF6FA3E0, 0xFFA9D4FA, 0xFFFFFFFF};
    private static final int HEADER_X = 12, HEADER_Y = 10, HEADER_WIDTH = 240, HEADER_HEIGHT = 22;
    private static final int TOGGLE_X = 124, TOGGLE_WIDTH = 92, TOGGLE_HEIGHT = 14;
    private static final int PROGRESS_X = 20, PROGRESS_Y = 114, PROGRESS_WIDTH = 224, PROGRESS_HEIGHT = 10;
    private static final int PILLAR_TOP = 141, PILLAR_BOTTOM = 270;
    private static final int EDITOR_WIDTH = 208, EDITOR_HEIGHT = 136;
    private static final int[][] STARS = {{0, 9}, {8, 4}, {17, 8}, {26, 2}, {33, 11}, {41, 6}};
    private static final int[][] LINKS = {{0, 1}, {1, 2}, {2, 3}, {2, 4}, {4, 5}};
    private static final Component ME = Component.literal("ME");
    private final ItemStack chamber = ModContent.STARLIGHT_TRANSMUTATION_CHAMBER_ITEM.toStack();
    private Button autoPullButton;
    private Button overclockButton;
    private AmountEditor amountEditor;
    private boolean markerClick;

    public StarlightTransmutationScreen(StarlightTransmutationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 264;
        imageHeight = 280;
    }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.appliedas.transmutation." + key, args); }

    @Override protected void init() {
        super.init();
        autoPullButton = toggle(139, StarlightTransmutationMenu.AUTO_PULL_BUTTON, menu::isAutoPull);
        autoPullButton.setTooltip(Tooltip.create(tr("auto_pull_hint")));
        overclockButton = toggle(181, StarlightTransmutationMenu.OVERCLOCK_BUTTON, menu::isLumenOverclock);
        overclockButton.setTooltip(Tooltip.create(tr("overclock_hint")));
        autoPullButton.setMessage(modeLabel());
        overclockButton.setMessage(overclockLabel());
        if (amountEditor != null && minecraft != null) amountEditor.init(minecraft, width, height);
    }
    private Button toggle(int y, int id, BooleanSupplier lamp) {
        return addRenderableWidget(new Plaque(leftPos + TOGGLE_X, topPos + y, TOGGLE_WIDTH, TOGGLE_HEIGHT, Component.empty(),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null)
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                }, lamp));
    }
    private Component modeLabel() { return tr(menu.isAutoPull() ? "auto_pull_on" : "auto_pull_off"); }
    private Component overclockLabel() { return tr(menu.isLumenOverclock() ? "overclock_on" : "overclock_off"); }
    @Override protected void containerTick() {
        super.containerTick();
        autoPullButton.setMessage(modeLabel());
        overclockButton.setMessage(overclockLabel());
        if (amountEditor != null && (!menu.isAutoPull() || !ItemStack.isSameItemSameComponents(
                amountEditor.item, menu.getPullMarker(amountEditor.slot)))) amountEditor = null;
    }
    public Rect2i getMarkerArea(int slot) {
        var position = menu.getSlot(StarlightTransmutationMenu.MARKER_SLOT_START + slot);
        return new Rect2i(leftPos + position.x, topPos + position.y, 16, 16);
    }
    public boolean canEditMarkers() { return amountEditor == null && menu.isAutoPull(); }
    public void acceptJeiMarker(int slot, ItemStack stack) {
        if (canEditMarkers() && minecraft != null && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new TransmutationFilterSelection(menu.containerId, slot, stack.copy()));
    }
    private int markerAt(double mouseX, double mouseY) {
        if (!menu.isAutoPull()) return -1;
        for (int slot = 0; slot < 9; slot++) {
            var position = menu.getSlot(StarlightTransmutationMenu.MARKER_SLOT_START + slot);
            if (isHovering(position.x, position.y, 16, 16, mouseX, mouseY)) return slot;
        }
        return -1;
    }
    private void openAmountEditor(int slot) {
        var marker = menu.getPullMarker(slot);
        if (marker.isEmpty() || minecraft == null) return;
        amountEditor = new AmountEditor(slot, marker.copy());
        amountEditor.init(minecraft, width, height);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft == null || minecraft.player == null) return false;
        if (amountEditor != null) {
            amountEditor.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        int slot = markerAt(mouseX, mouseY);
        if (slot >= 0) {
            markerClick = true;
            if (minecraft.options.keyPickItem.matchesMouse(button)) openAmountEditor(slot);
            else if ((button == 0 || button == 1) && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId,
                        StarlightTransmutationMenu.MARKER_SLOT_START + slot, button, ClickType.PICKUP, minecraft.player);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (markerClick) { markerClick = false; return true; }
        if (amountEditor != null) { amountEditor.mouseReleased(mouseX, mouseY, button); return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (amountEditor != null) { amountEditor.mouseDragged(mouseX, mouseY, button, dragX, dragY); return true; }
        if (markerClick) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (amountEditor != null) { amountEditor.keyPressed(key, scanCode, modifiers); return true; }
        if (minecraft != null && menu.isAutoPull() && hoveredSlot != null && hoveredSlot.index >= StarlightTransmutationMenu.MARKER_SLOT_START
                && minecraft.options.keyPickItem.matches(key, scanCode)) {
            openAmountEditor(hoveredSlot.index - StarlightTransmutationMenu.MARKER_SLOT_START);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
        return amountEditor != null ? amountEditor.keyReleased(key, scanCode, modifiers) : super.keyReleased(key, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return amountEditor != null ? amountEditor.charTyped(character, modifiers) : super.charTyped(character, modifiers);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (amountEditor != null) { amountEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY); return true; }
        int slot = markerAt(mouseX, mouseY);
        if (slot >= 0 && scrollY != 0) {
            var marker = menu.getPullMarker(slot);
            if (!marker.isEmpty()) PacketDistributor.sendToServer(new TransmutationMarkerAmount(menu.containerId, slot,
                    marker.copyWithCount(1), (scrollY > 0 ? 1 : -1) * (hasShiftDown() ? 10 : 1), true));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        boolean running = menu.getStatus() == Status.RUNNING;
        frame(g, x, y, imageWidth, imageHeight);
        renderHeader(g, x + HEADER_X, y + HEADER_Y, running);

        inset(g, x + 14, y + 34, 68, 78);
        inset(g, x + 182, y + 34, 68, 78);
        trayLabel(g, tr("input"), x + 48, y + 39);
        trayLabel(g, tr("output"), x + 216, y + 39);
        for (int slot = 0; slot < 18; slot++) {
            var position = menu.getSlot(slot);
            slot(g, x + position.x, y + position.y, slot >= 9);
        }
        renderObservatory(g, x + RECIPE_X, y + RECIPE_Y, running);
        arrowhead(g, x + 82, y + 73, running ? AQUA : GOLD_SHADE);
        arrowhead(g, x + 178, y + 73, running ? AQUA : GOLD_SHADE);

        int duration = menu.getDuration();
        star(g, x + 15, y + 119, 2, GOLD_SHADE);
        channel(g, x + PROGRESS_X, x + PROGRESS_X + PROGRESS_WIDTH, y + 117,
                duration <= 0 ? 0F : Math.clamp(menu.getProgress() / (float) duration, 0F, 1F));
        star(g, x + 249, y + 119, 2, GOLD_SHADE);
        renderStatus(g, x, y + 128);

        boolean pulling = menu.isAutoPull();
        inset(g, x + 44, y + 137, 176, 38);
        fitted(g, tr("markers"), x + 51, y + 141, TOGGLE_X - 55, MUTED);
        for (int slot = 0; slot < 9; slot++) {
            var position = menu.getSlot(StarlightTransmutationMenu.MARKER_SLOT_START + slot);
            marker(g, x + position.x, y + position.y, pulling);
        }
        inset(g, x + 44, y + 179, 176, 95);
        fitted(g, playerInventoryTitle, x + 51, y + 183, TOGGLE_X - 55, MUTED);
        g.fill(x + 50, y + 251, x + 214, y + 252, GOLD_SHADE);
        g.fill(x + 50, y + 252, x + 214, y + 253, GOLD);
        for (int slot = 18; slot < StarlightTransmutationMenu.MARKER_SLOT_START; slot++) {
            var position = menu.getSlot(slot);
            slot(g, x + position.x, y + position.y, false);
        }
        for (int center : new int[]{25, imageWidth - 25})
            pillar(g, x + center, y + PILLAR_TOP, y + PILLAR_BOTTOM, running, menu.hasStarlight());
    }

    private void renderHeader(GuiGraphics g, int x, int y, boolean running) {
        skyBand(g, x, y, HEADER_WIDTH, HEADER_HEIGHT, 147, 128);
        socket(g, x + 5, y + 3, running ? 0xFF1E3A5A : 0xFF33363D);
        g.renderItem(chamber, x + 5, y + 3);
        boolean online = menu.isOnline();
        int meX = x + HEADER_WIDTH - 17 - font.width(ME), titleWidth = Math.min(font.width(title), meX - x - 35);
        fitted(g, title, x + 27, y + 7, titleWidth, CREAM);
        int chainX = meX - 56;
        if (chainX >= x + 27 + titleWidth + 12) constellation(g, chainX, y + 3, menu.hasStarlight());
        g.drawString(font, ME, meX, y + 7, online ? PARCHMENT : 0xFF8F846C, false);
        lamp(g, x + HEADER_WIDTH - 13, y + 9, online ? AQUA_LIGHT : WARNING);
    }
    private int networkX() { return HEADER_X + HEADER_WIDTH - 19 - font.width(ME); }

    private static void constellation(GuiGraphics g, int x, int y, boolean lit) {
        for (int[] link : LINKS) {
            int[] from = STARS[link[0]], to = STARS[link[1]];
            line(g, x + from[0], y + from[1], x + to[0], y + to[1], lit ? 0x709FE9F2 : 0x30A6B0C4);
        }
        float time = Util.getMillis() / 600F;
        for (int i = 0; i < STARS.length; i++) {
            int sx = x + STARS[i][0], sy = y + STARS[i][1], radius = i == 3 ? 2 : i % 2 == 0 ? 1 : 2;
            int color = lit ? i == 3 ? GOLD : AQUA_LIGHT : i == 3 ? 0xFF8F846C : 0xFF647088;
            if (lit && radius > 1) {
                int halo = (int) (0x30 + 0x20 * Mth.sin(time + i * 1.7F));
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, halo << 24 | color & 0xFFFFFF);
            }
            star(g, sx, sy, radius, color, lit ? 0xFFFFFFFF : 0xFFA7AFBE);
        }
    }

    private void renderObservatory(GuiGraphics g, int x, int y, boolean running) {
        skyBand(g, x, y, RECIPE_WIDTH, RECIPE_HEIGHT, 181, 113);
        int cx = x + RECIPE_WIDTH / 2, cy = y + RECIPE_HEIGHT / 2;
        float sweep = Util.getMillis() / 1000F * 120F;
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI / 24;
            int px = cx + (int) Math.round(Math.cos(angle) * 32), py = cy + (int) Math.round(Math.sin(angle) * 32);
            float distance = Mth.wrapDegrees(i * 7.5F - sweep) / 24F;
            int color = running ? FastColor.ARGB32.lerp((float) Math.exp(-distance * distance), 0x50D4BE76, 0xF0DDF9FF)
                    : 0x48D4BE76;
            g.fill(px, py, px + 1, py + 1, color);
        }
        for (int[] point : new int[][]{{0, -32}, {32, 0}, {0, 32}, {-32, 0}})
            star(g, cx + point[0], cy + point[1], 1, 0x90D4BE76, 0xC0FFF3C6);
        var constellation = menu.getConstellation();
        if (constellation == null) return;
        // The native renderer draws immediately; submit the buffered sky first.
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderConstellationUtil.drawConstellationUI(constellation.getConstellationColor(), constellation,
                    g.pose(), cx - 28, cy - 28, 56, 56, 1.5F, () -> 1F, true, false);
        } finally {
            RenderSystem.disableBlend();
        }
    }

    private static void channel(GuiGraphics g, int x1, int x2, int y, float fill) {
        int end = x2 - 4, length = Math.round((end - x1 - 2) * fill), head = x1 + 1 + length;
        g.fill(x1, y, end, y + 5, 0xFF777568);
        g.fill(x1 + 1, y + 1, end - 1, y + 4, 0xFFACA99A);
        g.fill(x1 + 1, y + 4, end - 1, y + 5, 0xFFF9F3DF);
        if (length > 0) {
            g.fill(x1 + 1, y + 1, head, y + 4, AQUA_LIGHT);
            g.fill(x1 + 1, y + 1, head, y + 2, 0xFFDDF9FF);
            g.fill(x1 + 1, y + 3, head, y + 4, AQUA);
        }
        arrowhead(g, end, y + 2, fill >= 1F ? AQUA : GOLD_SHADE);
        if (length > 0 && fill < 1F) star(g, head, y + 2, 2, STARLIGHT, 0xFFFFFFFF);
    }

    private void renderStatus(GuiGraphics g, int x, int y) {
        var status = menu.getStatus();
        boolean running = status == Status.RUNNING, idle = status == Status.IDLE;
        Component text = tr("status." + status.name().toLowerCase(Locale.ROOT));
        int textWidth = Math.min(font.width(text), 170), total = textWidth + 9, left = x + (imageWidth - total) / 2;
        lamp(g, left + 1, y + 2, running ? AQUA_LIGHT : idle ? 0xFF9C9685 : WARNING);
        fitted(g, text, left + 9, y, textWidth, running ? 0xFF276D84 : idle ? MUTED : 0xFF9A5A1E);
        g.fill(x + 20, y + 3, left - 9, y + 4, GOLD);
        g.fill(left + total + 8, y + 3, x + imageWidth - 20, y + 4, GOLD);
        star(g, left - 6, y + 3, 2, GOLD_SHADE);
        star(g, left + total + 4, y + 3, 2, GOLD_SHADE);
    }

    private void trayLabel(GuiGraphics g, Component text, int centerX, int y) {
        int width = Math.min(font.width(text), 58), left = centerX - width / 2;
        fitted(g, text, left, y, width, MUTED);
        if (width <= 44) {
            star(g, left - 5, y + 3, 1, GOLD_SHADE);
            star(g, left + width + 3, y + 3, 1, GOLD_SHADE);
        }
    }

    private static void pillar(GuiGraphics g, int cx, int top, int bottom, boolean running, boolean charged) {
        int shaftTop = top + 4, shaftBottom = bottom - 4;
        g.fill(cx - 4, shaftTop, cx + 5, shaftBottom, 0xFF817C72);
        g.fill(cx - 3, shaftTop, cx + 4, shaftBottom, 0xFFE7E5DF);
        g.fill(cx - 3, shaftTop, cx - 2, shaftBottom, 0xFFF6F5F1);
        g.fill(cx + 3, shaftTop, cx + 4, shaftBottom, 0xFFBEBAB0);
        int channelTop = shaftTop + 3, channelBottom = shaftBottom - 3, length = channelBottom - channelTop;
        g.fill(cx - 1, channelTop - 1, cx + 2, channelBottom + 1, 0xFF0D1331);
        float head = (Util.getMillis() % 2200L) / 2200F * (length + 40) - 20;
        for (int row = channelTop; row < channelBottom; row++) {
            int core, side;
            if (running) {
                float distance = (row - channelTop - head) / 7F, pulse = (float) Math.exp(-distance * distance);
                core = glow(0.15F + 0.85F * pulse);
                side = FastColor.ARGB32.lerp(pulse * 0.8F, DUSK, GLOW[2]);
            } else {
                core = charged || (row - channelTop) % 8 == 4 ? DORMANT : DUSK;
                side = NAVY;
            }
            g.fill(cx - 1, row, cx + 2, row + 1, side);
            g.fill(cx, row, cx + 1, row + 1, core);
        }
        for (int cap : new int[]{top, bottom - 4}) {
            g.fill(cx - 5, cap, cx + 6, cap + 4, GOLD);
            g.fill(cx - 5, cap, cx + 6, cap + 1, 0xFFEFD27B);
            g.fill(cx - 5, cap + 3, cx + 6, cap + 4, GOLD_SHADE);
        }
        star(g, cx, top - 3, 2, GOLD_SHADE, running ? 0xFFFFFFFF : GOLD);
    }
    private static int glow(float level) {
        float position = Mth.clamp(level, 0F, 1F) * (GLOW.length - 1);
        int index = Math.min((int) position, GLOW.length - 2);
        return FastColor.ARGB32.lerp(position - index, GLOW[index], GLOW[index + 1]);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, amountEditor == null ? mouseX : -10000, amountEditor == null ? mouseY : -10000, partialTick);
        if (amountEditor != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            amountEditor.render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            return;
        }
        boolean markerHovered = menu.isAutoPull() && hoveredSlot != null
                && hoveredSlot.index >= StarlightTransmutationMenu.MARKER_SLOT_START;
        if (markerHovered) {
            int slot = hoveredSlot.index - StarlightTransmutationMenu.MARKER_SLOT_START;
            var marker = menu.getPullMarker(slot);
            var actual = menu.getSlot(slot).getItem();
            graphics.renderComponentTooltip(font, List.of(marker.isEmpty() ? tr("marker_empty") : marker.getHoverName(),
                    tr("marker_stock", actual.getCount(), marker.getCount()), tr("marker_hint"), tr("marker_amount_hint")), mouseX, mouseY);
        } else renderTooltip(graphics, mouseX, mouseY);
        boolean recipeHovered = isHovering(RECIPE_X, RECIPE_Y, RECIPE_WIDTH, RECIPE_HEIGHT, mouseX, mouseY);
        if (recipeHovered && menu.getConstellation() != null)
            graphics.renderTooltip(font, List.of(menu.getConstellation().getName().getVisualOrderText()),
                    CONSTELLATION_TOOLTIP, mouseX, mouseY);
        int networkX = networkX();
        if (isHovering(networkX, HEADER_Y + 2, HEADER_X + HEADER_WIDTH - 2 - networkX, HEADER_HEIGHT - 4, mouseX, mouseY))
            graphics.renderTooltip(font, tr(menu.isOnline() ? "network_online" : "network_offline"), mouseX, mouseY);
        if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY) && menu.getDuration() > 0)
            graphics.renderTooltip(font, tr("progress", menu.getProgress(), menu.getDuration()), mouseX, mouseY);
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int width, int color) {
        if (width <= 0) return;
        int textWidth = font.width(text);
        if (textWidth <= width) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = width / (float) textWidth;
        g.pose().pushPose();
        try {
            g.pose().translate(x, y + (font.lineHeight * (1F - scale)) / 2F, 0);
            g.pose().scale(scale, scale, 1F);
            g.drawString(font, text, 0, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    private static void marker(GuiGraphics g, int x, int y, boolean enabled) {
        g.fill(x - 1, y - 1, x + 17, y + 17, enabled ? 0xFFB5E4EA : 0xFFE9E4D8);
        g.fill(x - 1, y - 1, x + 17, y, enabled ? 0xFF2F6F86 : 0xFFA49E91);
        g.fill(x - 1, y, x, y + 16, enabled ? 0xFF2F6F86 : 0xFFA49E91);
        g.fill(x, y, x + 16, y + 16, enabled ? 0xFF929E9F : 0xFFCBC6BA);
        if (!enabled) star(g, x + 7, y + 7, 2, 0xFFB2AB9C, 0xFFEDE8DC);
    }

    private final class Plaque extends Button {
        private final BooleanSupplier lamp;

        private Plaque(int x, int y, int width, int height, Component message, OnPress onPress, @Nullable BooleanSupplier lamp) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
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
            g.fill(x + 3, y + 2, x + w - 3, y + h - 3, hover ? 0x605FCBDC : active ? 0x58FFF9E8 : 0xA0D8D3C5);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFF2BF);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GOLD);
            int textY = y + (h - 1 - font.lineHeight) / 2 + 1, color = active ? INK : 0xFF91897A;
            if (lamp != null) {
                lamp(g, x + w - 12, y + (h - 5) / 2, !active ? FADED : lamp.getAsBoolean() ? AQUA : 0xFF9C9685);
                fitted(g, getMessage(), x + 7, textY, w - 24, color);
            } else {
                int textWidth = Math.min(font.width(getMessage()), w - 10);
                fitted(g, getMessage(), x + (w - textWidth) / 2, textY, textWidth, color);
            }
        }
    }

    private final class AmountEditor extends Screen {
        private final int slot;
        private final ItemStack item;
        private final int maximum;
        private EditBox input;
        private Button confirm;
        private String value;

        private AmountEditor(int slot, ItemStack item) {
            super(tr("amount_title"));
            this.slot = slot;
            this.item = item;
            maximum = Math.min(64, item.getMaxStackSize());
            value = Integer.toString(item.getCount());
        }
        @Override protected void init() {
            int x = (width - EDITOR_WIDTH) / 2, y = (height - EDITOR_HEIGHT) / 2;
            input = addRenderableWidget(new EditBox(font, x + 22, y + 52, 164, 10, tr("amount_title")));
            input.setBordered(false);
            input.setTextColor(CREAM);
            input.setMaxLength(3);
            input.setFilter(text -> text.isEmpty() || text.matches("[0-9]{1,3}"));
            input.setValue(value);
            input.setResponder(text -> { value = text; confirm.active = validAmount(); });
            int[] changes = {-10, -1, 1, 10};
            for (int i = 0; i < changes.length; i++) {
                int change = changes[i];
                addRenderableWidget(new Plaque(x + 16 + 45 * i, y + 72, 41, 18,
                        Component.literal(change > 0 ? "+" + change : "" + change), button -> adjust(change), null));
            }
            confirm = addRenderableWidget(new Plaque(x + 16, y + 106, 85, 18, Component.translatable("gui.done"),
                    button -> save(), null));
            addRenderableWidget(new Plaque(x + 107, y + 106, 85, 18, Component.translatable("gui.cancel"),
                    button -> onClose(), null));
            confirm.active = validAmount();
            setInitialFocus(input);
            input.setHighlightPos(0);
        }
        private boolean validAmount() { return !value.isEmpty() && Integer.parseInt(value) <= maximum; }
        private void adjust(int delta) {
            int amount = value.isEmpty() ? 0 : Integer.parseInt(value);
            input.setValue(Integer.toString(Math.clamp(amount + delta, 0, maximum)));
        }
        private void save() {
            if (!validAmount()) return;
            PacketDistributor.sendToServer(new TransmutationMarkerAmount(menu.containerId, slot,
                    item.copyWithCount(1), Integer.parseInt(value), false));
            onClose();
        }
        @Override public void onClose() { amountEditor = null; }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // This is an overlay on the live container. Screen.render's default background
            // would blur the panel we just drew, then cover it with another menu background.
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
            return super.keyPressed(key, scanCode, modifiers);
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (vertical != 0) adjust((vertical > 0 ? 1 : -1) * (hasShiftDown() ? 10 : 1));
            return true;
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = (width - EDITOR_WIDTH) / 2, y = (height - EDITOR_HEIGHT) / 2;
            graphics.fill(0, 0, width, height, 0xB0000000);
            frame(graphics, x, y, EDITOR_WIDTH, EDITOR_HEIGHT);
            skyBand(graphics, x + 10, y + 10, EDITOR_WIDTH - 20, 30, 144, 42);
            socket(graphics, x + 16, y + 17, 0xFF33363D);
            graphics.renderItem(item, x + 16, y + 17);
            fitted(graphics, title, x + 39, y + 15, EDITOR_WIDTH - 59, CREAM);
            fitted(graphics, tr("amount_range", maximum), x + 39, y + 27, EDITOR_WIDTH - 59, PARCHMENT);
            int fieldX = x + 16, fieldY = y + 46, fieldWidth = EDITOR_WIDTH - 32;
            boolean valid = validAmount();
            graphics.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + 20, valid ? GOLD : WARNING);
            graphics.fill(fieldX, fieldY, fieldX + fieldWidth, fieldY + 1, valid ? GOLD_SHADE : 0xFF8A4A1A);
            graphics.fill(fieldX, fieldY, fieldX + 1, fieldY + 20, valid ? GOLD_SHADE : 0xFF8A4A1A);
            graphics.blit(SKY, fieldX + 1, fieldY + 1, 140, 150, fieldWidth - 2, 18, 450, 300);
            graphics.fill(fieldX + 1, fieldY + 1, fieldX + fieldWidth - 1, fieldY + 19, 0x50000000);
            graphics.fill(x + 24, y + 98, x + EDITOR_WIDTH - 24, y + 99, GOLD);
            star(graphics, x + 20, y + 98, 2, GOLD_SHADE);
            star(graphics, x + EDITOR_WIDTH - 21, y + 98, 2, GOLD_SHADE);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
