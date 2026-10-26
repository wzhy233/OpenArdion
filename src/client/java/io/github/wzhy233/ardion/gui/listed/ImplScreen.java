package io.github.wzhy233.ardion.gui.listed;

import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.gui.listed.api.AbstractComponent;
import io.github.wzhy233.ardion.gui.listed.components.Panel;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.setting.ProxySetting;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.setting.ThemeSetting;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ImplScreen {
    public static final float panelStartX = 12F;
    public static final float panelY = 22F;
    public static final float panelWidth = 90F;
    public static final float panelTopHeight = 17F;
    public static final float panelMaxHeight = 200F;
    public static final float panelSpacing = 5F;
    public static final float moduleHeight = 19F;
    public static final float moduleSpacing = 0.5F;
    public static final float valueSpacing = 0.5F;
    public static final float keyBindHeight = 10F;
    public static final ImplScreen instance = new ImplScreen();
    public static final Color[] MainTheme = new Color[]{
            new Color(26, 25, 26),
            new Color(31, 30, 31),
            new Color(5, 134, 105),
            new Color(38, 37, 38),
            new Color(45, 45, 45)
    };

    private final List<Panel> panels = new CopyOnWriteArrayList<>();
    private Panel currentPanel;

    private ImplScreen() {
    }

    public static int getComponentColor(int index) {
        return ThemeSetting.getColor(index);
    }

    public void initGui() {
        if (!panels.isEmpty()) {
            panels.forEach(AbstractComponent::resetAppearance);
            return;
        }
        float x = panelStartX;
        for (ModuleCategory category : ModuleCategory.values()) {
            List<Module> modules = ModuleManager.getModulesByCategory(category);
            if (modules.isEmpty() && category != ModuleCategory.LUA) continue;
            Panel panel = new Panel(this, category);
            panel.setX(x);
            panel.setY(panelY);
            panel.setWidth(panelWidth);
            panel.setHeight(panelTopHeight + Math.min(modules.size() * (moduleHeight + moduleSpacing), panelMaxHeight));
            panels.add(panel);
            x += panelWidth + panelSpacing;
        }

        Panel panel = getClientPanel();
        panel.setX(x);
        panel.setY(panelY);
        panel.setWidth(panelWidth);
        panel.setHeight(panelTopHeight + Math.min(panel.getModules().size() * (moduleHeight + moduleSpacing), panelMaxHeight));
        panels.add(panel);

        panels.forEach(AbstractComponent::init);
        currentPanel = panels.get(panels.size() - 1);
    }

    private Panel getClientPanel() {
        List<Module> configModules = new ArrayList<>();
        Module settings = new Module("settings", ModuleCategory.CLIENT) {
            {
                GlobalSetting.getValues().forEach(this::registerValue);
            }

            @Override
            protected void onEnable() {
                setEnabled(false);
            }
        };
        configModules.add(settings);
        Module proxy = new Module("proxy", ModuleCategory.CLIENT) {
            {
                ProxySetting.getValues().forEach(this::registerValue);
            }

            @Override
            protected void onEnable() {
                setEnabled(false);
            }
        };
        configModules.add(proxy);
        Module targeting = new Module("targining", ModuleCategory.CLIENT) {
            {
                TargetSetting.getValues().forEach(this::registerValue);
            }

            @Override
            protected void onEnable() {
                setEnabled(false);
            }
        };
        configModules.add(targeting);
        Module theme = new Module("theme", ModuleCategory.CLIENT) {
            {
                ThemeSetting.getValues().forEach(this::registerValue);
            }

            @Override
            protected void onEnable() {
                setEnabled(false);
            }
        };
        configModules.add(theme);
        Module hud = new Module("hud-settings", ModuleCategory.CLIENT) {
            {
                HudSetting.getValues().forEach(this::registerValue);
            }

            @Override
            protected void onEnable() {
                setEnabled(false);
            }
        };
        configModules.add(hud);
        return new Panel(this, "CONFIGS", configModules);
    }

    public void handleMouseInput(double wheel) {
        if (wheel != 0) panels.forEach(panel -> panel.setWheel((int) wheel));
    }

    public void update(int mouseX, int mouseY) {
        panels.forEach(panel -> {
            if (panel.isFullHovering(mouseX, mouseY)) {
                panel.update(mouseX, mouseY);
            }
        });
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        update(mouseX, mouseY);
        panels.forEach(panel -> panel.drawComponent(mouseX, mouseY, partialTicks, null));
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        Panel toTop = null;
        for (Panel panel : panels) {
            if (!isHovering(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(), mouseX, mouseY)) continue;
            if (toTop == null || panels.indexOf(panel) > panels.indexOf(toTop)) {
                toTop = panel;
            }
        }
        if (toTop != null) {
            panels.remove(toTop);
            panels.add(toTop);
            currentPanel = toTop;
        }
        panels.forEach(panel -> panel.mouseClicked(mouseX, mouseY, mouseButton));
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        panels.forEach(panel -> panel.mouseReleased(mouseX, mouseY, state));
    }

    public void keyTyped(char typedChar, int keyCode) {
        panels.forEach(panel -> panel.keyTyped(typedChar, keyCode));
    }

    public void onGuiClosed() {
        panels.forEach(panel -> panel.setDragging(false));
        ConfigManager.save();
    }

    public boolean isHovering(float x, float y, float width, float height, float mouseX, float mouseY) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    public Panel getCurrentPanel() {
        return currentPanel;
    }
}
