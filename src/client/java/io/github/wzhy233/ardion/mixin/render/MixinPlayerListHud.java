package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.gui.hud.island.TabIsland;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.impl.render.BetterTab;
import io.github.wzhy233.ardion.module.impl.render.HUD;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.setting.HudSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PlayerListHud.class)
public class MixinPlayerListHud {
    @Shadow private Text header;
    @Shadow private Text footer;
    @Shadow private List<PlayerListEntry> collectPlayerEntries() { throw new AssertionError(); }
    @Shadow public Text getPlayerName(PlayerListEntry entry) { throw new AssertionError(); }

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void modifyPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        Text name = spoof(cir.getReturnValue());
        BetterTab betterTab = ModuleManager.getModule(BetterTab.class);
        if (betterTab != null && betterTab.isEnabled()) {
            name = betterTab.getPlayerName(entry, name);
        }
        cir.setReturnValue(name);
    }

    @ModifyVariable(method = "setHeader", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyHeader(Text header) {
        return spoof(header);
    }

    @ModifyVariable(method = "setFooter", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyFooter(Text footer) {
        return spoof(footer);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderDynamicIslandTab(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard,
                                        ScoreboardObjective objective, CallbackInfo ci) {
        HUD hud = ModuleManager.getModule(HUD.class);
        if (hud == null || !hud.isEnabled()
                || !HudSetting.dynamicIsland() || !HudSetting.islandTab()) {
            HUD.hideTabIsland();
            return;
        }

        HUD.showTabIsland(new TabIsland.Snapshot(
                ardion$collectEntries(scoreboard, objective),
                ardion$safeText(header),
                ardion$safeText(footer),
                PlayerListHud.MAX_ROWS
        ));
        ci.cancel();
    }

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void hideDynamicIslandTab(boolean visible, CallbackInfo ci) {
        if (!visible) {
            HUD.hideTabIsland();
        }
    }

    private Text spoof(Text text) {
        return Spoofer.spoof(text);
    }

    @Unique
    private List<TabIsland.Entry> ardion$collectEntries(Scoreboard scoreboard, ScoreboardObjective objective) {
        return collectPlayerEntries().stream()
                .map(entry -> ardion$entry(entry, scoreboard, objective))
                .toList();
    }

    @Unique
    private TabIsland.Entry ardion$entry(PlayerListEntry entry, Scoreboard scoreboard, ScoreboardObjective objective) {
        Text playerName = getPlayerName(entry);
        Text score = ardion$score(entry, scoreboard, objective);
        return new TabIsland.Entry(
                ardion$safeText(playerName),
                entry.getLatency(),
                entry.getGameMode() == GameMode.SPECTATOR,
                score,
                objective != null && objective.getRenderType() == ScoreboardCriterion.RenderType.HEARTS,
                ardion$nameColor(entry, playerName)
        );
    }

    @Unique
    private int ardion$nameColor(PlayerListEntry entry, Text playerName) {
        Team team = entry.getScoreboardTeam();
        if (team != null && team.getColor().getColorValue() != null) {
            return team.getColor().getColorValue();
        }
        if (playerName != null && playerName.getStyle().getColor() != null) {
            return playerName.getStyle().getColor().getRgb();
        }
        return 0xFFFFFF;
    }

    @Unique
    private Text ardion$score(PlayerListEntry entry, Scoreboard scoreboard, ScoreboardObjective objective) {
        if (scoreboard == null || objective == null) {
            return Text.empty();
        }

        ReadableScoreboardScore score = scoreboard.getScore(ScoreHolder.fromProfile(entry.getProfile()), objective);
        if (score == null) {
            return Text.empty();
        }
        if (objective.getRenderType() == ScoreboardCriterion.RenderType.HEARTS) {
            return Text.literal(Math.max(0, score.getScore()) + " hp").withColor(0xFF6170);
        }

        try {
            return ardion$safeText(ReadableScoreboardScore.getFormattedScore(score,
                    objective.getNumberFormatOr(net.minecraft.scoreboard.number.StyledNumberFormat.YELLOW)));
        } catch (Throwable ignored) {
            return Text.literal(String.valueOf(score.getScore()));
        }
    }

    @Unique
    private Text ardion$safeText(Text text) {
        Text spoofed = Spoofer.spoof(text);
        return spoofed == null ? Text.empty() : spoofed.copy();
    }
}
