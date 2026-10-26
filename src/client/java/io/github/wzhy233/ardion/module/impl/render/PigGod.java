package io.github.wzhy233.ardion.module.impl.render;

import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.impl.misc.AntiBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.util.player.PlayerUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public final class PigGod extends Module {
    public PigGod() {
        super("pig-god", ModuleCategory.RENDER);
    }

    public boolean shouldReplace(PlayerEntity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!this.isEnabled() || client.player == null || target == null) {
            return false;
        }
        if (target == client.player) {
            return false;
        }
        if (!target.isAlive() || target.isSpectator()) {
            return false;
        }

        AntiBot antiBot = ModuleManager.getModule(AntiBot.class);
        if (antiBot != null && antiBot.isBot(target)) {
            return false;
        }

        return !TargetSetting.teamCheck() || !PlayerUtil.isTeammate(client.player, target);
    }

    public static boolean shouldReplacePlayer(PlayerEntity target) {
        PigGod pigGod = ModuleManager.getModule(PigGod.class);
        return pigGod != null && pigGod.shouldReplace(target);
    }
}
