package io.github.wzhy233.ardion.gui.ui;

import io.github.humbleui.skija.Canvas;
import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Consumer;

public final class UiManager {
    public static final long ENTER_MS = 220;
    public static final long EXIT_MS = 180;
    public static final long INTERACTION_MS = 120;
    public static final UiManager INSTANCE = new UiManager();
    private static float screenOpacity = 1;

    private UiManager() { }

    public static float screenOpacity() { return screenOpacity; }
    static void screenOpacity(float opacity) { screenOpacity = opacity; }

    public static Consumer<Canvas> compose(Consumer<Canvas> drawing) {
        float captured = screenOpacity;
        if (captured >= 1) return drawing;
        return canvas -> {
            try (UiLayer ignored = new UiLayer(canvas, null, captured)) { drawing.accept(canvas); }
        };
    }

    public static boolean deferScreenChange(Screen next) {
        Screen current = MinecraftClient.getInstance().currentScreen;
        if (current == next) return false;
        if (current instanceof AnimatedScreen animated && !animated.canLeave()
                && (next == null || next instanceof AnimatedScreen)) {
            animated.closeTo(next);
            return true;
        }
        return false;
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255;
            int b = (to >>> shift) & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }

    @Listener
    public void renderHud(Render2DEvent event) {
        HUD hud = ModuleManager.getModule(HUD.class);
        if (hud != null) hud.onRender(event);
    }
}
