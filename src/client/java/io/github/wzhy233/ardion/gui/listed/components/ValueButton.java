package io.github.wzhy233.ardion.gui.listed.components;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.gui.listed.api.AbstractComponent;
import io.github.wzhy233.ardion.gui.listed.api.Limitation;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Icon;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.*;

import java.awt.*;

public final class ValueButton extends AbstractComponent {
    private final Value<?> value;
    private final ColorSelector colorSelector;
    private float sliderAnimeWidth;

    public ValueButton(AbstractComponent parent, Value<?> value) {
        super(parent);
        this.value = value;
        this.colorSelector = value instanceof ColorValue colorValue ? new ColorSelector(colorValue) : null;
    }

    @Override
    public void update(int mouseX, int mouseY) {
        if (!((ModuleButton) getParent()).isExtended() || !isVisible()) sliderAnimeWidth = 0F;
        super.update(mouseX, mouseY);
    }

    @Override
    protected void renderComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation) {
        if (!(getX() + getWidth() < limitation.getX()
                || getX() > limitation.getX() + limitation.getWidth()
                || getY() + getHeight() < limitation.getY()
                || getY() > limitation.getY() + limitation.getHeight())) {
            Font font = Fonts.getRegular(12);
            Font icon = Fonts.getIcon(14);
            Color color = new Color(ImplScreen.getComponentColor((int) (getY() * 10)), true);

            if (value instanceof BooleanValue booleanValue) {
                Font checkbox = Fonts.getIcon(18);
                Rect bound = Skia.getTextBounds(value.getName(), font);
                Skia.drawText(value.getName(), getX() + 5,
                        getY() + (getHeight() - bound.getHeight()) / 2F, Color.WHITE, font);
                float checked = ui.value("checked", booleanValue.getValue() ? 1 : 0,
                        io.github.wzhy233.ardion.gui.ui.UiManager.INTERACTION_MS);
                for (int state = 0; state < 2; state++) {
                    String iconText = state == 1 ? Icon.CHECK_BOX : Icon.CHECK_BOX_OUTLINE_BLANK;
                    bound = Skia.getTextBounds(iconText, checkbox);
                    Color ink = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                            Math.round(color.getAlpha() * (state == 1 ? checked : 1 - checked)));
                    Skia.drawText(iconText, getX() + getWidth() - 5 - bound.getWidth(),
                            getY() + (getHeight() - bound.getHeight()) / 2F, ink, checkbox);
                }
            } else if (value instanceof NumberValue numberValue) {
                Skia.drawText(numberValue.getName(), getX() + 5, getY() + 5, Color.WHITE, font);
                String text = String.format("%.2f", numberValue.getValue().floatValue());
                Rect bound = Skia.getTextBounds(text, font);
                Skia.drawText(text, getX() + getWidth() - bound.getWidth() - 5, getY() + 5, Color.WHITE, font);
                float w = (float) ((getWidth() - 10) * ((numberValue.getValue() - numberValue.getMin())
                        / (numberValue.getMax() - numberValue.getMin())));
                sliderAnimeWidth += (w - sliderAnimeWidth) / 10F;
                int controlAlpha = Math.round(HudSetting.backgroundAlpha() * 0.85F);
                if (controlAlpha > 0) {
                    Skia.drawRect(getX() + 5, getY() + getHeight() - 6, getWidth() - 10, 1,
                            new Color(255, 255, 255, Math.round(controlAlpha * 0.35F)));
                }
                Skia.drawRect(getX() + 5, getY() + getHeight() - 6, sliderAnimeWidth, 1, color);
                if (controlAlpha > 0) {
                    Skia.drawRect(getX() + 5 + sliderAnimeWidth - 4, getY() + getHeight() - 6, 8, 1,
                            new Color(0, 0, 0, controlAlpha));
                }
                Skia.drawArc(getX() + 5 + sliderAnimeWidth, getY() + getHeight() - 5.5F, 2.5F, 0, 360, 1, color);

                if (isDragging()) {
                    if (mouseX >= getX() + 5 && mouseX <= getX() + 5 + getWidth() - 10) {
                        double val = (mouseX - (getX() + 5)) / (getWidth() - 10)
                                * (numberValue.getMax() - numberValue.getMin()) + numberValue.getMin();
                        numberValue.setValue(numberValue.isInteger() ? Math.rint(val) : Math.round(val * 100.0D) / 100.0D);
                    } else if (mouseX < getX() + 5) {
                        numberValue.setValue(numberValue.getMin());
                    } else if (mouseX > getX() + getWidth() - 5) {
                        numberValue.setValue(numberValue.getMax());
                    }
                }
            } else if (value instanceof ModeValue modeValue) {
                int controlAlpha = Math.round(HudSetting.backgroundAlpha() * 0.85F);
                if (controlAlpha > 0) {
                    Skia.drawRoundedRect(getX() + 2, getY() + 1, getWidth() - 4, getHeight() - 2, 2,
                            new Color(0, 0, 0, controlAlpha));
                }
                String text = modeValue.getName() + ": " + modeValue.getValue();
                Rect bound = Skia.getTextBounds(text, font);
                Skia.drawText(text, getX() + (getWidth() - bound.getWidth()) / 2F,
                        getY() + (getHeight() - bound.getHeight()) / 2F - 2, Color.WHITE, font);
                String iconText = Icon.ARROW_BACK_IOS + Icon.ARROW_FORWARD_IOS;
                Rect iconBounds = Skia.getTextBounds(iconText, icon);
                Skia.drawText(iconText, getX() + (getWidth() - iconBounds.getWidth()) / 2F,
                        getY() + getHeight() / 2F + 1.5F, color, icon);
            } else if (value instanceof StringValue stringValue) {
                String text = stringValue.getName() + ": " + stringValue.getValue();
                Rect bound = Skia.getTextBounds(text, font);
                Skia.drawText(text, getX() + (getWidth() - bound.getWidth()) / 2F,
                        getY() + (getHeight() - bound.getHeight()) / 2F, Color.WHITE, font);
            } else if (value instanceof ColorValue colorValue) {
                String text = colorValue.getName() + ":";
                Rect bound = Skia.getTextBounds(text, font);
                Skia.drawText(text, getX() + (getWidth() - bound.getWidth() - 7) / 2F, getY() + 1, color, font);
                Skia.drawRoundedRect(getX() + (getWidth() - bound.getWidth() - 7) / 2F + bound.getWidth() + 2,
                        getY() + 2, 5, 5, 1, new Color(colorValue.getColor(), true));
                if (colorSelector != null) {
                    colorSelector.draw(Skia.getCanvas(), getX() + (getWidth() - 54) / 2F,
                            getY() + 9, 40, 40, mouseX, mouseY, isDragging());
                }
            }
        } else {
            sliderAnimeWidth = 0F;
        }
        super.renderComponent(mouseX, mouseY, partialTicks, limitation);
    }

    public void setHeight() {
        if (value instanceof BooleanValue) setHeight(12);
        else if (value instanceof NumberValue) setHeight(22);
        else if (value instanceof ModeValue) setHeight(20);
        else if (value instanceof StringValue) setHeight(20);
        else if (value instanceof ColorValue) setHeight(52);
        else setHeight(12);
    }

    @Override
    public void mouseClicked(float mouseX, float mouseY, int mouseButton) {
        if (isHovering(getParent().getParent().getX(), getParent().getParent().getY() + ImplScreen.panelTopHeight,
                getParent().getParent().getWidth(), getParent().getParent().getHeight() - ImplScreen.panelTopHeight,
                mouseX, mouseY)
                && isHovering(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY)) {
            markPressed();
            if (value instanceof BooleanValue booleanValue) {
                booleanValue.setValue(!booleanValue.getValue());
            } else if (value instanceof NumberValue) {
                if (isHovering(getX() + 2, getY() + getHeight() - 9, getWidth() - 4, 7, mouseX, mouseY)) {
                    setDragging(true);
                }
            } else if (value instanceof ModeValue modeValue) {
                if (isHovering(getX(), getY(), getWidth() / 2F, getHeight(), mouseX, mouseY)) modeValue.previous();
                if (isHovering(getX() + getWidth() / 2F, getY(), getWidth() / 2F, getHeight(), mouseX, mouseY)) modeValue.next();
            } else if (value instanceof ColorValue) {
                setDragging(true);
            }
        }
    }

    public boolean isVisible() {
        return GlobalSetting.isVisible(value);
    }

    public Value<?> getValue() {
        return value;
    }
}
