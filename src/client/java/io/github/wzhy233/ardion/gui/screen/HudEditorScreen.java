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
import io.github.wzhy233.ardion.gui.hud.HudPaint;
import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class HudEditorScreen extends io.github.wzhy233.ardion.gui.ui.AnimatedScreen {
    private static final float SNAP_DISTANCE = 6.0F;
    private static final float CONTEXT_WIDTH = 120.0F;
    private static final float CONTEXT_ROW_HEIGHT = ImplScreen.moduleHeight;
    private ElementState contextElement;

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
    private final SkiaRenderManager skia = SkiaRenderManager.get();
    private final List<GuideLine> guideLines = new ArrayList<>();

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
        selected = dragging = resizing = contextElement = null;
        contextOpen = false;
        guideLines.clear();
    }

    @Override
    public void removed() {
        ConfigManager.save();
    }

    @Override
    protected void renderUi(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!skia.renderNow(context, canvas -> {
            renderElements(context);
            renderSkijaOutlines(canvas);
            renderContextMenu(mouseX, mouseY);
        })) renderSkijaFailure(context);

    }

    private void renderSkijaFailure(DrawContext context) {
        context.fill(0, 0, width, height, 0xD80B0E16);
        context.drawTextWithShadow(textRenderer, "OpenArdion Skija HUD editor failed", 20, 20, 0xFFFF6B6B);
        context.drawTextWithShadow(textRenderer, skia.failureReason(), 20, 36, 0xFFFFFFFF);
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

        }
    }

    private void renderSkijaOutlines(Canvas canvas) {
        for (GuideLine guideLine : guideLines) {
            if (guideLine.vertical()) {
                Skia.drawRect(guideLine.position() - 0.5F, 0.0F, 1.0F, height, color(ImplScreen.getComponentColor(0)));
            } else {
                Skia.drawRect(0.0F, guideLine.position() - 0.5F, width, 1.0F, color(ImplScreen.getComponentColor(0)));
            }
        }

        for (ElementState element : elements) {
            float opacity = ui.visible("outline:" + element.name, element.visible());
            if (opacity == 0 || element.bounds == null) continue;
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(canvas, null, opacity)) {
                float active = ui.value("outline-selected:" + element.name, element == selected ? 1 : 0,
                        io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
                drawSkiaOutline(element.bounds.x, element.bounds.y, element.bounds.width, element.bounds.height, active);
            }
        }
    }

    private void renderContextMenu(int mouseX, int mouseY) {
        float opacity = ui.visible("context-menu", contextOpen);
        if (opacity == 0) return;
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(), null, opacity)) {
            float menuHeight = contextHeight();
            float x = contextLeft();
            float y = contextTop();
            float radius = 5.0F;
            if (HudSetting.blur()) {
                Skia.save();
                try {
                    Skia.clip(x, y, CONTEXT_WIDTH, menuHeight, radius);
                    Rect bounds = Rect.makeXYWH(x, y, CONTEXT_WIDTH, menuHeight);
                    if (HudSetting.blurSksl()) {
                        Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), bounds,
                                HudSetting.blurSigma(), HudSetting.skslShader());
                    } else {
                        Effects.blurBackdrop(Skia.getCanvas(), bounds, HudSetting.blurSigma());
                    }
                } finally { Skia.restore(); }
            }
            int alpha = HudSetting.backgroundAlpha();
            if (alpha > 0 && HudSetting.shadow()) Skia.drawShadow(x, y, CONTEXT_WIDTH, menuHeight, radius);
            HudPaint.renderHudCard(x, y, CONTEXT_WIDTH, menuHeight, radius, Color.WHITE, alpha);
            Font font = Fonts.getRegular(14);
            for (int i = 0; i < contextRows(); i++) {
                float rowY = y + 3.0F + i * CONTEXT_ROW_HEIGHT;
                boolean enabled = contextElement == null && elements.get(i).visible();
                float hover = ui.hover("context:" + (contextElement == null ? "all" : contextElement.name) + i,
                        contextOpen && contextIndex(mouseX, mouseY) == i);
                int background = enabled ? ImplScreen.getComponentColor((int) (rowY * 10))
                        : Math.round(alpha * 0.55F) << 24;
                Skia.drawRoundedRect(x + 2, rowY + 1, CONTEXT_WIDTH - 4, CONTEXT_ROW_HEIGHT - 2, 3,
                        color(io.github.wzhy233.ardion.gui.ui.UiManager.mix(background, 0x40FFFFFF, hover)));
                String label = contextElement == null ? elements.get(i).name : i == 0 ? "Reset" : "Hide";
                Rect bounds = Skia.getTextBounds(label, font);
                Skia.drawText(label, x + 5, rowY + (CONTEXT_ROW_HEIGHT - bounds.getHeight()) / 2,
                        Color.WHITE, font);
            }
        }
    }

    private void drawSkiaOutline(float x, float y, float w, float h, float active) {
        int argb = io.github.wzhy233.ardion.gui.ui.UiManager.mix(0x60FFFFFF, ImplScreen.getComponentColor(0), active);
        Skia.drawOutline(x - 1, y - 1, w + 2, h + 2, 3, 1, color(argb));
        Skia.drawRoundedRect(x + w - 5, y + h - 5, 5, 5, 1, color(argb));
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
            int index = contextIndex(mouseX, mouseY);
            if (index >= 0) {
                if (contextElement == null) {
                    selected = elements.get(index);
                    selected.setVisible(!selected.visible());
                } else if (index == 0) {
                    contextElement.reset();
                } else {
                    contextElement.setVisible(false);
                }
                ConfigManager.save();
            }
            contextOpen = false;
            return true;
        }

        for (int i = elements.size() - 1; i >= 0; i--) {
            ElementState element = elements.get(i);
            if (!element.visible() || element.bounds == null || !element.bounds.contains(mouseX, mouseY)) continue;
            selected = element;
            if (button == 1) {
                openContextMenu((float) mouseX, (float) mouseY, element);
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
            openContextMenu((float) mouseX, (float) mouseY, null);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!acceptsInput()) return true;
        double mouseX = click.x();
        double mouseY = click.y();

        if (click.button() != 0) return false;

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
        if (click.button() != 0) return false;
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
        if (keyCode == 256 && contextOpen) {
            contextOpen = false;
            return true;
        }
        if (keyCode == 261 && selected != null) {
            selected.setVisible(false);
            ConfigManager.save();
            return true;
        }
        return super.keyPressed(input);
    }

    private void openContextMenu(float mouseX, float mouseY, ElementState element) {
        dragging = resizing = null;
        guideLines.clear();
        contextElement = element;
        contextOpen = true;
        contextX = mouseX;
        contextY = mouseY;
    }

    private int contextRows() {
        return contextElement == null ? elements.size() : 2;
    }

    private float contextHeight() {
        return 6 + contextRows() * CONTEXT_ROW_HEIGHT;
    }

    private float contextLeft() {
        return MathHelper.clamp(contextX, 0, Math.max(0, width - CONTEXT_WIDTH));
    }

    private float contextTop() {
        return MathHelper.clamp(contextY, 0, Math.max(0, height - contextHeight()));
    }

    private int contextIndex(double mouseX, double mouseY) {
        if (!contextOpen || mouseX < contextLeft() + 2 || mouseX >= contextLeft() + CONTEXT_WIDTH - 2) return -1;
        double offset = mouseY - contextTop() - 3;
        if (offset < 0 || offset >= contextRows() * CONTEXT_ROW_HEIGHT) return -1;
        return (int) (offset / CONTEXT_ROW_HEIGHT);
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
