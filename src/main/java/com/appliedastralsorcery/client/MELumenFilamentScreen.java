package com.appliedastralsorcery.client;

import java.util.ArrayList;
import java.util.List;

import appeng.api.client.AEKeyRendering;
import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.integration.jei.GhostIngredientResolver;
import com.appliedastralsorcery.lumen.LumenKey;
import com.appliedastralsorcery.lumen.MELumenFilamentMenu;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Slot-free configuration screen. All changes are validated by the server menu. */
public final class MELumenFilamentScreen extends AbstractContainerScreen<MELumenFilamentMenu> {
    private static final int LIST_TOP = 94;
    private static final int ROW_HEIGHT = 24;
    private static final int TEXT = 0xFF353332;
    private static final int MUTED = 0xFF575452;
    private static final int PALE = 0xFFF2EBDD;
    private static final int GOLD = 0xFFD7BF75;
    private static final int GOLD_SHADE = 0xFF73603A;
    private static final ResourceLocation MARBLE = material("marble_raw");
    private static final ResourceLocation SOOTY_MARBLE = material("sooty_marble_raw");
    private static final ResourceLocation WOOD = material("infused_wood");
    private static final ResourceLocation CARVED_WOOD = material("infused_wood_engraved");

    private final List<LumenButton> lumenButtons = new ArrayList<>();
    private Button previousPage;
    private Button nextPage;
    private Button stop;
    private int rows;
    private int page;
    private boolean initialSelectionShown;

    public MELumenFilamentScreen(MELumenFilamentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(294, width - 16);
        rows = Math.max(1, Math.min(6, (height - 16 - 130) / ROW_HEIGHT));
        imageHeight = 130 + rows * ROW_HEIGHT;
        super.init();
        page = Math.min(page, pageCount() - 1);
        lumenButtons.clear();
        for (int row = 0; row < rows; row++) {
            lumenButtons.add(addRenderableWidget(new LumenButton(row)));
        }

        int footerY = topPos + imageHeight - 28;
        previousPage = addRenderableWidget(new AltarButton(leftPos + 12, footerY, 22,
                Component.literal("<"), button -> changePage(-1)));
        previousPage.setTooltip(Tooltip.create(Component.translatable("gui.appliedas.filament.previous")));
        nextPage = addRenderableWidget(new AltarButton(leftPos + 88, footerY, 22,
                Component.literal(">"), button -> changePage(1)));
        nextPage.setTooltip(Tooltip.create(Component.translatable("gui.appliedas.filament.next")));
        stop = addRenderableWidget(new AltarButton(leftPos + 122, footerY, imageWidth - 134,
                Component.translatable("gui.appliedas.filament.stop"), button -> sendSelection(0)));
        stop.setTooltip(Tooltip.create(Component.translatable("gui.appliedas.filament.stop_hint")));
        updateButtons();
    }

    private int pageCount() {
        return Math.max(1, (menu.getAvailableLumen().size() + rows - 1) / rows);
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(pageCount() - 1, page + direction));
        initialSelectionShown = true;
        updateButtons();
    }

    private void sendSelection(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    public List<Rect2i> getJeiDropAreas() {
        return List.of(new Rect2i(leftPos + imageWidth - 56, topPos + 35, 38, 38),
                new Rect2i(leftPos + 14, topPos + 32, imageWidth - 88, 14));
    }

    private boolean clickMarker(double x, double y, int button) {
        if ((button != 0 && button != 1) || !MarkerAreas.contains(getJeiDropAreas(), x, y)) return false;
        var lumen = GhostIngredientResolver.resolveLumen(menu.getCarried());
        if (lumen != null) acceptJeiLumen(lumen);
        return true;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return clickMarker(x, y, button) || super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!menu.getCarried().isEmpty() && clickMarker(x, y, button)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if ((button == 0 || button == 1) && MarkerAreas.contains(getJeiDropAreas(), x, y)) return true;
        return super.mouseReleased(x, y, button);
    }

    public void acceptJeiLumen(Lumen lumen) {
        int index = menu.getAvailableLumen().indexOf(lumen);
        if (index < 0) return;
        menu.setData(0, index + 1);
        page = index / rows;
        initialSelectionShown = true;
        sendSelection(index + 1);
        updateButtons();
    }

    private void updateButtons() {
        // The initial menu data may arrive after screen initialization.
        if (!initialSelectionShown && menu.getSelectedLumen() != null) {
            int selected = menu.getAvailableLumen().indexOf(menu.getSelectedLumen());
            if (selected >= 0) page = selected / rows;
            initialSelectionShown = true;
        }
        for (var button : lumenButtons) button.updateLumen();
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pageCount();
        stop.active = menu.getSelectedLumen() != null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        Component current = currentType();
        if (isHovering(14, 34, imageWidth - 88, font.lineHeight, mouseX, mouseY)) {
            graphics.renderTooltip(font, current, mouseX, mouseY);
        } else if (isHovering(14, 49, imageWidth - 88, font.lineHeight, mouseX, mouseY)) {
            graphics.renderTooltip(font, menu.getStatusMessage(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The same native marble and infused wood as AS's crafting altars, kept at one GUI pixel per texel.
        graphics.fill(leftPos + 2, topPos + 3, leftPos + imageWidth + 2, topPos + imageHeight + 3, 0x80000000);
        marblePlate(graphics, leftPos, topPos, imageWidth, imageHeight);
        woodFrame(graphics, leftPos + 7, topPos + 6, imageWidth - 14, 22);
        tile(graphics, SOOTY_MARBLE, leftPos + 11, topPos + 9, imageWidth - 22, 16);
        graphics.renderItem(ModContent.ME_LUMEN_FILAMENT_ITEM.toStack(), leftPos + 13, topPos + 9);
        tile(graphics, CARVED_WOOD, leftPos + imageWidth - 29, topPos + 9, 16, 16);

        // Four arms and a raised, gilt central socket echo the actual filament seen from above.
        drawFilament(graphics, leftPos + imageWidth - 56, topPos + 35);
        graphics.fill(leftPos + 13, topPos + 77, leftPos + imageWidth - 13, topPos + 78, 0xFF9B9790);
        graphics.fill(leftPos + 13, topPos + 78, leftPos + imageWidth - 13, topPos + 79, 0xFFF8F6EF);

        woodFrame(graphics, leftPos + 9, topPos + LIST_TOP - 4, imageWidth - 18, rows * ROW_HEIGHT + 6);
        tile(graphics, SOOTY_MARBLE, leftPos + 12, topPos + LIST_TOP - 1,
                imageWidth - 24, rows * ROW_HEIGHT);
        // Small silver clasps connect the inset to the marble slab, as on the filament's arms.
        for (int side : new int[] { leftPos + 7, leftPos + imageWidth - 12 }) {
            marblePlate(graphics, side, topPos + LIST_TOP + 5, 5, 10);
            marblePlate(graphics, side, topPos + LIST_TOP + rows * ROW_HEIGHT - 17, 5, 10);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFitted(graphics, title, 34, 13, imageWidth - 68, PALE);
        drawFitted(graphics, currentType(), 14, 34, imageWidth - 88, TEXT);
        drawFitted(graphics, Component.translatable("gui.appliedas.filament.status", menu.getStatusMessage()),
                14, 49, imageWidth - 88, MUTED);
        drawFitted(graphics, Component.translatable("gui.appliedas.filament.buffer", menu.getBufferedAmount()),
                14, 64, imageWidth - 88, MUTED);
        drawFitted(graphics, Component.translatable("gui.appliedas.filament.choose"),
                14, 80, imageWidth - 28, TEXT);
        var pageText = Component.translatable("gui.appliedas.filament.page", page + 1, pageCount());
        graphics.drawString(font, pageText, 61 - font.width(pageText) / 2, imageHeight - 22, TEXT, false);
    }

    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/block/" + name + ".png");
    }

    private static void tile(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy += 16) {
            for (int dx = 0; dx < width; dx += 16) {
                int w = Math.min(16, width - dx);
                int h = Math.min(16, height - dy);
                graphics.blit(texture, x + dx, y + dy, 0, 0, w, h, 16, 16);
            }
        }
    }

    private static void marblePlate(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF555452);
        tile(graphics, MARBLE, x + 1, y + 1, width - 2, height - 2);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFCFCFA);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, 0xFFF4F4F2);
        graphics.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF91918E);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, 0xFFA1A19C);
    }

    private static void woodFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF3C3020);
        tile(graphics, WOOD, x + 1, y + 1, width - 2, height - 2);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, GOLD);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 1, 0xFFAA884A);
        graphics.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, GOLD_SHADE);
    }

    private void drawFilament(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y + 14, x + 38, y + 27, 0xFF444443);
        graphics.fill(x + 14, y + 2, x + 27, y + 38, 0xFF444443);
        woodFrame(graphics, x, y + 12, 38, 14);
        woodFrame(graphics, x + 12, y, 14, 38);
        marblePlate(graphics, x, y + 15, 5, 8);
        marblePlate(graphics, x + 33, y + 15, 5, 8);
        marblePlate(graphics, x + 15, y, 8, 5);
        marblePlate(graphics, x + 15, y + 33, 8, 5);
        woodFrame(graphics, x + 7, y + 7, 24, 24);
        tile(graphics, SOOTY_MARBLE, x + 10, y + 10, 18, 18);
        graphics.renderOutline(x + 9, y + 9, 20, 20, GOLD_SHADE);
        var selected = menu.getSelectedLumen();
        if (selected != null) {
            AEKeyRendering.drawInGui(minecraft, graphics, x + 11, y + 11, LumenKey.of(selected));
        } else {
            star(graphics, x + 19, y + 19, 0xFFABA69A);
        }
    }

    private static void star(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 3, y, x + 4, y + 1, color);
        graphics.fill(x, y - 3, x + 1, y + 4, color);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        graphics.fill(x, y, x + 1, y + 1, PALE);
    }

    private final class AltarButton extends Button {
        private AltarButton(int x, int y, int width, Component label, OnPress action) {
            super(x, y, width, 20, label, action, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            woodFrame(graphics, getX(), getY(), width, height);
            tile(graphics, SOOTY_MARBLE, getX() + 3, getY() + 3, width - 6, height - 6);
            if (active && isHoveredOrFocused()) {
                graphics.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, 0x306A532A);
                graphics.renderOutline(getX() + 1, getY() + 1, width - 2, height - 2, GOLD);
            }
            if (!active) graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x80000000);
            int color = !active ? 0xFF86837A : isHoveredOrFocused() ? 0xFFFFE7A3 : PALE;
            renderScrollingString(graphics, font, 4, color);
        }
    }

    private Component currentType() {
        var selected = menu.getSelectedLumen();
        return Component.translatable("gui.appliedas.filament.current", selected == null
                ? Component.translatable("message.appliedas.filament.none") : selected.getHoverName());
    }

    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        }
        graphics.drawString(font, value, x, y, color, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && isHovering(8, LIST_TOP - 2, imageWidth - 16,
                rows * ROW_HEIGHT + 2, mouseX, mouseY)) {
            changePage(deltaY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private final class LumenButton extends Button {
        private final int row;
        private int lumenIndex;
        private LumenKey key;

        private LumenButton(int row) {
            super(leftPos + 14, topPos + LIST_TOP + row * ROW_HEIGHT, imageWidth - 28, ROW_HEIGHT - 2,
                    Component.empty(), button -> {}, DEFAULT_NARRATION);
            this.row = row;
        }

        private void updateLumen() {
            int index = page * rows + row;
            visible = index < menu.getAvailableLumen().size();
            active = visible;
            if (visible && (key == null || index != lumenIndex)) {
                lumenIndex = index;
                key = LumenKey.of(menu.getAvailableLumen().get(index));
                setMessage(key.getDisplayName());
                setTooltip(Tooltip.create(key.getDisplayName()));
            }
        }

        @Override
        public void onPress() {
            initialSelectionShown = true;
            sendSelection(lumenIndex + 1);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = key.lumen() == menu.getSelectedLumen();
            marblePlate(graphics, getX(), getY(), width, height);
            if (selected) {
                woodFrame(graphics, getX(), getY(), width, height);
                tile(graphics, SOOTY_MARBLE, getX() + 3, getY() + 3, width - 6, height - 6);
                graphics.renderOutline(getX(), getY(), width, height, GOLD);
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x408D7238);
                graphics.renderOutline(getX(), getY(), width, height, GOLD_SHADE);
            }
            tile(graphics, SOOTY_MARBLE, getX() + 3, getY() + 2, 20, 18);
            graphics.renderOutline(getX() + 2, getY() + 1, 22, 20, selected ? GOLD : 0xFF858077);
            AEKeyRendering.drawInGui(minecraft, graphics, getX() + 5, getY() + 3, key);
            drawFitted(graphics, key.getDisplayName(), getX() + 31, getY() + 7, width - 49,
                    selected ? 0xFFFFE7A3 : TEXT);
            if (selected) star(graphics, getX() + width - 11, getY() + 10, GOLD);
        }
    }
}
