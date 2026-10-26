package io.github.wzhy233.ardion.gui.listed.components;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import io.github.wzhy233.ardion.gui.listed.ImplScreen;
import io.github.wzhy233.ardion.gui.listed.api.AbstractComponent;
import io.github.wzhy233.ardion.gui.listed.api.Limitation;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.input.KeyUtil;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public final class KeyBindingButton extends AbstractComponent {
    private final Module module;
    private boolean keyBinding;

    public KeyBindingButton(AbstractComponent parent, Module module) {
        super(parent);
        this.module = module;
    }

    @Override
    protected void renderComponent(int mouseX, int mouseY, float partialTicks, Limitation limitation) {
        Font font = Fonts.getRegular(14);
        int bgAlpha = Math.round(HudSetting.backgroundAlpha() * 0.55F);
        if (bgAlpha > 0) {
            Skia.drawRoundedRect(getX() + 2.0F, getY() + 1.0F, getWidth() - 4.0F, getHeight() - 2.0F,
                    2.0F, new Color(0, 0, 0, bgAlpha));
        }
        String text = keyBinding ? "Listening..." : "Bind: " + KeyUtil.getKeyName(module.getBindKey());
        Rect textBounds = Skia.getTextBounds(text, font);
        Skia.drawText(text, getX() + (getWidth() - textBounds.getWidth()) / 2F,
                getY() + (getHeight() - textBounds.getHeight()) / 2F,
                new Color(ImplScreen.getComponentColor((int) (getY() * 10)), true), font);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int state) {
        keyBinding = state == 0 && isHovering(getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
        if (keyBinding) markPressed();
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) keyBinding = false;
        if (keyBinding) {
            module.setBindKey(keyCode == GLFW.GLFW_KEY_DELETE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
            keyBinding = false;
        }
    }
}
