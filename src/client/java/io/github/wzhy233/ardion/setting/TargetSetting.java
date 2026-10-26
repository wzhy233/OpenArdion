package io.github.wzhy233.ardion.setting;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.value.base.Value;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.ModeValue;

import java.util.List;

public final class TargetSetting {
    private static final SettingOwner OWNER = new SettingOwner();
    private static final BooleanValue teamCheck = new BooleanValue("team-check", OWNER, false);
    private static final ModeValue teamCheckMode = new ModeValue("team-mode", OWNER, "scoreboard",
            () -> teamCheck.getValue(), "scoreboard", "armor");
    private static final BooleanValue player = new BooleanValue("player", OWNER, true);
    private static final BooleanValue animal = new BooleanValue("animal", OWNER, false);
    private static final BooleanValue villager = new BooleanValue("villager", OWNER, false);
    private static final BooleanValue crystal = new BooleanValue("crystal", OWNER, false);
    private static final BooleanValue monster = new BooleanValue("monster", OWNER, false);
    private static final BooleanValue invisible = new BooleanValue("invisible", OWNER, false);
    private static final List<Value<?>> values = List.of(teamCheck, teamCheckMode, player, animal, villager, crystal, monster, invisible);

    private TargetSetting() {
    }

    public static List<Value<?>> getValues() {
        return values;
    }

    public static boolean teamCheck() {
        return teamCheck.getValue();
    }

    public static ModeValue getTeamCheckMode() {
        return teamCheckMode;
    }

    public static boolean player() {
        return player.getValue();
    }

    public static boolean invisibles() {
        return invisible.getValue();
    }

    public static boolean animal() {
        return animal.getValue();
    }

    public static boolean villager() {
        return villager.getValue();
    }

    public static boolean crystal() {
        return crystal.getValue();
    }

    public static boolean monster() {
        return monster.getValue();
    }

    public static boolean isVisible(Value<?> value) {
        if (value instanceof BooleanValue booleanValue) return booleanValue.isVisible();
        if (value instanceof ModeValue modeValue) return modeValue.isVisible();
        return true;
    }

    private static final class SettingOwner extends Module {
        private SettingOwner() {
            super("targining", ModuleCategory.CLIENT);
        }
    }
}
