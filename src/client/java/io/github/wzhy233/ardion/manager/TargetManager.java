package io.github.wzhy233.ardion.manager;

import io.github.wzhy233.ardion.module.impl.misc.AntiBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.util.player.PlayerUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public final class TargetManager {
    private TargetManager() {
    }

    public static boolean isValidTarget(PlayerEntity player, Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        World entityWorld = entity.getEntityWorld();
        if (client.world == null || entityWorld != client.world) {
            return false;
        }

        boolean validType;
        if (entity instanceof PlayerEntity target) {
            validType = isValidPlayerTarget(player, target);
        } else if (entity instanceof EndCrystalEntity) {
            validType = TargetSetting.crystal();
        } else if (entity instanceof AnimalEntity) {
            validType = TargetSetting.animal();
        } else if (entity instanceof VillagerEntity) {
            validType = TargetSetting.villager();
        } else if (entity instanceof Monster) {
            validType = TargetSetting.monster();
        } else {
            validType = false;
        }

        if (!validType) {
            return false;
        }
        return TargetSetting.invisibles() || !entity.isInvisible() && (player == null || !entity.isInvisibleTo(player));
    }

    public static boolean isValidPlayerTarget(PlayerEntity player, PlayerEntity target) {
        if (!TargetSetting.player() || target == null || target.isSpectator() || FriendManager.isFriend(target.getUuid())) {
            return false;
        }
        AntiBot antiBot = ModuleManager.getModule(AntiBot.class);
        if (antiBot != null && antiBot.isBot(target)) {
            return false;
        }
        if (player == null) {
            return true;
        }
        return target != player && (!TargetSetting.teamCheck() || !PlayerUtil.isTeammate(player, target));
    }
}
