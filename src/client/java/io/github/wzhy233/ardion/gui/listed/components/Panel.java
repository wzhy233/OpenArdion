package io.github.wzhy233.ardion.gui.listed.components;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.hud.HudPaint;
import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.gui.listed.api.AbstractComponent;
import io.github.wzhy233.ardion.gui.listed.api.Limitation;
import io.github.wzhy233.ardion.gui.shader.Effects;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;

import java.awt.*;
import java.util.List;

public final class Panel extends AbstractComponent {
    private final ModuleCategory category;
    private final String title;
    private final ImplScreen screenIn;
    private final List<Module> modules;
    private List<Module> shownModules = List.of();
    private float scroll;
    private float scrollCache;
    private float dragX;
    private float dragY;

    public Panel(ImplScreen screenIn, ModuleCategory category) {
        super(null);
        this.category = category;
        this.title = category.name();
        this.screenIn = screenIn;
        this.modules = null;
    }

    public Panel(ImplScreen screenIn, String title, List<Module> modules) {
        super(null);
        this.category = null;
        this.title = title;
        this.screenIn = screenIn;
        this.modules = modules;
    }

    @Override
    public void init() {
        shownModules = List.copyOf(getModules());
        getChildComponents().clear();
        float y = getY() + ImplScreen.panelTopHeight;
        if (category != null) {
            for (Module module : category.getPinnedModules()) {
                addModule(y, module);
                y += ImplScreen.moduleHeight + ImplScreen.moduleSpacing;
            }
            for (Module module : ModuleManager.getModulesByCategory(category)) {
                if (!category.isPinned(module)) {
                    addModule(y, module);
                    y += ImplScreen.moduleHeight + ImplScreen.moduleSpacing;
                }
            }
        } else {
            for (Module module : modules) {
                addModule(y, module);
                y += ImplScreen.moduleHeight + ImplScreen.moduleSpacing;
            }
        }
        super.init();
    }

    private void addModule(float y, Module module) {
        ModuleButton button = new ModuleButton(this, module);
        button.setX(getX());
        button.setY(y);
        button.setWidth(getWidth());
        button.setHeight(ImplScreen.moduleHeight);
        getChildComponents().add(button);
    }

    @Override
    public void update(int mouseX, int mouseY) {
        scrollCache += (scroll - scrollCache) / 5F;
        float height = 0F;
        for (AbstractComponent child : getChildComponents()) {
            height += child.getHeight() + ImplScreen.moduleSpacing;
            ModuleButton moduleButton = (ModuleButton) child;
            height += moduleButton.getExtend();
        }
        height = Math.max(0, height - ImplScreen.moduleSpacing);
        setHeight(ImplScreen.panelTopHeight + Math.min(height, ImplScreen.panelMaxHeight));
        getChildComponents().forEach(component -> component.update(mouseX, mouseY));
    }

    @Override
    public float getHeight() {
        return ui.value("height", super.getHeight(), io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
    }

    @Override
    protected void renderComponent(int mouseX, int mouseY, float partialTicks, Limitation ignored) {
        if (category != null && !shownModules.equals(getModules())) {
            init();
            update(mouseX, mouseY);
        }
        if (isDragging()) {
            setX(mouseX - dragX);
            setY(mouseY - dragY);
        }

        float y = getY() + ImplScreen.panelTopHeight + scrollCache;
        float realY = getY() + ImplScreen.panelTopHeight + scroll;
        for (AbstractComponent component : getChildComponents()) {
            ModuleButton moduleButton = (ModuleButton) component;
            moduleButton.setX(getX());
            moduleButton.setY(y);
            moduleButton.setRealY(realY);
            y += ImplScreen.moduleHeight + ImplScreen.moduleSpacing + moduleButton.getExtend();
            realY += ImplScreen.moduleHeight + ImplScreen.moduleSpacing + moduleButton.getExtend();
        }

        if (!getChildComponents().isEmpty()) {
            float allExpand = 0F;
            for (AbstractComponent component : getChildComponents()) {
                allExpand += ((ModuleButton) component).getExtend();
            }
            if (scroll > 0 || -scroll > getChildComponents().size() * (ImplScreen.moduleHeight + ImplScreen.moduleSpacing)
                    - ImplScreen.moduleSpacing + allExpand - (getHeight() - ImplScreen.panelTopHeight)) {
                scroll = 0;
            }
            if (isHovering(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY)) {
                scroll += scroll();
                setWheel(0);
            }
            getChildComponents().forEach(component -> component.update(mouseX, mouseY));
        }

        float radius = 5.0F;
        int bgAlpha = HudSetting.backgroundAlpha();
        applyBackdropBlur(getX(), getY(), getWidth(), getHeight(), radius);
        if (bgAlpha > 0 && HudSetting.shadow()) Skia.drawShadow(getX(), getY(), getWidth(), getHeight(), radius);
        HudPaint.renderHudCard(getX(), getY(), getWidth(), getHeight(), radius, Color.WHITE, bgAlpha);
        Font font = Fonts.getMedium(18);
        Rect textBounds = Skia.getTextBounds(title, font);
        Skia.drawText(title, getX() + (getWidth() - textBounds.getWidth()) / 2F,
                getY() + (ImplScreen.panelTopHeight - textBounds.getHeight()) / 2F, Color.WHITE, font);

        Limitation limitation = new Limitation(getX(), getY() + ImplScreen.panelTopHeight,
                getWidth(), getHeight() - ImplScreen.panelTopHeight, 3);
        limitation.start();
        super.renderComponent(mouseX, mouseY, partialTicks, limitation);
        limitation.end();

        if (!getChildComponents().isEmpty()) {
            int lineAlpha = Math.round(bgAlpha * 0.33F);
            if (lineAlpha > 0) {
                Skia.drawRect(getX(), getY() + ImplScreen.panelTopHeight - 0.5F, getWidth(), 0.5F,
                        new Color(210, 210, 210, lineAlpha));
            }
        }
    }

    private void applyBackdropBlur(float x, float y, float width, float height, float radius) {
        if (!HudSetting.blur()) return;
        Rect bounds = Rect.makeXYWH(x, y, width, height);
        Skia.save();
        Skia.clip(x, y, width, height, radius);
        if (HudSetting.blurSksl()) {
            Effects.blurBackdropSKSL(Skia.getCanvas(), Skia.getSurface(), bounds,
                    HudSetting.blurSigma(), HudSetting.skslShader());
        } else {
            Effects.blurBackdrop(Skia.getCanvas(), bounds, HudSetting.blurSigma());
        }
        Skia.restore();
    }

    private float scroll() {
        float amount = 0F;
        if (getWheel() > 0) amount = 20F;
        if (getWheel() < 0) amount = -20F;
        ModuleButton last = (ModuleButton) getChildComponents().get(getChildComponents().size() - 1);
        if (last.getRealY() + ImplScreen.moduleHeight + last.getExtend() + amount < getY() + getHeight()) {
            amount = -(last.getRealY() + ImplScreen.moduleHeight + last.getExtend() - getY() - getHeight());
        }
        if (getChildComponents().get(0).getY() + amount > getY() + ImplScreen.panelTopHeight) {
            amount = -(getChildComponents().get(0).getY() - (getY() + ImplScreen.panelTopHeight));
        }
        return amount;
    }

    public boolean isCurrent() {
        return screenIn.getCurrentPanel() == this;
    }

    @Override
    public boolean isHovering(float mouseX, float mouseY) {
        return isHovering(getX(), getY() + ImplScreen.panelTopHeight, getWidth(),
                getHeight() - ImplScreen.panelTopHeight, mouseX, mouseY);
    }

    public boolean isFullHovering(float mouseX, float mouseY) {
        return isHovering(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int mouseButton) {
        if (!isCurrent()) return;
        if (isHovering(getX(), getY(), getWidth(), ImplScreen.panelTopHeight, mouseX, mouseY)) {
            setDragging(true);
            dragX = mouseX - getX();
            dragY = mouseY - getY();
        }
        getChildComponents().forEach(component -> component.mouseClicked(mouseX, mouseY, mouseButton));
    }

    public List<Module> getModules() {
        return modules == null ? ModuleManager.getModulesByCategory(category) : modules;
    }
}
