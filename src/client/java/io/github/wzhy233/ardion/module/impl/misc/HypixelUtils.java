package io.github.wzhy233.ardion.module.impl.misc;

import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.listener.Listener;
import io.github.wzhy233.ardion.module.base.Module;
import io.github.wzhy233.ardion.module.category.ModuleCategory;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.server.Server;
import io.github.wzhy233.ardion.util.server.ServerUtil;
import io.github.wzhy233.ardion.value.impl.BooleanValue;
import io.github.wzhy233.ardion.value.impl.StringValue;
import net.minecraft.client.MinecraftClient;

public final class HypixelUtils extends Module {
    private static final long AUTO_TIP_DELAY_MS = 1_200_000L;
    private final StringValue apiKey = this.registerValue(new StringValue("api-key", this, ""));
    private final BooleanValue levelHead = this.registerValue(new BooleanValue("level-head", this, false));
    private final BooleanValue autoTip = this.registerValue(new BooleanValue("auto-tip", this, false));
    private long lastTipTime;

    public HypixelUtils() {
        super("hypixel-utils", ModuleCategory.MISC);
    }

    public static String getApiKey() {
        HypixelUtils hypixelUtils = ModuleManager.getModule(HypixelUtils.class);
        return hypixelUtils == null ? "" : hypixelUtils.apiKey.getValue().trim();
    }

    public static boolean isLevelHeadEnabled() {
        HypixelUtils hypixelUtils = ModuleManager.getModule(HypixelUtils.class);
        return hypixelUtils != null && hypixelUtils.isEnabled() && hypixelUtils.levelHead.getValue();
    }

    @Listener
    public void onTick(MinecraftTickEvent event) {
        if (event.getStage() != MinecraftTickEvent.Stage.PRE) {
            return;
        }

        MinecraftClient client = event.getClient();
        if (!this.autoTip.getValue() || client.player == null || !ServerUtil.isJoined(Server.HYPIXEL)) {
            this.lastTipTime = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (this.lastTipTime != 0L && now - this.lastTipTime < AUTO_TIP_DELAY_MS) {
            return;
        }

        client.player.networkHandler.sendChatCommand("tip all");
        this.lastTipTime = now;
    }
}
