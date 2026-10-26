package io.github.wzhy233.ardion.module.impl.render;

import io.github.humbleui.skija.Font;
import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.hypixel.HypixelManager;
import io.github.wzhy233.ardion.hypixel.HypixelUser;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.util.render.Fonts;
import io.github.wzhy233.ardion.util.render.Skia;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;
import io.github.wzhy233.ardion.value.impl.NumberValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.*;

public final class BedwarsStatsOverlay extends Module {
    private static final float WIDTH = 294.0F;
    private static final float HEADER_HEIGHT = 34.0F;
    private static final float ROW_HEIGHT = 15.0F;
    private final NumberValue x = this.registerValue(new NumberValue("x", this, 8.0D, 0.0D, 4000.0D, true));
    private final NumberValue y = this.registerValue(new NumberValue("y", this, 70.0D, 0.0D, 4000.0D, true));
    private final NumberValue scale = this.registerValue(new NumberValue("scale", this, 1.0D, 0.5D, 2.0D, false));
    private final NumberValue max = this.registerValue(new NumberValue("max", this, 16.0D, 1.0D, 30.0D, true));

    public BedwarsStatsOverlay() {
        super("bedwars-stats-overlay", ModuleCategory.RENDER);
    }

    @Listener
    public void onRender(Render2DEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null || client.getNetworkHandler() == null || !ServerUtil.isJoined(Server.HYPIXEL)) {
            return;
        }

        Skia.query(canvas -> render(client));
    }

    private void render(MinecraftClient client) {
        float drawX = this.x.getValue().floatValue();
        float drawY = this.y.getValue().floatValue();
        float drawScale = this.scale.getValue().floatValue();
        int shown = 0;
        int maxRows = this.max.getValue().intValue();

        Skia.save();
        Skia.translate(drawX, drawY);
        Skia.scale(drawScale);

        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            HypixelUser user = user(entry);
            if (user != null && !user.isUnknown()) {
                shown++;
            }
            if (shown >= maxRows) {
                break;
            }
        }

        float height = HEADER_HEIGHT + shown * ROW_HEIGHT;
        drawBackground(height);
        drawHeader();

        int row = 0;
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            HypixelUser user = user(entry);
            if (user == null || user.isUnknown()) {
                continue;
            }
            drawRow(entry.getProfile().name(), user, row++);
            if (row >= maxRows) {
                break;
            }
        }

        Skia.restore();
    }

    private void drawBackground(float height) {
        Skia.drawShadow(0.0F, 0.0F, WIDTH, height, 6.0F);
        Skia.drawRoundedRect(0.0F, 0.0F, WIDTH, height, 6.0F, new Color(16, 18, 22, 185));
        Skia.drawOutline(0.5F, 0.5F, WIDTH - 1.0F, height - 1.0F, 5.0F, 1.0F, new Color(255, 255, 255, 34));
    }

    private void drawHeader() {
        Font title = Fonts.getMedium(10.0F);
        Font label = Fonts.getMedium(9.0F);
        Color primary = new Color(255, 255, 255, 235);
        Color secondary = new Color(190, 205, 225, 225);
        Skia.drawText("Bedwars Stats", 5.5F, 5.5F, primary, title);
        Skia.drawRect(0.0F, 18.0F, WIDTH, 1.0F, new Color(255, 255, 255, 42));
        Skia.drawFullCenteredText("Name", 45.0F, 27.0F, secondary, label);
        Skia.drawFullCenteredText("Level", 120.0F, 27.0F, secondary, label);
        Skia.drawFullCenteredText("WLR", 170.0F, 27.0F, secondary, label);
        Skia.drawFullCenteredText("FKDR", 220.0F, 27.0F, secondary, label);
        Skia.drawFullCenteredText("BBLR", 270.0F, 27.0F, secondary, label);
    }

    private void drawRow(String name, HypixelUser user, int row) {
        float centerY = HEADER_HEIGHT + row * ROW_HEIGHT + 6.0F;
        Font font = Fonts.getRegular(9.0F);
        Color text = new Color(255, 255, 255, 225);
        String limitedName = Skia.getLimitTextWithDot(name, font, 88.0F);
        Skia.drawHeightCenteredText(limitedName, 8.0F, centerY, text, font);
        Skia.drawFullCenteredText(user.bedwarsLevel(), 120.0F, centerY, text, font);
        Skia.drawFullCenteredText(user.winLoseRatio(), 170.0F, centerY, text, font);
        Skia.drawFullCenteredText(user.finalKillDeathRatio(), 220.0F, centerY, text, font);
        Skia.drawFullCenteredText(user.bedsBrokeLostRatio(), 270.0F, centerY, text, font);
    }

    private HypixelUser user(PlayerListEntry entry) {
        if (entry == null || entry.getProfile() == null || entry.getProfile().id() == null) {
            return null;
        }
        return HypixelManager.getByUuid(entry.getProfile().id().toString());
    }
}
