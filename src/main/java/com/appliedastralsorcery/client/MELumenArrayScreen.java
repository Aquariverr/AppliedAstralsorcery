package com.appliedastralsorcery.client;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.appliedastralsorcery.ModContent;
import com.appliedastralsorcery.integration.jei.GhostIngredientResolver;
import java.util.List;
import com.appliedastralsorcery.lumen.LumenKey;
import com.appliedastralsorcery.lumen.MELumenArrayMenu;
import hellfirepvp.astralsorcery.client.ClientProxy;
import hellfirepvp.astralsorcery.client.lib.TexturesAS;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MELumenArrayScreen extends AbstractContainerScreen<MELumenArrayMenu> {
    private static final int SIDE_MARGIN = 12;
    private static final int CONTENT_WIDTH = 264 - 2 * SIDE_MARGIN;
    private static final int BUTTON_HEIGHT = 18;
    private static final int INK = 0xFF3B3933;
    private static final int MUTED = 0xFF696356;
    private static final int GOLD = 0xFFD4BE76;
    private static final int GOLD_SHADE = 0xFF806630;
    private static final int AQUA = 0xFF228CC1;
    private static final int AQUA_LIGHT = 0xFF7AD6E8;
    private static final int EDGE = 0xFF9D9073;
    private static final ResourceLocation MARBLE = material("marble_raw");
    private static final ResourceLocation WOOD = material("infused_wood");
    private static final ResourceLocation AQUAMARINE = ResourceLocation.fromNamespaceAndPath(
            "astralsorcery", "textures/item/aquamarine.png");
    private EditBox target;
    private final ReserveMarkerDrag reserveDrag = new ReserveMarkerDrag();
    private int lastTarget = -1;
    private ItemStack catalystMarker = ItemStack.EMPTY;
    private ArrayButton apply, previous, next, export, supply, filaments, redstone;

    public MELumenArrayScreen(MELumenArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 264;
        imageHeight = 238;
    }

    private Component tr(String key, Object... args) {
        if (menu.isAlchemyArray() && key.equals("network_offline"))
            return Component.translatable("gui.appliedas.alchemy_array." + key, args);
        return Component.translatable("gui.appliedas.array." + key, args);
    }

    @Override protected void init() {
        reserveDrag.cancel();
        super.init();
        target = new EditBox(font, leftPos + 112, topPos + 95, 48, 12, tr("target"));
        target.setBordered(false);
        target.setTextColor(0xFFF6EDD8);
        target.setMaxLength(4);
        target.setFilter(s -> s.matches("[0-9]{0,4}"));
        target.setValue(Integer.toString(menu.value(1)));
        target.setTooltip(Tooltip.create(tr("target_hint")));
        addRenderableWidget(target);
        lastTarget = menu.value(1);
        apply = addButton(180, 89, 68, tr("apply"), -1, b -> applyTarget());
        apply.setTooltip(Tooltip.create(tr("apply_hint")));
        previous = addButton(80, 30, 20, Component.literal("<"), -1, b -> select(-1));
        next = addButton(228, 30, 20, Component.literal(">"), -1, b -> select(1));
        previous.setTooltip(Tooltip.create(tr("previous")));
        next.setTooltip(Tooltip.create(tr("next")));
        export = addButton(SIDE_MARGIN, 110, 116, tr("export"), 4, b -> send(1));
        supply = addButton(136, 110, 116, tr("supply"), 5, b -> send(2));
        filaments = addButton(SIDE_MARGIN, 132, 116, tr("filaments"), 6, b -> send(3));
        redstone = addButton(136, 132, 116, tr("redstone"), MELumenArrayMenu.REDSTONE_DATA,
                b -> send(MELumenArrayMenu.REDSTONE_BUTTON));
        export.setTooltip(Tooltip.create(tr("export_hint")));
        supply.setTooltip(Tooltip.create(tr("supply_hint")));
        filaments.setTooltip(Tooltip.create(tr("filaments_hint")));
        redstone.setTooltip(Tooltip.create(tr("redstone_hint")));
    }

    private ArrayButton addButton(int x, int y, int width, Component label, int dataIndex, Button.OnPress press) {
        return addRenderableWidget(new ArrayButton(leftPos + x, topPos + y, width, label, dataIndex, press));
    }

    private void applyTarget() {
        if (target.getValue().isEmpty()) return;
        int amount = Math.clamp(Integer.parseInt(target.getValue()), 0, 4000);
        applyAmount(amount);
    }

    private void applyAmount(int amount) {
        menu.setData(1, amount);
        send(10000 + amount);
        target.setValue(Integer.toString(amount));
        target.setFocused(false);
        lastTarget = amount;
    }

    public int getDisplayedPullTarget() { return reserveDrag.displayed(menu.value(1)); }

    public List<Rect2i> getJeiDropAreas() {
        return List.of(new Rect2i(leftPos + 102, topPos + 30, 124, 20),
                new Rect2i(leftPos + 19, topPos + 84, 18, 18));
    }

    private boolean clickMarker(double x, double y, int button) {
        if ((button != 0 && button != 1) || !MarkerAreas.contains(getJeiDropAreas(), x, y)) return false;
        var lumen = GhostIngredientResolver.resolveLumen(menu.getCarried());
        if (lumen != null) acceptJeiLumen(lumen);
        return true;
    }

    public void acceptJeiLumen(Lumen lumen) {
        int index = menu.getTypes().indexOf(lumen);
        if (index < 0) return;
        menu.setData(0, index + 1);
        send(100 + index);
    }

    private void updateReserveDrag(double mouseX) {
        reserveDrag.update(mouseX, leftPos + 81, 166, 4000);
        target.setValue(Integer.toString(getDisplayedPullTarget()));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickMarker(mouseX, mouseY, button)) return true;
        if (button == 0 && isHovering(80, 60, 168, 13, mouseX, mouseY)) {
            target.setFocused(false);
            reserveDrag.begin(mouseX, leftPos + 81, 166, 4000);
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

    private void select(int direction) {
        if (menu.getTypes().isEmpty()) return;
        int current = menu.getTypes().indexOf(menu.getSelectedLumen());
        acceptJeiLumen(menu.getTypes().get(Math.floorMod(current + direction, menu.getTypes().size())));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (reserveDrag.isDragging()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                reserveDrag.cancel();
                target.setValue(Integer.toString(menu.value(1)));
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
        updateCatalystMarker();
        if (!reserveDrag.isDragging() && lastTarget != menu.value(1) && !target.isFocused()) {
            target.setValue(Integer.toString(menu.value(1)));
            lastTarget = menu.value(1);
        }
        apply.active = !reserveDrag.isDragging() && !target.getValue().isEmpty();
        previous.active = next.active = !menu.getTypes().isEmpty();
        export.updateMessage();
        supply.updateMessage();
        filaments.updateMessage();
        redstone.updateMessage();
        super.render(g, mx, my, tick);
        renderTooltip(g, mx, my);
        if (isHovering(102, 30, 124, 20, mx, my)) {
            g.renderTooltip(font, selectedName(), mx, my);
        } else if (isHovering(19, 84, 18, 18, mx, my)) {
            var activeLumen = menu.getActiveLumen();
            g.renderTooltip(font, activeLumen == null ? selectedName() :
                    tr("active", LumenKey.of(activeLumen).getDisplayName()), mx, my);
        } else if (isHovering(44, 84, 18, 18, mx, my) && !menu.getSlot(0).hasItem()) {
            if (!catalystMarker.isEmpty()) {
                g.renderComponentTooltip(font, List.of(tr("catalyst_for", selectedName()),
                        catalystMarker.getHoverName(), tr("catalyst_marker_hint")), mx, my);
            } else {
                g.renderTooltip(font, tr(menu.getSelectedLumen() == null ? "catalyst_hint" : "catalyst_missing"), mx, my);
            }
        } else if (isHovering(80, 60, 168, 13, mx, my)) {
            g.renderTooltip(font, tr("marker_hint"), mx, my);
        } else if (isHovering(80, 52, 168, 8, mx, my)) {
            g.renderTooltip(font, tr("lumen_hint", menu.value(2), getDisplayedPullTarget()), mx, my);
        } else if (isHovering(80, 72, 168, 16, mx, my)) {
            g.renderTooltip(font, tr("starlight", menu.value(3)), mx, my);
        } else if (isHovering(188, 195, 64, 39, mx, my)) {
            g.renderTooltip(font, menu.value(9) == 1 ? tr("switch_hint") : tr("target_hint"), mx, my);
        } else if (isHovering(188, 154, 64, 38, mx, my)) {
            g.renderTooltip(font, tr(menu.value(7) == 1 ? "network_online" : "network_offline"), mx, my);
        }
    }

    private Component selectedName() {
        var selected = menu.getSelectedLumen();
        return selected == null ? tr("choose") : LumenKey.of(selected).getDisplayName();
    }

    private void updateCatalystMarker() {
        catalystMarker = ItemStack.EMPTY;
        Lumen selected = menu.getSelectedLumen();
        if (selected == null || minecraft == null || minecraft.level == null || menu.getSlot(0).hasItem()) return;
        // Follow the selected recipe even while the array is draining its previous lumen.
        minecraft.level.getRecipeManager().getAllRecipesFor(
                hellfirepvp.astralsorcery.common.lib.RecipeTypesAS.LUMEN_GENERATION_TYPE.get()).stream()
                .filter(recipe -> recipe.value().getProducedLumen() == selected
                        && menu.isAlchemyArray() != recipe.value().getLumenCombinationInputs().isEmpty())
                .findFirst().ifPresent(recipe -> {
            ItemStack[] alternatives = recipe.value().getInput().getItems();
            if (alternatives.length > 0) {
                catalystMarker = alternatives[(int) ((Util.getMillis() / 1000L) % alternatives.length)].copyWithCount(1);
            }
        });
    }

    @Nullable private Lumen displayedLumen() {
        var activeLumen = menu.getActiveLumen();
        return activeLumen == null ? menu.getSelectedLumen() : activeLumen;
    }

    private int lumenColor() {
        var lumen = displayedLumen();
        return lumen == null ? 0xFFB4A8DA : 0xFF000000 | lumen.getColor(ClientProxy.getClientTick()).getColor();
    }

    private int networkColor() {
        return menu.value(7) == 1 ? AQUA : 0xFF9C8D81;
    }

    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        plate(g, leftPos, topPos, imageWidth, imageHeight);
        g.fill(leftPos + 5, topPos + 5, leftPos + 259, topPos + 233, 0x24FFF8E5);
        tile(g, WOOD, leftPos + SIDE_MARGIN, topPos + 6, CONTENT_WIDTH, 22);
        g.renderOutline(leftPos + SIDE_MARGIN, topPos + 6, CONTENT_WIDTH, 22, GOLD_SHADE);
        plate(g, leftPos + SIDE_MARGIN + 4, topPos + 9, CONTENT_WIDTH - 8, 16);
        g.fill(leftPos + SIDE_MARGIN + 5, topPos + 10, leftPos + SIDE_MARGIN + CONTENT_WIDTH - 5, topPos + 24, 0x40FFF9EC);
        g.fill(leftPos + SIDE_MARGIN + 5, topPos + 24, leftPos + SIDE_MARGIN + CONTENT_WIDTH - 5, topPos + 25, GOLD);
        g.renderItem((menu.isAlchemyArray() ? ModContent.ME_LUMEN_ALCHEMY_ARRAY_ITEM : ModContent.ME_LUMEN_ARRAY_ITEM).toStack(),
                leftPos + SIDE_MARGIN + 5, topPos + 9);
        g.blit(AQUAMARINE, leftPos + 224, topPos + 9, 0, 0, 16, 16, 16, 16);

        drawBasin(g);
        inset(g, leftPos + 76, topPos + 30, SIDE_MARGIN + CONTENT_WIDTH - 76, 78);
        g.fill(leftPos + 102, topPos + 33, leftPos + 226, topPos + 48, 0x508BD2DD);
        g.fill(leftPos + 104, topPos + 49, leftPos + 224, topPos + 50, GOLD_SHADE);
        bar(g, 80, 64, 168, menu.value(2), 4000, lumenColor());
        int marker = 81 + 165 * Math.clamp(getDisplayedPullTarget(), 0, 4000) / 4000;
        g.fill(leftPos + marker - 1, topPos + 63, leftPos + marker + 2, topPos + 70, GOLD_SHADE);
        g.fill(leftPos + marker, topPos + 63, leftPos + marker + 1, topPos + 69, GOLD);
        bar(g, 80, 82, 168, menu.value(3), 2000, AQUA_LIGHT);
        inset(g, leftPos + 108, topPos + 91, 65, 16);
        tile(g, WOOD, leftPos + 111, topPos + 93, 59, 12);
        g.fill(leftPos + 111, topPos + 93, leftPos + 170, topPos + 105, 0xA033291B);
        if (target.isFocused()) g.renderOutline(leftPos + 108, topPos + 91, 65, 16, AQUA);

        inset(g, leftPos + SIDE_MARGIN, topPos + 154, 171, 80);
        g.fill(leftPos + 18, topPos + 211, leftPos + 178, topPos + 212, GOLD_SHADE);
        g.fill(leftPos + 18, topPos + 212, leftPos + 178, topPos + 213, GOLD);
        for (var slot : menu.slots) {
            if (slot.index != 0) slot(g, leftPos + slot.x, topPos + slot.y, false);
        }
        inset(g, leftPos + 188, topPos + 154, 64, 38);
        g.fill(leftPos + 193, topPos + 169, leftPos + 247, topPos + 170, EDGE);
        lamp(g, leftPos + 195, topPos + 178, networkColor());
        inset(g, leftPos + 188, topPos + 196, 64, 38);
        g.fill(leftPos + 193, topPos + 211, leftPos + 247, topPos + 212, EDGE);
        if (menu.value(9) == 1) g.fill(leftPos + 188, topPos + 197, leftPos + 190, topPos + 233, GOLD_SHADE);
    }

    private void drawBasin(GuiGraphics g) {
        int x = leftPos, y = topPos;
        inset(g, x + SIDE_MARGIN, y + 31, 56, 76);
        g.fillGradient(x + 16, y + 35, x + 64, y + 81, 0x60455965, 0x183B6670);
        AstralMachinePreview.array(g, x + 40, y + 57, menu.value(3), menu.isAlchemyArray());
        g.fill(x + 19, y + 84, x + 37, y + 102, 0xFF304A53);
        g.renderOutline(x + 19, y + 84, 18, 18, GOLD);
        var lumen = displayedLumen();
        if (lumen != null && minecraft != null) {
            var sprite = minecraft.getModelManager().getAtlas(TexturesAS.ATLAS_LUMEN)
                    .getSprite(lumen.getRegistryKey().orElseThrow().location());
            int color = lumenColor();
            g.blit(x + 20, y + 85, 0, 16, 16, sprite,
                    ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                    (color & 255) / 255F, 1F);
        }
        slot(g, x + menu.getSlot(0).x, y + menu.getSlot(0).y, true);
        if (!catalystMarker.isEmpty() && !menu.getSlot(0).hasItem()) {
            int slotX = x + menu.getSlot(0).x, slotY = y + menu.getSlot(0).y;
            g.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF628A88);
            g.renderFakeItem(catalystMarker, slotX, slotY);
            g.fill(slotX, slotY, slotX + 16, slotY + 16, 200, 0x60628A88);
        }
    }

    private void bar(GuiGraphics g, int x, int y, int width, int value, int max, int color) {
        x += leftPos;
        y += topPos;
        g.fill(x, y, x + width, y + 5, 0xFF777568);
        g.fill(x + 1, y + 1, x + width - 1, y + 4, 0xFFACA99A);
        g.fill(x + 1, y + 4, x + width - 1, y + 5, 0xFFF9F3DF);
        int fill = (width - 2) * Math.clamp(value, 0, max) / max;
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 4, color);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x66FFFFFF);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        fitted(g, title, SIDE_MARGIN + 25, 13, 174, INK);
        fitted(g, selectedName(), 105, 36, 118, INK);
        fitted(g, tr("stored", menu.value(2)), 80, 53, 168, INK);
        fitted(g, tr("starlight", menu.value(3)), 80, 72, 168, MUTED);
        fitted(g, tr("target"), 80, 95, 27, MUTED);
        g.drawString(font, tr("network"), 194, 160, MUTED, false);
        fitted(g, tr(menu.value(7) == 1 ? "online" : "offline"), 204, 176, 43, INK);
        fitted(g, tr(menu.value(9) == 1 ? "switching" : "reserve"), 194, 202, 53, menu.value(9) == 1 ? GOLD_SHADE : MUTED);
        g.drawString(font, Integer.toString(getDisplayedPullTarget()), 194, 218, INK, false);
        g.drawString(font, "Lm", 234, 218, MUTED, false);
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

    private static void slot(GuiGraphics g, int x, int y, boolean catalyst) {
        g.fill(x - 1, y - 1, x + 17, y + 17, catalyst ? GOLD : 0xFFF6F1E4);
        g.fill(x - 1, y - 1, x + 17, y, catalyst ? GOLD_SHADE : 0xFF625F54);
        g.fill(x - 1, y, x, y + 16, catalyst ? GOLD_SHADE : 0xFF625F54);
        g.fill(x, y, x + 16, y + 16, catalyst ? 0xFF628A88 : 0xFF97978F);
        if (catalyst) {
            g.fill(x + 6, y + 4, x + 10, y + 12, 0xFFA8C7BD);
            g.fill(x + 4, y + 6, x + 12, y + 10, 0xFFA8C7BD);
        }
    }

    private static void lamp(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y - 1, x + 3, y + 5, GOLD_SHADE);
        g.fill(x - 1, y + 1, x + 5, y + 3, GOLD_SHADE);
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x, y, x + 3, y + 1, 0xBBE7FFFF);
        g.fill(x + 3, y + 1, x + 4, y + 4, 0x550D3844);
    }

    private final class ArrayButton extends Button {
        private final Component label;
        private final int dataIndex;

        private ArrayButton(int x, int y, int width, Component label, int dataIndex, OnPress press) {
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
