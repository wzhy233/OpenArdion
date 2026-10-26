package io.github.wzhy233.ardion.gui.screen;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.module.impl.combat.KillAura;
import io.github.wzhy233.ardion.module.impl.combat.TriggerBot;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.GuiRenderUtil;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class HudEditorScreen extends io.github.wzhy233.ardion.gui.ui.AnimatedScreen {
    private static final int PANEL_WIDTH = 188;
    private static final float SNAP_DISTANCE = 6.0F;
    private static final float CONTEXT_WIDTH = 154.0F;
    private static final float CONTEXT_ROW_HEIGHT = 24.0F;
    private float panelX = 18.0F;
    private float panelY = 18.0F;
    private float panelXTarget = 18.0F;
    private float panelYTarget = 18.0F;
    private boolean draggingPanel = false;
    private float panelDragOffsetX;
    private float panelDragOffsetY;

    private final List<ElementState> elements = new ArrayList<>();
    private ElementState selected;
    private ElementState dragging;
    private ElementState resizing;
    private boolean contextOpen;
    private float contextX;
    private float contextY;
    private float dragOffsetX;
    private float dragOffsetY;
    private float resizeStartMouseX;
    private float resizeStartScale;
    private long lastUpdate;
    private final SkiaRenderManager skia = SkiaRenderManager.get();
    private final List<GuideLine> guideLines = new ArrayList<>();
    private static final float MOVE_SPEED = 0.2F;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        elements.clear();
        HUD hud = ModuleManager.getModule(HUD.class);
        if (hud != null) {
            elements.add(new ElementState("XYZ", hud::isXyzVisible, hud::setXyzVisible, hud::resetXyz));
            elements.add(new ElementState("ArrayList", hud::isArrayListVisible, hud::setArrayListVisible, hud::resetArrayList));
            elements.add(new ElementState("Info", hud::isInfoVisible, hud::setInfoVisible, hud::resetInfo));
            elements.add(new ElementState("TargetHUD", hud::isTargetHudVisible, hud::setTargetHudVisible, hud::resetTargetHud));
        }
        if (!elements.isEmpty()) selected = elements.get(0);
    }

    @Override
    public void removed() {
        ConfigManager.save();
    }

    @Override
    protected void renderUi(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimations();
        if (!skia.renderNow(context, canvas -> {
            drawScreenScrim(width, height);
            renderElements(context);
            renderSkijaOutlines(canvas);
            renderSkijaPanel(canvas, mouseX, mouseY);
            renderContextMenu(mouseX, mouseY);
        })) renderSkijaFailure(context);

    }

    private void renderSkijaFailure(DrawContext context) {
        context.fill(0, 0, width, height, 0xD80B0E16);
        context.drawTextWithShadow(textRenderer, "Ardion Skija HUD editor failed", 20, 20, 0xFFFF6B6B);
        context.drawTextWithShadow(textRenderer, skia.failureReason(), 20, 36, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Old HUD editor fallback is disabled so this failure is visible.", 20, 52, 0xFFB8C0D0);
    }

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        int deltaTime = (int) Math.max(0L, now - lastUpdate);
        lastUpdate = now;
        if (deltaTime > 100) deltaTime = 16;
        float factor = Math.min(1.0F, deltaTime * MOVE_SPEED * 0.06F);
        panelX += (panelXTarget - panelX) * factor;
        panelY += (panelYTarget - panelY) * factor;
    }

    private void renderElements(DrawContext context) {
        HUD hud = ModuleManager.getModule(HUD.class);

        for (ElementState element : elements) {
            element.bounds = null;

            if (hud != null && element.name.equals("XYZ")) {
                element.bounds = toBox(hud.renderXyz());
            } else if (hud != null && element.name.equals("ArrayList")) {
                element.bounds = toBox(hud.renderArrayList(width));
            } else if (hud != null && element.name.equals("Info")) {
                element.bounds = toBox(hud.renderInfo(width, height));
            } else if (hud != null && element.name.equals("TargetHUD")) {
                LivingEntity preview = currentCombatTarget();
                if (preview == null) preview = MinecraftClient.getInstance().player;
                if (preview != null) {
                    element.bounds = toBox(hud.renderTargetHudPreview(context, preview, width, height));
                }
            }

            if (element.bounds != null) {
                if (!skia.isUsable()) {
                    int color = element == selected ? 0xCC3698EC : 0x88FFFFFF;
                    drawOutline(context, element.bounds, color);
                    GuiRenderUtil.drawRoundedRect(context, element.bounds.x + element.bounds.width - 7.0F, element.bounds.y + element.bounds.height - 7.0F, 7.0F, 7.0F, 2.0F, color);
                }
            }
        }
    }

    private void renderSkijaOutlines(Canvas canvas) {
        for (GuideLine guideLine : guideLines) {
            if (guideLine.vertical()) {
                Skia.drawRect(guideLine.position() - 0.5F, 0.0F, 1.0F, height, color(0xAA4DA3FF));
            } else {
                Skia.drawRect(0.0F, guideLine.position() - 0.5F, width, 1.0F, color(0xAA4DA3FF));
            }
        }

        for (ElementState element : elements) {
            float opacity = ui.visible("outline:" + element.name, element.visible());
            if (opacity == 0 || element.bounds == null) continue;
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, opacity)) {
                float active = ui.value("outline-selected:" + element.name, element == selected ? 1 : 0,
                        io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
                drawSkiaOutline(element.bounds.x, element.bounds.y, element.bounds.width, element.bounds.height, active);
                if (active > 0) {
                    try (var marker = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, active)) {
                        fillRound(element.bounds.x + element.bounds.width - 11.0F, element.bounds.y + element.bounds.height - 11.0F, 11.0F, 11.0F, 4.0F, 0xFFEDF5FF);
                        fillRound(element.bounds.x + element.bounds.width - 8.0F, element.bounds.y + element.bounds.height - 8.0F, 5.0F, 5.0F, 2.5F, 0xFF35D8FF);
                    }
                }
            }
        }
    }

    private void renderContextMenu(int mouseX, int mouseY) {
        float opacity = ui.visible("context-menu", contextOpen);
        if (opacity == 0) return;
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(), null, opacity)) {

        float menuHeight = 10.0F + elements.size() * CONTEXT_ROW_HEIGHT;
        float x = MathHelper.clamp(contextX, 4.0F, width - CONTEXT_WIDTH - 4.0F);
        float y = MathHelper.clamp(contextY, 4.0F, height - menuHeight - 4.0F);
        drawGlassPanel(x, y, CONTEXT_WIDTH, menuHeight, 12.0F);

        float rowY = y + 5.0F;
        for (ElementState element : elements) {
            boolean hover = mouseX >= x + 5.0F && mouseX <= x + CONTEXT_WIDTH - 5.0F
                    && mouseY >= rowY && mouseY <= rowY + CONTEXT_ROW_HEIGHT;
            float emphasis = ui.hover("context:" + element.name, hover);
            if (emphasis > 0 || element == selected) {
                Skia.drawRoundedRect(x + 5.0F, rowY + 2.0F, CONTEXT_WIDTH - 10.0F, CONTEXT_ROW_HEIGHT - 4.0F, 7.0F,
                        color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(element == selected ? 0x244DA3FF : 0x00FFFFFF, 0x3AFFFFFF, emphasis)));
            }
            Skia.drawCircle(x + 17.0F, rowY + 12.0F, 3.5F, color(element.visible() ? 0xFF34C759 : 0xFF8E8E93));
            text(element.name, x + 28.0F, rowY + 5.0F, 10.5F, 0xFFEFF4FF, false);
            text(element.visible() ? "On" : "Off", x + CONTEXT_WIDTH - 30.0F, rowY + 5.0F, 10.0F, 0xFF9BA7BD, false);
            rowY += CONTEXT_ROW_HEIGHT;
        }
        }
    }

    private void renderSkijaPanel(Canvas canvas, int mouseX, int mouseY) {
        float panelHeight = 64.0F + elements.size() * 38.0F;
        boolean panelHover = mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + 38.0F;
        drawGlassPanel(panelX, panelY, PANEL_WIDTH, panelHeight, 22.0F);
        drawHeader(panelX, panelY, PANEL_WIDTH, 46.0F, 22.0F);
        float headerHover = ui.hover("panel-header", panelHover);
        if (headerHover > 0) Skia.drawRoundedRect(panelX + 4.0F, panelY + 4.0F, PANEL_WIDTH - 8.0F,
                38.0F, 18.0F, color(Math.round(headerHover * 24.0F) << 24 | 0xFFFFFF));
        text("HUD Editor", panelX + 16.0F, panelY + 10.0F, 17.0F, 0xFFF5F7FF, true);

        float y = panelY + 58.0F;
        for (ElementState element : elements) {
            boolean hover = mouseX >= panelX + 12.0F && mouseX <= panelX + PANEL_WIDTH - 12.0F && mouseY >= y && mouseY <= y + 30.0F;
            float active = ui.value("selected:" + element.name, element == selected ? 1 : 0,
                    io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
            float emphasis = ui.hover("element:" + element.name, hover);
            drawCard(panelX + 12.0F, y, PANEL_WIDTH - 24.0F, 30.0F, active, emphasis);
            fillRound(panelX + 24.0F, y + 10.0F, 10.0F, 10.0F, 5.0F, element.visible() ? 0xFF35E1C8 : 0xFF6E778A);
            text(element.name, panelX + 42.0F, y + 8.0F, 11.0F, 0xFFEAF0FF, false);
            text(element.visible() ? "shown" : "hidden", panelX + PANEL_WIDTH - 58.0F, y + 9.0F, 10.0F, 0xFF9BA7BD, false);
            y += 38.0F;
        }
        text("LMB drag / corner scale", panelX + 16.0F, y + 2.0F, 10.0F, 0xFF9BA7BD, false);
        text("RMB reset / DEL hide", panelX + 16.0F, y + 15.0F, 10.0F, 0xFF9BA7BD, false);
    }

    private void drawScreenScrim(int width, int height) {
        Skia.drawRect(0.0F, 0.0F, width, height, color(0x5A030712));
        Skia.drawGradientRoundedRect(0.0F, 0.0F, width, height, 0.0F, color(0x422A4DFF), color(0x001A1028));
    }

    private void drawGlassPanel(float x, float y, float w, float h, float radius) {
        Skia.drawShadow(x, y, w, h, radius);
        Skia.drawRoundedRect(x, y, w, h, radius, color(0xD8253041));
        Skia.drawOutline(x + 0.5F, y + 0.5F, w - 1.0F, h - 1.0F, Math.max(0.0F, radius - 1.0F), 1.0F, color(0x42FFFFFF));
    }

    private void drawHeader(float x, float y, float w, float h, float radius) {
        Skia.drawGradientRoundedRect(x, y, w, h, radius, color(0x6CFFFFFF), color(0x1436E6D3));
        Skia.drawRect(x, y + h - 1.0F, w, 1.0F, color(0x20FFFFFF));
    }

    private void drawCard(float x, float y, float w, float h, float active, float hover) {
        int top = io.github.wzhy233.ardion.gui.ui.UiManager.mix(
                io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x4A273246, 0x42FFFFFF, hover), 0xEA5472FF, active);
        int bottom = io.github.wzhy233.ardion.gui.ui.UiManager.mix(
                io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x30111723, 0x22FFFFFF, hover), 0xDA27D6C2, active);
        Skia.drawGradientRoundedRect(x, y, w, h, 12.0F, color(top), color(bottom));
        Skia.drawOutline(x, y, w, h, 10.0F, 1.0F,
                color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x18FFFFFF, 0x58FFFFFF, active)));
    }

    private void drawSkiaOutline(float x, float y, float w, float h, float active) {
        int argb = io.github.wzhy233.ardion.gui.ui.UiManager.mix(0xB8FFFFFF, 0xFF35D8FF, active);
        Skia.drawOutline(x - 2.0F, y - 2.0F, w + 4.0F, h + 4.0F, 6.0F, 1.8F, color(argb));
        fillRound(x + w - 8.0F, y + h - 8.0F, 8.0F, 8.0F, 3.0F, argb);
    }

    private void fillRound(float x, float y, float w, float h, float radius, int argb) {
        Skia.drawRoundedRect(x, y, w, h, radius, color(argb));
    }

    private void text(String value, float x, float y, float size, int argb, boolean medium) {
        Font font = medium ? Fonts.getMedium(size * 2.0F) : Fonts.getRegular(size * 2.0F);
        Rect bounds = Skia.getTextBounds(value, font);
        Skia.drawText(value, x, y - bounds.getTop(), color(argb), font);
    }

    private static Color color(int argb) {
        return new Color(argb, true);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!acceptsInput()) return true;
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (contextOpen && button == 0) {
            int contextIndex = contextIndex(mouseX, mouseY);
            if (contextIndex >= 0) {
                ElementState element = elements.get(contextIndex);
                selected = element;
                element.setVisible(!element.visible());
                contextOpen = false;
                ConfigManager.save();
                return true;
            }
            contextOpen = false;
        }

        float panelHeight = 64.0F + elements.size() * 38.0F;
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            if (button == 0 && mouseY <= panelY + 46.0F) {
                draggingPanel = true;
                panelDragOffsetX = (float) mouseX - panelX;
                panelDragOffsetY = (float) mouseY - panelY;
                return true;
            }
            for (int i = 0; i < elements.size(); i++) {
                float rowY = panelY + 58.0F + i * 38.0F;
                if (mouseX < panelX + 12.0F || mouseX > panelX + PANEL_WIDTH - 12.0F
                        || mouseY < rowY || mouseY > rowY + 30.0F) continue;
                ElementState element = elements.get(i);
                selected = element;
                if (button == 0) {
                    element.setVisible(!element.visible());
                    ConfigManager.save();
                } else if (button == 1) {
                    openContextMenu((float) mouseX, (float) mouseY);
                }
                return true;
            }
            return true;
        }

        for (int i = elements.size() - 1; i >= 0; i--) {
            ElementState element = elements.get(i);
            if (!element.visible() || element.bounds == null || !element.bounds.contains(mouseX, mouseY)) continue;
            selected = element;
            if (button == 1) {
                openContextMenu((float) mouseX, (float) mouseY);
                return true;
            }
            if (button == 0) {
                if (element.bounds.isInResize(mouseX, mouseY)) {
                    resizing = element;
                    resizeStartMouseX = (float) mouseX;
                    resizeStartScale = scaleOf(element);
                } else {
                    dragging = element;
                    dragOffsetX = (float) mouseX - element.bounds.x;
                    dragOffsetY = (float) mouseY - element.bounds.y;
                }
                return true;
            }
        }

        if (button == 1) {
            openContextMenu((float) mouseX, (float) mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!acceptsInput()) return true;
        double mouseX = click.x();
        double mouseY = click.y();

        if (draggingPanel) {
            panelXTarget = (float) mouseX - panelDragOffsetX;
            panelYTarget = (float) mouseY - panelDragOffsetY;
            return true;
        }

        if (dragging != null) {
            move(dragging, (float) mouseX - dragOffsetX, (float) mouseY - dragOffsetY);
            return true;
        }
        if (resizing != null) {
            setScale(resizing, MathHelper.clamp(resizeStartScale + ((float) mouseX - resizeStartMouseX) / 120.0F, 0.5F, 3.0F));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (!acceptsInput()) return true;
        draggingPanel = false;
        dragging = null;
        resizing = null;
        guideLines.clear();
        ConfigManager.save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!acceptsInput()) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!acceptsInput()) return true;
        int keyCode = input.key();
        if (keyCode == 261 && selected != null) {
            selected.setVisible(false);
            ConfigManager.save();
            return true;
        }
        return super.keyPressed(input);
    }

    private void openContextMenu(float mouseX, float mouseY) {
        contextOpen = true;
        contextX = mouseX;
        contextY = mouseY;
    }

    private int contextIndex(double mouseX, double mouseY) {
        if (!contextOpen) return -1;
        float menuHeight = 10.0F + elements.size() * CONTEXT_ROW_HEIGHT;
        float x = MathHelper.clamp(contextX, 4.0F, width - CONTEXT_WIDTH - 4.0F);
        float y = MathHelper.clamp(contextY, 4.0F, height - menuHeight - 4.0F);
        if (mouseX < x + 5.0F || mouseX > x + CONTEXT_WIDTH - 5.0F) return -1;
        float rowY = y + 5.0F;
        for (int i = 0; i < elements.size(); i++) {
            if (mouseY >= rowY && mouseY <= rowY + CONTEXT_ROW_HEIGHT) return i;
            rowY += CONTEXT_ROW_HEIGHT;
        }
        return -1;
    }

    private int panelIndex(double mouseX, double mouseY) {
        int y = (int) (panelY + 58.0F);
        for (int i = 0; i < elements.size(); i++) {
            if (mouseX >= panelX + 12.0F && mouseX <= panelX + PANEL_WIDTH - 12.0F && mouseY >= y && mouseY <= y + 30.0F) {
                return i;
            }
            y += 38;
        }
        return -1;
    }

    private void move(ElementState element, float x, float y) {
        HUD hud = ModuleManager.getModule(HUD.class);

        float scale = scaleOf(element);
        float elementWidth = element.bounds != null ? element.bounds.width : 100.0F;
        float elementHeight = element.bounds != null ? element.bounds.height : 20.0F;

        float[] snapped = snapPosition(element, x, y, elementWidth, elementHeight);
        float clampedX = MathHelper.clamp(snapped[0], 0.0F, width - elementWidth);
        float clampedY = MathHelper.clamp(snapped[1], 0.0F, height - elementHeight);

        if (hud != null && element.name.equals("XYZ")) {
            hud.setXyz(clampedX, clampedY, scale);
        } else if (hud != null && element.name.equals("ArrayList")) {
            boolean left = clampedX + elementWidth * 0.5F <= width * 0.5F;
            float anchorX = left ? clampedX : clampedX + elementWidth;
            hud.setArrayList(MathHelper.clamp(anchorX, 0.0F, width), MathHelper.clamp(clampedY, 0.0F, height), scale, left);
        } else if (hud != null && element.name.equals("Info")) {
            float right = width - clampedX - elementWidth;
            float bottom = height - clampedY - elementHeight;
            hud.setInfo(Math.max(0.0F, Math.min(right, width)), Math.max(0.0F, Math.min(bottom, height)), scale);
        } else if (hud != null && element.name.equals("TargetHUD")) {
            float centerX = width / 2.0F;
            float centerY = height / 2.0F;
            float newX = MathHelper.clamp(clampedX + elementWidth / 2.0F - centerX, -centerX, centerX);
            float newY = MathHelper.clamp(clampedY + elementHeight / 2.0F - centerY, -centerY, centerY);
            hud.setTargetHud(newX, newY, scale);
        }
    }

    private float[] snapPosition(ElementState moving, float x, float y, float elementWidth, float elementHeight) {
        guideLines.clear();
        float snappedX = x;
        float snappedY = y;
        float centerX = x + elementWidth / 2.0F;
        float centerY = y + elementHeight / 2.0F;
        float screenCenterX = width / 2.0F;
        float screenCenterY = height / 2.0F;

        if (Math.abs(centerX - screenCenterX) <= SNAP_DISTANCE) {
            snappedX = screenCenterX - elementWidth / 2.0F;
            guideLines.add(new GuideLine(true, screenCenterX));
        }
        if (Math.abs(centerY - screenCenterY) <= SNAP_DISTANCE) {
            snappedY = screenCenterY - elementHeight / 2.0F;
            guideLines.add(new GuideLine(false, screenCenterY));
        }

        for (ElementState element : elements) {
            if (element == moving || !element.visible() || element.bounds == null) continue;
            ElementBox box = element.bounds;
            snappedX = snapHorizontal(snappedX, elementWidth, box.x, true);
            snappedX = snapHorizontal(snappedX, elementWidth, box.x + box.width, true);
            snappedX = snapHorizontal(snappedX, elementWidth, box.x + box.width / 2.0F, false);
            snappedY = snapVertical(snappedY, elementHeight, box.y, true);
            snappedY = snapVertical(snappedY, elementHeight, box.y + box.height, true);
            snappedY = snapVertical(snappedY, elementHeight, box.y + box.height / 2.0F, false);
        }
        return new float[]{snappedX, snappedY};
    }

    private float snapHorizontal(float x, float elementWidth, float guide, boolean edge) {
        float left = x;
        float right = x + elementWidth;
        float center = x + elementWidth / 2.0F;
        if (edge && Math.abs(left - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(true, guide));
            return guide;
        }
        if (edge && Math.abs(right - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(true, guide));
            return guide - elementWidth;
        }
        if (!edge && Math.abs(center - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(true, guide));
            return guide - elementWidth / 2.0F;
        }
        return x;
    }

    private float snapVertical(float y, float elementHeight, float guide, boolean edge) {
        float top = y;
        float bottom = y + elementHeight;
        float center = y + elementHeight / 2.0F;
        if (edge && Math.abs(top - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(false, guide));
            return guide;
        }
        if (edge && Math.abs(bottom - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(false, guide));
            return guide - elementHeight;
        }
        if (!edge && Math.abs(center - guide) <= SNAP_DISTANCE) {
            guideLines.add(new GuideLine(false, guide));
            return guide - elementHeight / 2.0F;
        }
        return y;
    }

    private float scaleOf(ElementState element) {
        HUD hud = ModuleManager.getModule(HUD.class);
        if (hud != null && element.name.equals("XYZ")) return hud.xyzScale();
        if (hud != null && element.name.equals("ArrayList")) return hud.arrayScale();
        if (hud != null && element.name.equals("Info")) return hud.infoScale();
        if (hud != null && element.name.equals("TargetHUD")) return hud.targetHudScale();
        return 1.0F;
    }

    private void setScale(ElementState element, float scale) {
        HUD hud = ModuleManager.getModule(HUD.class);
        if (hud != null && element.name.equals("XYZ")) hud.setXyz(hud.xyzX(), hud.xyzY(), scale);
        if (hud != null && element.name.equals("ArrayList")) hud.setArrayList(hud.arrayX(), hud.arrayY(), scale);
        if (hud != null && element.name.equals("Info")) hud.setInfo(hud.infoX(), hud.infoY(), scale);
        if (hud != null && element.name.equals("TargetHUD")) hud.setTargetHud(hud.targetHudX(), hud.targetHudY(), scale);
    }

    private static ElementBox toBox(HUD.Bounds bounds) {
        return new ElementBox(bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static LivingEntity currentCombatTarget() {
        KillAura killAura = ModuleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        TriggerBot triggerBot = ModuleManager.getModule(TriggerBot.class);
        if (triggerBot != null && triggerBot.isEnabled() && triggerBot.getCurrentTarget() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static void drawOutline(DrawContext context, ElementBox box, int color) {
        context.fill((int) box.x, (int) box.y, (int) (box.x + box.width), (int) box.y + 1, color);
        context.fill((int) box.x, (int) (box.y + box.height) - 1, (int) (box.x + box.width), (int) (box.y + box.height), color);
        context.fill((int) box.x, (int) box.y, (int) box.x + 1, (int) (box.y + box.height), color);
        context.fill((int) (box.x + box.width) - 1, (int) box.y, (int) (box.x + box.width), (int) (box.y + box.height), color);
    }

    private record ElementBox(float x, float y, float width, float height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        boolean isInResize(double mouseX, double mouseY) {
            return mouseX >= x + width - 9.0F && mouseY >= y + height - 9.0F;
        }
    }

    private record GuideLine(boolean vertical, float position) {
    }

    private static final class ElementState {
        final String name;
        final VisibleGetter visibleGetter;
        final VisibleSetter visibleSetter;
        final Runnable reset;
        ElementBox bounds;

        ElementState(String name, VisibleGetter visibleGetter, VisibleSetter visibleSetter, Runnable reset) {
            this.name = name;
            this.visibleGetter = visibleGetter;
            this.visibleSetter = visibleSetter;
            this.reset = reset;
        }

        boolean visible() {
            return visibleGetter.get();
        }

        void setVisible(boolean visible) {
            visibleSetter.set(visible);
        }

        void reset() {
            reset.run();
        }
    }

    private interface VisibleGetter {
        boolean get();
    }

    private interface VisibleSetter {
        void set(boolean visible);
    }
}
