package com.appliedastralsorcery.client;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.chalice.MEChaliceMenu;
import com.appliedastralsorcery.chalice.ChaliceFluidMarker;
import com.appliedastralsorcery.chalice.ChaliceFluidSelection;
import appeng.api.stacks.AEFluidKey;
import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.glfw.GLFW;

/** A marble chalice with a suspended fluid cube and two ME fittings. */
public final class MEChaliceScreen extends AbstractContainerScreen<MEChaliceMenu> {
    private static final int CAPACITY = 64000;
    private static final int SIDE_MARGIN = 12;
    private static final int CONTENT_WIDTH = 264 - 2 * SIDE_MARGIN;
    private static final int BUTTON_HEIGHT = 18;
    private static final int INK = 0xFF3B3933;
    private static final int MUTED = 0xFF696356;
    private static final int GOLD = 0xFFD4BE76;
    private static final int GOLD_SHADE = 0xFF806630;
    private static final int AQUA = 0xFF228CC1;
    private static final int EDGE = 0xFF9D9073;
    private static final ResourceLocation MARBLE = material("marble_raw");
    private static final ResourceLocation WOOD = material("infused_wood");
    private static final ResourceLocation AQUAMARINE = ResourceLocation.fromNamespaceAndPath(
            "astralsorcery", "textures/item/aquamarine.png");
    private EditBox target;
    private final ReserveMarkerDrag reserveDrag = new ReserveMarkerDrag();
    private int lastTarget = -1;
    private ChaliceButton apply, export, pull, clear;

    public MEChaliceScreen(MEChaliceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 264;
        imageHeight = 238;
    }

    private Component tr(String key, Object... args) {
        return Component.translatable("gui.appliedas.chalice." + key, args);
    }

    @Override protected void init() {
        reserveDrag.cancel();
        super.init();
        target = new EditBox(font, leftPos + 112, topPos + 95, 54, 12, tr("target"));
        target.setBordered(false);
        target.setTextColor(0xFFF6EDD8);
        target.setMaxLength(5);
        target.setFilter(s -> s.matches("[0-9]{0,5}"));
        target.setValue(Integer.toString(menu.getPullTarget()));
        target.setTooltip(Tooltip.create(tr("target_hint")));
        addRenderableWidget(target);
        lastTarget = menu.getPullTarget();
        apply = addButton(180, 89, 68, tr("apply"), -1, b -> applyTarget());
        apply.setTooltip(Tooltip.create(tr("apply_hint")));
        export = addButton(SIDE_MARGIN, 110, 116, tr("export"), 4, b -> send(1));
        pull = addButton(136, 110, 116, tr("pull"), 5, b -> send(2));
        clear = addButton(SIDE_MARGIN, 132, CONTENT_WIDTH, tr("clear"), -1, b -> setMarker(null));
        export.setTooltip(Tooltip.create(tr("export_hint")));
        pull.setTooltip(Tooltip.create(tr("pull_hint")));
        clear.setTooltip(Tooltip.create(tr("clear_hint")));
    }

    private ChaliceButton addButton(int x, int y, int width, Component label, int dataIndex, Button.OnPress press) {
        return addRenderableWidget(new ChaliceButton(leftPos + x, topPos + y, width, label, dataIndex, press));
    }

    private void applyTarget() {
        if (target.getValue().isEmpty()) return;
        int amount = Math.clamp(Integer.parseInt(target.getValue()), 0, CAPACITY);
        applyAmount(amount);
    }

    private void applyAmount(int amount) {
        menu.setData(1, amount);
        send(MEChaliceMenu.TARGET_BUTTON_BASE + amount);
        target.setValue(Integer.toString(amount));
        target.setFocused(false);
        lastTarget = amount;
    }

    public int getDisplayedPullTarget() { return reserveDrag.displayed(menu.getPullTarget()); }

    public List<Rect2i> getJeiDropAreas() {
        return List.of(new Rect2i(leftPos + 80, topPos + 30, 168, 20),
                new Rect2i(leftPos + SIDE_MARGIN, topPos + 31, 56, 76));
    }

    public void acceptJeiFluid(AEFluidKey fluid) {
        if (fluid != null && menu.getTypes().contains(fluid.getFluid())) setMarker(fluid);
    }

    private void setMarker(AEFluidKey key) {
        menu.setClientSelection(key);
        if (minecraft != null && minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new ChaliceFluidSelection(menu.containerId,
                    key == null ? FluidStack.EMPTY : key.toStack(1)));
    }

    private boolean clickMarker(double x, double y, int button) {
        if ((button != 0 && button != 1) || !MarkerAreas.contains(getJeiDropAreas(), x, y)) return false;
        var carried = menu.getCarried();
        var key = ChaliceFluidMarker.carried(carried, button);
        if (carried.isEmpty() || key != null && menu.getTypes().contains(key.getFluid())) {
            menu.setClientSelection(key);
            send(button == 1 ? MEChaliceMenu.MARKER_RIGHT : MEChaliceMenu.MARKER_LEFT);
        }
        return true;
    }

    private void updateReserveDrag(double mouseX) {
        reserveDrag.update(mouseX, leftPos + 81, 166, CAPACITY);
        target.setValue(Integer.toString(getDisplayedPullTarget()));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickMarker(mouseX, mouseY, button)) return true;
        if (button == 0 && isHovering(80, 60, 168, 13, mouseX, mouseY)) {
            target.setFocused(false);
            reserveDrag.begin(mouseX, leftPos + 81, 166, CAPACITY);
            updateReserveDrag(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && reserveDrag.isDragging()) {
            updateReserveDrag(mouseX);
            return true;
        }
        if (!menu.getCarried().isEmpty() && clickMarker(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (reserveDrag.isDragging()) {
            if (button == 0) {
                updateReserveDrag(mouseX);
                applyAmount(reserveDrag.finish());
            }
            return true;
        }
        if ((button == 0 || button == 1) && MarkerAreas.contains(getJeiDropAreas(), mouseX, mouseY)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (reserveDrag.isDragging()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                reserveDrag.cancel();
                target.setValue(Integer.toString(menu.getPullTarget()));
            }
            return true;
        }
        if (target.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            applyTarget();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        if (!reserveDrag.isDragging() && lastTarget != menu.getPullTarget() && !target.isFocused()) {
            target.setValue(Integer.toString(menu.getPullTarget()));
            lastTarget = menu.getPullTarget();
        }
        apply.active = !reserveDrag.isDragging() && !target.getValue().isEmpty();
        clear.active = menu.getSelectedFluid() != null;
        export.updateMessage();
        pull.updateMessage();
        super.render(g, mx, my, tick);
        renderTooltip(g, mx, my);
        if (MarkerAreas.contains(getJeiDropAreas(), mx, my)) {
            g.renderComponentTooltip(font, List.of(selectedName(), tr("filter_hint")), mx, my);
        } else if (isHovering(80, 60, 168, 13, mx, my)) {
            g.renderTooltip(font, tr("marker_hint"), mx, my);
        } else if (isHovering(80, 52, 168, 8, mx, my)) {
            g.renderTooltip(font, tr("fluid_hint", menu.getStoredAmount(), getDisplayedPullTarget()), mx, my);
        } else if (isHovering(80, 72, 168, 16, mx, my)) {
            g.renderTooltip(font, storedName(), mx, my);
        } else if (isHovering(188, 196, 64, 38, mx, my)) {
            g.renderTooltip(font, tr(menu.value(7) == 1 ? "switch_hint" : "target_hint"), mx, my);
        } else if (isHovering(188, 154, 64, 38, mx, my)) {
            g.renderTooltip(font, tr(menu.value(6) == 1 ? "network_online" : "network_offline"), mx, my);
        }
    }

    private FluidStack selectedStack() {
        var fluid = menu.getSelectedKey();
        return fluid == null ? FluidStack.EMPTY : fluid.toStack(1000);
    }

    private Component selectedName() {
        FluidStack selected = selectedStack();
        return selected.isEmpty() ? tr("choose") : selected.getHoverName();
    }

    private Component storedName() {
        FluidStack stored = menu.getStoredFluid();
        return tr("active", stored.isEmpty() ? tr("empty") : stored.getHoverName());
    }

    private int networkColor() {
        return menu.value(6) == 1 ? AQUA : 0xFF9C8D81;
    }

    private int fluidColor(FluidStack fluid) {
        return fluid.isEmpty() ? 0xFF8CB6BF : IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        plate(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 5, topPos + 5, leftPos + 259, topPos + 233, 0x24FFF8E5);
        tile(g, WOOD, leftPos + SIDE_MARGIN, topPos + 6, CONTENT_WIDTH, 22);
        g.renderOutline(leftPos + SIDE_MARGIN, topPos + 6, CONTENT_WIDTH, 22, GOLD_SHADE);
        plate(g, leftPos + SIDE_MARGIN + 4, topPos + 9, CONTENT_WIDTH - 8, 16);
        g.fill(leftPos + SIDE_MARGIN + 5, topPos + 10, leftPos + SIDE_MARGIN + CONTENT_WIDTH - 5, topPos + 24, 0x40FFF9EC);
        g.fill(leftPos + SIDE_MARGIN + 5, topPos + 24, leftPos + SIDE_MARGIN + CONTENT_WIDTH - 5, topPos + 25, GOLD);
        g.renderItem(ModContent.ME_CHALICE_ITEM.toStack(), leftPos + SIDE_MARGIN + 5, topPos + 9);
        g.blit(AQUAMARINE, leftPos + 224, topPos + 9, 0, 0, 16, 16, 16, 16);

        drawChalice(g, tick);
        inset(g, leftPos + 76, topPos + 30, SIDE_MARGIN + CONTENT_WIDTH - 76, 78);
        g.fill(leftPos + 80, topPos + 31, leftPos + 248, topPos + 49, 0x508BD2DD);
        g.fill(leftPos + 82, topPos + 49, leftPos + 246, topPos + 50, GOLD_SHADE);
        drawFluid(g, selectedStack(), leftPos + 83, topPos + 32, 16, 16);
        drawTankBar(g);
        inset(g, leftPos + 108, topPos + 91, 65, 16);
        tile(g, WOOD, leftPos + 111, topPos + 93, 59, 12);
        g.fill(leftPos + 111, topPos + 93, leftPos + 170, topPos + 105, 0xA033291B);
        if (target.isFocused()) g.renderOutline(leftPos + 108, topPos + 91, 65, 16, AQUA);

        inset(g, leftPos + SIDE_MARGIN, topPos + 154, 171, 80);
        g.fill(leftPos + 18, topPos + 211, leftPos + 178, topPos + 212, GOLD_SHADE);
        g.fill(leftPos + 18, topPos + 212, leftPos + 178, topPos + 213, GOLD);
        for (var slot : menu.slots) drawSlot(g, leftPos + slot.x, topPos + slot.y);
        inset(g, leftPos + 188, topPos + 154, 64, 38);
        g.fill(leftPos + 193, topPos + 169, leftPos + 247, topPos + 170, EDGE);
        lamp(g, leftPos + 195, topPos + 178, networkColor());
        inset(g, leftPos + 188, topPos + 196, 64, 38);
        g.fill(leftPos + 193, topPos + 211, leftPos + 247, topPos + 212, EDGE);
        if (menu.value(7) == 1) g.fill(leftPos + 188, topPos + 197, leftPos + 190, topPos + 233, GOLD_SHADE);
    }

    private void drawChalice(GuiGraphics g, float partialTick) {
        int x = leftPos, y = topPos;
        inset(g, x + SIDE_MARGIN, y + 31, 56, 76);
        g.fillGradient(x + 16, y + 35, x + 64, y + 103, 0x60455965, 0x183B6670);
        AstralMachinePreview.chalice(g, x + 40, y + 78, menu.getStoredFluid(), menu.getStoredAmount(),
                CAPACITY, partialTick);
    }

    private void drawFluid(GuiGraphics g, FluidStack fluid, int x, int y, int width, int height) {
        if (fluid.isEmpty() || minecraft == null || width <= 0 || height <= 0) return;
        var properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(properties.getStillTexture(fluid));
        int tint = properties.getTintColor(fluid);
        // Scissors use screen coordinates while sprites follow the pose (e.g. an embedded preview).
        var matrix = g.pose().last().pose();
        var from = new org.joml.Vector3f(x, y, 0).mulPosition(matrix);
        var to = new org.joml.Vector3f(x + width, y + height, 0).mulPosition(matrix);
        g.enableScissor((int) Math.floor(from.x()), (int) Math.floor(from.y()),
                (int) Math.ceil(to.x()), (int) Math.ceil(to.y()));
        for (int dy = 0; dy < height; dy += 16) for (int dx = 0; dx < width; dx += 16)
            g.blit(x + dx, y + dy, 0, 16, 16, sprite, ((tint >> 16) & 255) / 255F,
                    ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, ((tint >>> 24) & 255) / 255F);
        g.disableScissor();
    }

    private void drawTankBar(GuiGraphics g) {
        int x = leftPos + 80, y = topPos + 64;
        g.fill(x, y, x + 168, y + 6, 0xFF777568);
        g.fill(x + 1, y + 1, x + 167, y + 5, 0xFFACA99A);
        int fill = 166 * Math.clamp(menu.getStoredAmount(), 0, CAPACITY) / CAPACITY;
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 5, fluidColor(menu.getStoredFluid()));
            drawFluid(g, menu.getStoredFluid(), x + 1, y + 1, fill, 4);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x66FFFFFF);
        }
        int marker = x + 1 + 165 * Math.clamp(getDisplayedPullTarget(), 0, CAPACITY) / CAPACITY;
        g.fill(marker - 1, y - 1, marker + 2, y + 7, GOLD_SHADE);
        g.fill(marker, y - 1, marker + 1, y + 6, GOLD);
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        fitted(g, title, SIDE_MARGIN + 25, 13, 174, INK);
        fitted(g, selectedName(), 103, 36, 142, INK);
        fitted(g, tr("stored", menu.getStoredAmount()), 80, 53, 168, INK);
        fitted(g, storedName(), 80, 75, 168, MUTED);
        fitted(g, tr("target"), 80, 95, 27, MUTED);
        fitted(g, tr("network"), 194, 160, 53, MUTED);
        fitted(g, tr(menu.value(6) == 1 ? "online" : "offline"), 204, 176, 43, INK);
        fitted(g, tr(menu.value(7) == 1 ? "switching" : "reserve"), 194, 202, 53, menu.value(7) == 1 ? GOLD_SHADE : MUTED);
        g.drawString(font, Integer.toString(getDisplayedPullTarget()), 194, 218, INK, false);
        g.drawString(font, "mB", 234, 218, MUTED, false);
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

    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/block/" + name + ".png");
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

    private static void inset(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF66533A);
        tile(g, WOOD, x + 1, y + 1, width - 2, height - 2);
        tile(g, MARBLE, x + 3, y + 2, width - 6, height - 4);
        g.fill(x + 3, y + 2, x + width - 3, y + height - 2, 0x50FFF9E9);
        g.fill(x + 2, y + 1, x + width - 2, y + 2, GOLD);
        g.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, GOLD_SHADE);
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFFF6F1E4);
        g.fill(x - 1, y - 1, x + 17, y, 0xFF625F54);
        g.fill(x - 1, y, x, y + 16, 0xFF625F54);
        g.fill(x, y, x + 16, y + 16, 0xFF97978F);
    }

    private static void lamp(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y - 1, x + 3, y + 5, GOLD_SHADE);
        g.fill(x - 1, y + 1, x + 5, y + 3, GOLD_SHADE);
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x, y, x + 3, y + 1, 0xBBE7FFFF);
        g.fill(x + 3, y + 1, x + 4, y + 4, 0x550D3844);
    }

    private final class ChaliceButton extends Button {
        private final Component label;
        private final int dataIndex;

        private ChaliceButton(int x, int y, int width, Component label, int dataIndex, OnPress press) {
            super(x, y, width, BUTTON_HEIGHT, label, press, DEFAULT_NARRATION);
            this.label = label;
            this.dataIndex = dataIndex;
        }

        private void updateMessage() {
            setMessage(tr("toggle", label, tr(menu.value(dataIndex) == 1 ? "on" : "off")));
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int textY = y + (h - font.lineHeight) / 2;
            boolean hover = active && isHoveredOrFocused();
            int border = hover ? AQUA : GOLD_SHADE;
            // Keep the frame, shadow and hit box within the same bounds.
            g.fill(x + 1, y + 1, x + w - 1, y + h, 0xFF98907C);
            g.fill(x, y + 1, x + w, y + h - 2, border);
            g.fill(x + 1, y, x + w - 1, y + h - 1, border);
            tile(g, WOOD, x + 1, y + 1, w - 2, h - 3);
            tile(g, MARBLE, x + 3, y + 2, w - 6, h - 5);
            g.fill(x + 3, y + 2, x + w - 3, y + h - 3, hover ? 0x605FCBDC : 0x58FFF9E8);
            g.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFF2BF);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, GOLD);
            if (dataIndex >= 0) {
                boolean on = menu.value(dataIndex) == 1;
                Component state = tr(on ? "on" : "off");
                int pillWidth = Math.min(w - 28,
                        Math.max(29, Math.max(font.width(tr("on")), font.width(tr("off"))) + 14));
                int pillX = x + w - pillWidth - 5;
                g.fill(pillX, y + 3, x + w - 5, y + h - 3, on ? 0xFFD1E6E1 : 0xFFDED9CA);
                g.fill(pillX, y + h - 4, x + w - 5, y + h - 3, on ? AQUA : EDGE);
                lamp(g, pillX + 3, y + (h - 4) / 2, on ? AQUA : 0xFF9C9685);
                fitted(g, state, pillX + 10, textY, pillWidth - 14, on ? INK : MUTED);
                fitted(g, label, x + 7, textY, w - pillWidth - 18, INK);
            } else {
                String text = font.plainSubstrByWidth(label.getString(), w - 6);
                g.drawString(font, text, x + (w - font.width(text)) / 2, textY, active ? INK : 0xFF91897A, false);
            }
        }
    }
}
