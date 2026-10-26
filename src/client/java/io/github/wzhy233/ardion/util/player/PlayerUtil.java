package io.github.wzhy233.ardion.util.player;

import io.github.wzhy233.ardion.module.impl.misc.AntiBot;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.setting.TargetSetting;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;
import io.github.wzhy233.ardion.util.text.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.awt.*;

public final class PlayerUtil {
    private PlayerUtil() {
    }

    public static MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    public static PlayerEntity getPlayer() {
        return getClient().player;
    }

    public static World getWorld() {
        return getClient().world;
    }

    public static boolean isInGame() {
        return getPlayer() != null && getWorld() != null;
    }

    public static boolean isTeammate(PlayerEntity player, PlayerEntity target) {
        if (!TargetSetting.teamCheck()) {
            return false;
        }
        String mode = TargetSetting.getTeamCheckMode().getValue();
        if (mode.equalsIgnoreCase("scoreboard")) {
            return isScoreboardTeammate(player, target);
        }
        if (mode.equalsIgnoreCase("armor")) {
            return isArmorTeammate(player, target);
        }
        return false;
    }

    public static boolean isScoreboardTeammate(PlayerEntity player, PlayerEntity target) {
        if (player == null || target == null || player.getEntityWorld() == null || target.getEntityWorld() == null) return false;
        return player.getTeamColorValue() == target.getTeamColorValue();
    }

    public static boolean isArmorTeammate(PlayerEntity player, PlayerEntity target) {
        if (player == null || target == null) return false;

        Integer playerHelmetColor = getHelmetColor(player);
        Integer targetHelmetColor = getHelmetColor(target);

        return playerHelmetColor != null && playerHelmetColor.equals(targetHelmetColor);
    }

    public static boolean isBot(PlayerEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || target == mc.player || mc.getNetworkHandler() == null) {
            return false;
        }

        AntiBot antiBot = ModuleManager.getModule(AntiBot.class);
        if (antiBot != null && antiBot.isEnabled()) {
            return antiBot.isBot(target);
        }

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());
        if (entry == null) {
            entry = mc.getNetworkHandler().getPlayerListEntry(target.getName().getString());
        }
        if (entry == null) {
            return ServerUtil.isMultiplayer();
        }

        if (!ServerUtil.isJoined(Server.HYPIXEL)) {
            return false;
        }

        String name = target.getName().getString();
        if (name.startsWith("\u00A7k")) {
            return target.isInvisible();
        }
        if (entry.getLatency() < 1) {
            return true;
        }

        Team team = entry.getScoreboardTeam();
        if (team == null || !team.getName().isEmpty()) {
            return false;
        }
        Text prefix = team.getPrefix();
        return team.getColor() == Formatting.RED
                || prefix != null && "\u00A7c".equals(prefix.getString());
    }

    public static Color getTeamColor(PlayerEntity target, Color fallback, int alpha) {
        if (target == null) {
            return withAlpha(fallback, alpha);
        }

        Team team = target.getScoreboardTeam();
        if (team != null) {
            Integer colorValue = team.getColor().getColorValue();
            if (colorValue != null) {
                return withAlpha(new Color(colorValue), alpha);
            }
        }

        int colorValue = target.getTeamColorValue();
        if ((colorValue & 0xFFFFFF) != 0xFFFFFF) {
            return withAlpha(new Color(colorValue), alpha);
        }
        return withAlpha(fallback, alpha);
    }

    public static PlayerListEntry getPlayerListEntry(PlayerEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || mc.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());
        return entry == null ? mc.getNetworkHandler().getPlayerListEntry(target.getName().getString()) : entry;
    }

    private static Color withAlpha(Color color, int alpha) {
        if (color == null) {
            color = Color.WHITE;
        }
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Integer getHelmetColor(PlayerEntity player) {
        ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        DyedColorComponent color = helmet.get(DataComponentTypes.DYED_COLOR);

        return color == null ? null : color.rgb();
    }

    public static void sendClientMessage(Text text) {
        if (!isInGame()) {
            return;
        }
        getPlayer().sendMessage(text, false);
    }

    public static void sendClientMessage(String text) {
        sendClientMessage(Text.literal(TextUtil.safe(text)));
    }

    public static float getHealth(PlayerEntity player) {
        return getHealth(player, GlobalSetting.targetHudHealthSource());
    }

    public static float getHealth(PlayerEntity player, String source) {
        if (player == null) return 0.0F;

        Integer score = null;
        if ("sb-a".equalsIgnoreCase(source)) {
            score = getListHealth(player);
        } else if ("sb-b".equalsIgnoreCase(source)) {
            score = getBelowNameHealth(player);
        }
        return score != null ? score.floatValue() : player.getHealth();
    }

    public static float getDisplayHealth(PlayerEntity player) {
        return getHealth(player);
    }

    public static float getDisplayMaxHealth(PlayerEntity player) {
        if (player == null) return 0.5F;
        return Math.max(player.getMaxHealth(), 0.5F);
    }

    public static float getHealthRatio(PlayerEntity player) {
        if (player == null) return 0.0F;
        return MathHelper.clamp(getDisplayHealth(player) / getDisplayMaxHealth(player), 0.0F, 1.0F);
    }

    public static Integer getListHealth(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        if (objective != null) {
            ReadableScoreboardScore score = scoreboard.getScore(ScoreHolder.fromName(player.getName().getString()), objective);
            if (score != null) return score.getScore();
        }

        return null;
    }

    public static Integer getBelowNameHealth(PlayerEntity player) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective belowObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME);
        if (belowObjective != null) {
            ReadableScoreboardScore score = scoreboard.getScore(ScoreHolder.fromName(player.getName().getString()), belowObjective);
            if (score != null) return score.getScore();
        }

        return null;
    }
}
