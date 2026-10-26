package io.github.wzhy233.ardion.setting;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.*;

import java.util.List;

public final class GlobalSetting {
    private static final SettingOwner OWNER = new SettingOwner();
    private static final ModeValue chatChannel = new ModeValue("chat-channel", OWNER, "normal", "normal", "party", "shout", "custom", "irc", "ai");
    private static final StringValue chatChannelPrefix = new StringValue("prefix", OWNER, "/say", () -> chatChannel.is("custom"));
    private static final BooleanValue showThinking = new BooleanValue("show-thinking", OWNER, true, () -> chatChannel.is("ai"));
    private static final BooleanValue clientOutput = new BooleanValue("client-output",OWNER,true);
    private static final ModeValue healthSource = new ModeValue("health-source", OWNER, "entity" , "entity", "sb-a", "sb-b");
    private static final BooleanValue disableMoveFix = new BooleanValue("disable-move-fix", OWNER, false);
    private static final List<Value<?>> values = List.of(chatChannel, chatChannelPrefix, showThinking, clientOutput, healthSource, disableMoveFix);

    private GlobalSetting() {
    }

    public static List<Value<?>> getValues() {
        return values;
    }

    public static List<Value<?>> getConfigValues() {
        return java.util.stream.Stream.of(values, ProxySetting.getValues(), TargetSetting.getValues(), ThemeSetting.getValues(), HudSetting.getValues())
                .flatMap(List::stream)
                .toList();
    }

    public static List<Value<?>> getVisibleValues() {
        return getConfigValues().stream()
                .filter(GlobalSetting::isVisible)
                .toList();
    }

    public static Value<?> getValue(String name) {
        return getConfigValues().stream()
                .filter(value -> value.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static ModeValue getTeamCheckMode() {
        return TargetSetting.getTeamCheckMode();
    }

    public static ModeValue getChatChannel() {
        return chatChannel;
    }

    public static StringValue getChatChannelPrefix() {
        return chatChannelPrefix;
    }

    public static BooleanValue getShowThinking() { return showThinking; }

    public static BooleanValue getClientOutput() {return clientOutput;}

    public static ModeValue getHealthSource() {
        return healthSource;
    }

    public static boolean isMoveFixDisabled() {
        return disableMoveFix.getValue();
    }

    public static String targetHudHealthSource() {
        return healthSource.getValue();
    }

    public static void setHealthSource(String source) {
        healthSource.setValueFromString(source);
    }

    public static boolean isVisible(Value<?> value) {
        if (value instanceof BooleanValue booleanValue) return booleanValue.isVisible();
        if (value instanceof NumberValue numberValue) return numberValue.isVisible();
        if (value instanceof ModeValue modeValue) return modeValue.isVisible();
        if (value instanceof StringValue stringValue) return stringValue.isVisible();
        if (value instanceof ColorValue colorValue) return colorValue.isVisible();
        if (TargetSetting.getValues().contains(value)) return TargetSetting.isVisible(value);
        if (ThemeSetting.getValues().contains(value)) return ThemeSetting.isVisible(value);
        if (HudSetting.getValues().contains(value)) return HudSetting.isVisible(value);
        return true;
    }

    private static final class SettingOwner extends Module {
        private SettingOwner() {
            super("settings", ModuleCategory.CLIENT);
        }
    }
}
