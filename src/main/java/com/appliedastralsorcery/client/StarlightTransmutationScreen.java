package com.appliedastralsorcery.client;

import java.util.Locale;
import java.util.List;
import com.appliedastralsorcery.transmutation.TransmutationFilterSelection;
import com.appliedastralsorcery.transmutation.TransmutationMarkerAmount;
import com.appliedastralsorcery.transmutation.StarlightTransmutationBlockEntity.Status;
import com.appliedastralsorcery.transmutation.StarlightTransmutationMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import hellfirepvp.astralsorcery.client.util.RenderConstellationUtil;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** A gilded marble console with recessed trays and a view of the incoming constellation. */
public final class StarlightTransmutationScreen extends AbstractContainerScreen<StarlightTransmutationMenu> {
    public static final int RECIPE_X = 86, RECIPE_Y = 34, RECIPE_WIDTH = 92, RECIPE_HEIGHT = 78;
    // JEI's recipe hint stays above the pointer; place the constellation name below it.
    private static final ClientTooltipPositioner CONSTELLATION_TOOLTIP = (width, height, mouseX, mouseY, tipWidth, tipHeight) ->
            DefaultTooltipPositioner.INSTANCE.positionTooltip(width, height, mouseX, mouseY + 28, tipWidth, tipHeight);
    private Button autoPullButton;
    private Button overclockButton;
    private AmountEditor amountEditor;
    private boolean markerClick;
    private static final ResourceLocation MARBLE = material("marble_raw");
    private static final ResourceLocation WOOD = material("infused_wood");
    private static final ResourceLocation RUNE = material("marble_runed");
    private static final ResourceLocation SKY = ResourceLocation.fromNamespaceAndPath("astralsorcery",
            "textures/screen/tome/background_constellation.png");
    private static final int INK = 0xFF3B3933, MUTED = 0xFF77705F;
    private static final int LABEL_PAPER = 0xFFF1EBDD;
    private static final int GOLD = 0xFFD4BE76, GOLD_SHADE = 0xFF806630;
    private static final int DARK = 0xFF202B42, AQUA = 0xFF228CC1, LIGHT = 0xFF9BE7F5;

    public StarlightTransmutationScreen(StarlightTransmutationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 264;
        imageHeight = 280;
        titleLabelX = 21;
        titleLabelY = 13;
        inventoryLabelX = 51;
        inventoryLabelY = 185;
    }
    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath("astralsorcery", "textures/block/" + name + ".png");
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.appliedas.transmutation." + key, args); }

    @Override protected void init() {
        super.init();
        autoPullButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StarlightTransmutationMenu.AUTO_PULL_BUTTON);
        }).bounds(leftPos + 124, topPos + 139, 90, 14).build());
        autoPullButton.setTooltip(Tooltip.create(tr("auto_pull_hint")));
        overclockButton = addRenderableWidget(Button.builder(overclockLabel(), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StarlightTransmutationMenu.OVERCLOCK_BUTTON);
        }).bounds(leftPos + 124, topPos + 181, 90, 14).build());
        overclockButton.setTooltip(Tooltip.create(tr("overclock_hint")));
        if (amountEditor != null) amountEditor.init(minecraft, width, height);
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
        if (marker.isEmpty()) return;
        amountEditor = new AmountEditor(slot, marker.copy());
        amountEditor.init(minecraft, width, height);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        if (menu.isAutoPull() && hoveredSlot != null && hoveredSlot.index >= StarlightTransmutationMenu.MARKER_SLOT_START
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

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        plate(graphics, x, y, imageWidth, imageHeight);
        tile(graphics, WOOD, x + 12, y + 6, 240, 23);
        graphics.renderOutline(x + 12, y + 6, 240, 23, GOLD_SHADE);
        plate(graphics, x + 16, y + 9, 232, 17);
        graphics.fill(x + 20, y + 12, x + 232, y + 23, LABEL_PAPER);
        graphics.fill(x + 18, y + 24, x + 246, y + 25, GOLD);
        lamp(graphics, x + 236, y + 13, menu.isOnline() ? AQUA : MUTED);

        tray(graphics, x + 14, y + 34, 68, 78);
        tray(graphics, x + 182, y + 34, 68, 78);
        graphics.fill(x + 19, y + 38, x + 77, y + 50, LABEL_PAPER);
        graphics.fill(x + 187, y + 38, x + 245, y + 50, LABEL_PAPER);
        for (int slot = 0; slot < 18; slot++) {
            var position = menu.getSlot(slot);
            slot(graphics, x + position.x, y + position.y, slot >= 9);
        }

        drawObservatory(graphics, x + 86, y + 34);
        graphics.fill(x + 82, y + 71, x + 86, y + 73, GOLD_SHADE);
        graphics.fill(x + 178, y + 71, x + 182, y + 73, GOLD_SHADE);
        star(graphics, x + 83, y + 72, menu.hasStarlight() ? LIGHT : GOLD);
        star(graphics, x + 181, y + 72, GOLD);

        drawProgress(graphics, x, y);
        graphics.fill(x + 20, y + 126, x + 244, y + 137, LABEL_PAPER);
        tray(graphics, x + 44, y + 137, 176, 38);
        graphics.fill(x + 50, y + 141, x + 214, y + 154, LABEL_PAPER);
        for (int slot = 0; slot < 9; slot++) {
            var position = menu.getSlot(StarlightTransmutationMenu.MARKER_SLOT_START + slot);
            slot(graphics, x + position.x, y + position.y, false);
            graphics.renderOutline(x + position.x, y + position.y, 16, 16, menu.isAutoPull() ? AQUA : MUTED);
        }
        tray(graphics, x + 44, y + 179, 176, 96);
        graphics.fill(x + 50, y + 183, x + 214, y + 196, LABEL_PAPER);
        graphics.fill(x + 50, y + 250, x + 214, y + 251, GOLD_SHADE);
        graphics.fill(x + 50, y + 251, x + 214, y + 252, GOLD);
        for (int slot = 18; slot < StarlightTransmutationMenu.MARKER_SLOT_START; slot++) {
            var position = menu.getSlot(slot);
            slot(graphics, x + position.x, y + position.y, false);
        }
        // Carved marble and stars flank the inventory without encroaching on item hitboxes.
        for (int side : new int[]{16, 230}) {
            tile(graphics, RUNE, x + side, y + 205, 18, 44);
            graphics.fill(x + side, y + 205, x + side + 18, y + 249, 0x35FFF7DD);
            star(graphics, x + side + 9, y + 192, GOLD_SHADE);
            star(graphics, x + side + 9, y + 263, GOLD_SHADE);
        }
    }

    private void drawObservatory(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 92, y + 78, GOLD_SHADE);
        graphics.blit(SKY, x + 1, y + 1, 180, 112, 90, 76, 450, 300);
        graphics.renderOutline(x + 3, y + 3, 86, 72, 0xFF65778C);
        var constellation = menu.getConstellation();
        if (constellation != null) {
            // The native renderer draws immediately; submit the buffered sky first.
            graphics.flush();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            try {
                RenderConstellationUtil.drawConstellationUI(constellation.getConstellationColor(), constellation,
                        graphics.pose(), x + 16, y + 7, 56, 56, 1.5F, () -> 1F, true, false);
            } finally {
                RenderSystem.disableBlend();
            }
        }
    }

    private void drawProgress(GuiGraphics graphics, int x, int y) {
        int duration = menu.getDuration();
        int filled = duration <= 0 ? 0 : (int) (220L * menu.getProgress() / duration);
        graphics.fill(x + 20, y + 114, x + 244, y + 124, GOLD_SHADE);
        graphics.fill(x + 21, y + 115, x + 243, y + 123, DARK);
        graphics.fillGradient(x + 22, y + 116, x + 22 + Math.clamp(filled, 0, 220), y + 122, LIGHT, AQUA);
        if (filled > 0) graphics.fill(x + 21 + Math.clamp(filled, 0, 220), y + 116,
                x + 22 + Math.clamp(filled, 0, 220), y + 122, 0xFFF0FCFF);
        star(graphics, x + 15, y + 119, GOLD_SHADE);
        star(graphics, x + 249, y + 119, GOLD_SHADE);
    }

    private static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, GOLD_SHADE);
        tile(graphics, WOOD, x + 1, y + 1, width - 2, height - 2);
        tile(graphics, MARBLE, x + 4, y + 4, width - 8, height - 8);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x44FFF9E8);
        graphics.renderOutline(x + 3, y + 3, width - 6, height - 6, GOLD);
    }
    private static void tray(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, GOLD_SHADE);
        tile(graphics, WOOD, x + 1, y + 1, width - 2, height - 2);
        tile(graphics, MARBLE, x + 3, y + 3, width - 6, height - 6);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x56FFF9E8);
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, GOLD);
    }
    private static void tile(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        for (int row = 0; row < height; row += 16) {
            for (int col = 0; col < width; col += 16)
                graphics.blit(texture, x + col, y + row, 0, 0, Math.min(16, width - col), Math.min(16, height - row), 16, 16);
        }
    }
    private static void slot(GuiGraphics graphics, int x, int y, boolean output) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, output ? GOLD_SHADE : 0xFF9C927D);
        graphics.fill(x, y, x + 16, y + 16, 0xFFDEDACE);
        graphics.fill(x, y, x + 16, y + 1, 0xFF969083);
        graphics.fill(x, y + 1, x + 1, y + 16, 0xFFB7B1A3);
        graphics.fill(x + 1, y + 15, x + 16, y + 16, 0xFFF6F1E4);
    }
    private static void lamp(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 7, y + 7, GOLD_SHADE);
        graphics.fill(x + 1, y + 1, x + 6, y + 6, color);
        graphics.fill(x + 2, y + 1, x + 4, y + 2, 0xFFDDF4EF);
    }
    private static void star(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 2, y, x + 3, y + 1, color);
        graphics.fill(x, y - 2, x + 1, y + 3, color);
        graphics.fill(x, y, x + 1, y + 1, 0xFFF3F9EE);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, INK, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
        centeredLabel(graphics, tr("input"), 48, 39, INK);
        graphics.drawString(font, tr("markers"), 51, 143, INK, false);
        centeredLabel(graphics, tr("output"), 216, 39, INK);
        var status = tr("status." + menu.getStatus().name().toLowerCase(Locale.ROOT));
        centeredLabel(graphics, status, imageWidth / 2, 128,
                menu.getStatus() == Status.RUNNING ? 0xFF276D84 : INK);
    }
    private void centeredLabel(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        // Dark text on light marble needs crisp glyphs, without Minecraft's offset black shadow.
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }
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
        if (isHovering(233, 10, 13, 13, mouseX, mouseY))
            graphics.renderTooltip(font, tr(menu.isOnline() ? "network_online" : "network_offline"), mouseX, mouseY);
        if (isHovering(20, 114, 224, 10, mouseX, mouseY) && menu.getDuration() > 0)
            graphics.renderTooltip(font, tr("progress", menu.getProgress(), menu.getDuration()), mouseX, mouseY);
    }

    /** A modal child, without changing Minecraft's screen or closing the live container/cursor. */
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
            int x = (width - 208) / 2, y = (height - 142) / 2;
            input = addRenderableWidget(new EditBox(font, x + 16, y + 51, 176, 18, tr("amount_title")));
            input.setMaxLength(3);
            input.setFilter(text -> text.isEmpty() || text.matches("[0-9]{1,3}"));
            input.setValue(value);
            input.setResponder(text -> { value = text; confirm.active = validAmount(); });
            int[] changes = {-10, -1, 1, 10};
            for (int i = 0; i < changes.length; i++) {
                int change = changes[i];
                addRenderableWidget(Button.builder(Component.literal(change > 0 ? "+" + change : "" + change),
                        button -> adjust(change)).bounds(x + 16 + 45 * i, y + 76, 41, 18).build());
            }
            confirm = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                    .bounds(x + 16, y + 108, 85, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                    .bounds(x + 107, y + 108, 85, 20).build());
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
            int x = (width - 208) / 2, y = (height - 142) / 2;
            graphics.fill(0, 0, width, height, 0xB0000000);
            plate(graphics, x, y, 208, 142);
            graphics.drawString(font, title, x + 16, y + 12, INK, false);
            graphics.renderItem(item, x + 16, y + 29);
            graphics.drawString(font, tr("amount_range", maximum), x + 38, y + 33, INK, false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
