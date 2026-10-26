package io.github.wzhy233.ardion.module.base;

import io.github.wzhy233.ardion.event.base.Event;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.gui.hud.island.slots.ModuleToggleSlot;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.render.ClickGUI;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import io.github.wzhy233.ardion.util.client.OutputUtil;
import io.github.wzhy233.ardion.value.base.Value;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.*;

public abstract class Module {
    private static final Set<String> ANNOUNCE_BLACKLIST = Set.of("hud", "note-bot", "free-look", "scaffold", "music-player");
    private final String name;
    private final ModuleCategory category;
    private final boolean active;
    private final boolean hasListeners;
    private final List<Value<?>> values = new ArrayList<>();
    private final List<Value<?>> valuesView = Collections.unmodifiableList(this.values);
    private final Map<String, Value<?>> valuesByName = new HashMap<>();
    private final String displayName;
    private int bindKey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean enabled;

    protected Module(String name, ModuleCategory category) {
        this(name, category, true);
    }

    protected Module(String name, ModuleCategory category, boolean active) {
        this.name = name;
        this.category = category;
        this.active = active;
        this.displayName = formatDisplayName(name);
        this.hasListeners = active && this.scanListeners();
    }

    public final void toggle() {
        this.setEnabled(!this.enabled);
    }

    public final void setEnabled(boolean enabled) {
        this.setEnabled(enabled, true);
    }

    public final void setEnabledSilently(boolean enabled) {
        OutputUtil.withNotificationsSuppressed(() -> this.setEnabled(enabled, false));
    }

    private void setEnabled(boolean enabled, boolean announce) {
        if (enabled && !this.active) {
            return;
        }
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        boolean shouldAnnounce = announce && !OutputUtil.areNotificationsSuppressed()
                && !isAnnouncementBlacklisted()
                && this != ModuleManager.getModule(ClickGUI.class);
        if (enabled) {
            if (this.hasListeners) {
                EventManager.register(this);
            }
            this.onEnable();
            if (shouldAnnounce) {
                if (HudSetting.moduleToggleSound()) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.player.playSound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5F, 1.3F);
                    }
                }
                HUD.postIslandSlot(new ModuleToggleSlot(this.getDisplayName(), true, 2000L));
            }
            return;
        }

        this.onDisable();
        if (this.hasListeners) {
            EventManager.unregister(this);
        }
        if (shouldAnnounce) {
            if (HudSetting.moduleToggleSound()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.playSound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.5F, 0.8F);
                }
            }
            HUD.postIslandSlot(new ModuleToggleSlot(this.getDisplayName(), false, 2000L));
        }
    }

    public final String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    private static String formatDisplayName(String name) {
        String[] parts = name.split("[-_]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    public final ModuleCategory getCategory() {
        return this.category;
    }

    public final boolean isActive() {
        return this.active;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    public final int getBindKey() {
        return this.bindKey;
    }

    public final boolean hasBind() {
        return this.bindKey != GLFW.GLFW_KEY_UNKNOWN;
    }

    public final void setBindKey(int bindKey) {
        int oldBind = this.bindKey;
        this.bindKey = bindKey;
        ModuleManager.updateBind(this, oldBind, bindKey);
    }

    public final void clearBindKey() {
        this.setBindKey(GLFW.GLFW_KEY_UNKNOWN);
    }

    public final List<Value<?>> getValues() {
        return this.valuesView;
    }

    public final Value<?> getValue(String name) {
        return name == null ? null : this.valuesByName.get(name.toLowerCase(Locale.ROOT));
    }

    protected final <T extends Value<?>> T registerValue(T value) {
        this.values.add(value);
        this.valuesByName.put(value.getName().toLowerCase(Locale.ROOT), value);
        return value;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    private boolean isAnnouncementBlacklisted() {
        return ANNOUNCE_BLACKLIST.contains(this.name.toLowerCase());
    }

    private boolean scanListeners() {
        for (Class<?> type = this.getClass(); type != null && Module.class.isAssignableFrom(type); type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Listener.class) || method.getParameterCount() != 1) {
                    continue;
                }

                if (Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    return true;
                }
            }
        }
        return false;
    }
}
