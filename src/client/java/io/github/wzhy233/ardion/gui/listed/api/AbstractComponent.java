package io.github.wzhy233.ardion.gui.listed.api;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractComponent implements Component {
    protected final io.github.wzhy233.ardion.gui.ui.UiState ui = new io.github.wzhy233.ardion.gui.ui.UiState();
    private boolean uiVisible = true;
    private long pressedUntil;
    private final List<AbstractComponent> childComponents = new CopyOnWriteArrayList<>();
    private final AbstractComponent parent;
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean dragging;
    private int wheel;

    protected AbstractComponent(AbstractComponent parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        childComponents.forEach(AbstractComponent::init);
    }

    @Override
    public void update(int mouseX, int mouseY) {
        childComponents.forEach(component -> component.update(mouseX, mouseY));
    }

    @Override
    public final void drawComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation) {
        float opacity = visibility();
        if (opacity == 0) return;
        var bounds = childComponents.isEmpty()
                ? io.github.humbleui.types.Rect.makeXYWH(x - 8, y - 8, width + 16, height + 16) : null;
        try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(
                io.github.wzhy233.ardion.util.render.Skia.getCanvas(), bounds, opacity)) {
            renderComponent(mouseX, mouseY, partialTicks, limitation);
            if (parent != null) {
                boolean hover = uiVisible && isHovering(mouseX, mouseY) && (limitation == null
                        || isHovering(limitation.getX(), limitation.getY(), limitation.getWidth(), limitation.getHeight(), mouseX, mouseY));
                float emphasis = ui.hover("pointer", hover);
                float press = ui.hover("pressed", System.nanoTime() < pressedUntil);
                io.github.wzhy233.ardion.util.render.Skia.drawRoundedRect(x + 2, y + 1,
                        width - 4, height - 2, 3, new Color(255, 255, 255, Math.round(16 * emphasis + 18 * press)));
            }
        }
    }

    protected void renderComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation) {
        childComponents.forEach(component -> component.drawComponent(mouseX, mouseY, partialTicks, limitation));
    }

    public final void setUiVisible(boolean visible) { uiVisible = visible; }
    public final float visibility() { return ui.visible("visibility", uiVisible); }
    protected final void markPressed() { pressedUntil = System.nanoTime() + 90_000_000L; }

    public final void resetAppearance() {
        ui.reset();
        dragging = false;
        childComponents.forEach(AbstractComponent::resetAppearance);
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int mouseButton) {
        if (mouseButton == 0 && isHovering(mouseX, mouseY)) dragging = true;
        childComponents.forEach(component -> component.mouseClicked(mouseX, mouseY, mouseButton));
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int state) {
        if (state == 0) setDragging(false);
        childComponents.forEach(component -> component.mouseReleased(mouseX, mouseY, state));
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        childComponents.forEach(component -> component.keyTyped(typedChar, keyCode));
    }

    public boolean isHovering(float x, float y, float width, float height, float mouseX, float mouseY) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    public boolean isHovering(float mouseX, float mouseY) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    protected float clamp(float value, float min, float max) {
        return Math.max(Math.min(value, max), min);
    }

    protected Color interpolate(Color source, Color target, float progress) {
        progress = clamp(progress, 0, 1);
        int red = (int) (source.getRed() * (1 - progress) + target.getRed() * progress);
        int green = (int) (source.getGreen() * (1 - progress) + target.getGreen() * progress);
        int blue = (int) (source.getBlue() * (1 - progress) + target.getBlue() * progress);
        return new Color(red, green, blue);
    }

    public List<AbstractComponent> getChildComponents() {
        return childComponents;
    }

    public AbstractComponent getParent() {
        return parent;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setDragging(boolean dragging) {
        this.dragging = dragging;
        if (!dragging) childComponents.forEach(component -> component.setDragging(false));
    }

    public int getWheel() {
        return wheel;
    }

    public void setWheel(int wheel) {
        this.wheel = wheel;
    }
}
