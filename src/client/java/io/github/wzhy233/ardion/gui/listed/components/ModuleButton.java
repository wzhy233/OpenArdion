package io.github.wzhy233.ardion.gui.listed.components;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.gui.listed.api.AbstractComponent;
import io.github.wzhy233.ardion.gui.listed.api.Limitation;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.value.base.Value;

import java.awt.*;

public final class ModuleButton extends AbstractComponent {
    private final Module module;
    private float realY;
    private boolean extended;
    private float extend;

    public ModuleButton(AbstractComponent parent, Module module) {
        super(parent);
        this.module = module;
    }

    @Override
    public void init() {
        getChildComponents().clear();
        float y = getY() + getHeight();
        if (isBindable()) {
            KeyBindingButton keyBindingButton = new KeyBindingButton(this, module);
            keyBindingButton.setX(getX());
            keyBindingButton.setY(y);
            keyBindingButton.setWidth(getWidth());
            keyBindingButton.setHeight(ImplScreen.keyBindHeight);
            getChildComponents().add(keyBindingButton);
            y += ImplScreen.keyBindHeight + ImplScreen.valueSpacing;
        }
        for (Value<?> value : module.getValues()) {
            ValueButton valueButton = new ValueButton(this, value);
            valueButton.setX(getX());
            valueButton.setY(y);
            valueButton.setWidth(getWidth());
            valueButton.setHeight();
            getChildComponents().add(valueButton);
            y += valueButton.getHeight() + ImplScreen.valueSpacing;
        }
        super.init();
    }

    @Override
    public void update(int mouseX, int mouseY) {
        float y = getY() + getHeight();
        for (AbstractComponent component : getChildComponents()) {
            component.setX(getX());
            component.setY(y);
            component.setUiVisible(extended && !(component instanceof ValueButton valueButton && !valueButton.isVisible()));
            y += (component.getHeight() + ImplScreen.valueSpacing) * component.visibility();
        }
        super.update(mouseX, mouseY);
    }

    @Override
    protected void renderComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation) {
        if (!(getX() + getWidth() < limitation.getX()
                || getX() > limitation.getX() + limitation.getWidth()
                || getY() + getHeight() < limitation.getY()
                || getY() > limitation.getY() + limitation.getHeight())) {
            float enabled = ui.value("enabled", module.isEnabled() ? 1 : 0,
                    io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
            try (var ignored = new io.github.wzhy233.ardion.gui.ui.UiLayer(Skia.getCanvas(), null, enabled)) {
                Skia.drawRoundedRect(getX() + 2.0F, getY() + 1.0F, getWidth() - 4.0F, getHeight() - 2.0F,
                        3.0F, new Color(ImplScreen.getComponentColor((int) (getY() * 10.0F)), true));
            }
            {
                int rowAlpha = Math.round(HudSetting.backgroundAlpha() * 0.55F * (1 - enabled));
                if (rowAlpha > 0) {
                Skia.drawRoundedRect(getX() + 2.0F, getY() + 1.0F, getWidth() - 4.0F, getHeight() - 2.0F,
                            3.0F, new Color(0, 0, 0, Math.min(255, rowAlpha)));
                }
            }
            Font font = Fonts.getRegular(14);
            String moduleName = module.getDisplayName();
            Rect textBounds = Skia.getTextBounds(moduleName, font);
            Skia.drawText(moduleName, getX() + 5,
                    getY() + (getHeight() - textBounds.getHeight()) / 2F,
                    Color.WHITE, font);

            if (getChildComponents().size() > (isBindable() ? 1 : 0)) {
                float x = getX() + getWidth() - 6;
                float topBottom = 6.5F;
                float y = getY() + topBottom;
                for (int i = 0; i < 3; i++) {
                    Skia.drawArc(x, y, 0.09F, 0, 360, 1, Color.WHITE);
                    y += (getHeight() - topBottom * 2) / 2F;
                }
            }

            if (module.getCategory() != null && module.getCategory().isPinned(module)) {
                Font regular = Fonts.getRegular(16);
                String star = "★";
                float pinX = getX() + getWidth() - 20;
                float pinY = getY() + (getHeight() - Skia.getTextBounds(star, regular).getHeight()) / 2F;
                Skia.drawText(star, pinX, pinY, Color.WHITE, regular);
            }
        }

        int saved = Skia.getCanvas().save();
        try {
            Skia.getCanvas().clipRect(io.github.humbleui.types.Rect.makeXYWH(getX(), getY() + getHeight(), getWidth(), getExtend()));
            getChildComponents().forEach(component -> component.drawComponent(mouseX, mouseY, partialTicks, limitation));
        } finally { Skia.getCanvas().restoreToCount(saved); }
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int mouseButton) {
        if (isHovering(getParent().getX(), getParent().getY() + ImplScreen.panelTopHeight,
                getParent().getWidth(), getParent().getHeight() - ImplScreen.panelTopHeight, mouseX, mouseY)
                && isHovering(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY)) {
            markPressed();
            if (mouseButton == 0 && isBindable()) module.toggle();
            if (mouseButton == 1 && !getChildComponents().isEmpty()) extended = !extended;
            if (isBindable() && mouseButton == 2) {
                module.getCategory().togglePinned(module);
                getParent().init();
            }
        }
        if (extended) {
            getChildComponents().stream()
                    .filter(component -> !(component instanceof ValueButton valueButton && !valueButton.isVisible()))
                    .forEach(component -> component.mouseClicked(mouseX, mouseY, mouseButton));
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int state) {
        if (extended) {
            getChildComponents().stream()
                    .filter(component -> !(component instanceof ValueButton valueButton && !valueButton.isVisible()))
                    .forEach(component -> component.mouseReleased(mouseX, mouseY, state));
        }
    }

    public float getExtend() {
        float height = 0;
        for (AbstractComponent child : getChildComponents()) {
            child.setUiVisible(extended && !(child instanceof ValueButton button && !button.isVisible()));
            height += (child.getHeight() + ImplScreen.valueSpacing) * child.visibility();
        }
        return height;
    }

    public float getRealY() {
        return realY;
    }

    public void setRealY(float realY) {
        this.realY = realY;
    }

    public boolean isExtended() {
        return extended;
    }

    private boolean isBindable() {
        return module.getCategory() != null && ModuleManager.getModules().contains(module);
    }
}
