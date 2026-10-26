package io.github.wzhy233.ardion.client;

import io.github.wzhy233.ardion.command.manager.CommandManager;
import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.event.impl.render.Render2DAfterSkiaEvent;
import io.github.wzhy233.ardion.event.impl.render.Render2DEvent;
import io.github.wzhy233.ardion.event.impl.render.Render3DEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.hypixel.HypixelManager;
import io.github.wzhy233.ardion.irc.IrcLaunchSession;
import io.github.wzhy233.ardion.manager.RotationManager;
import io.github.wzhy233.ardion.music.MusicPlayerService;
import io.github.wzhy233.ardion.module.manager.BindManager;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.notebot.Service;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import io.github.wzhy233.ardion.web.WebServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;

import static io.github.wzhy233.ardion.Ardion.LOGGER;

public class ArdionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        IrcLaunchSession.capture(MinecraftClient.getInstance().getSession());
        EventManager.init();
        EventManager.register(new BindManager());
        EventManager.register(new ConfigManager());
        EventManager.register(RotationManager.get());
        HypixelManager.init();
        ModuleManager.init();
        EventManager.register(io.github.wzhy233.ardion.gui.ui.UiManager.INSTANCE);
        Service.init();
        MusicPlayerService.init();
        io.github.wzhy233.mcp.LuaRuntime.loadAll();
        ConfigManager.load();
        CommandManager.init();
        WebServer.start();

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            float tickDelta = tickCounter.getTickProgress(false);
            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();
            EventManager.post(new Render2DEvent(drawContext, tickDelta, scaledWidth, scaledHeight));
            SkiaRenderManager skia = SkiaRenderManager.get();
            if (skia.hasPendingDraws()) {
                skia.drawFrame(drawContext, scaledWidth, scaledHeight, tickDelta);
            } else {
                skia.releaseIfIdle();
            }
            EventManager.post(new Render2DAfterSkiaEvent(drawContext, tickDelta, scaledWidth, scaledHeight));
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            if (context.matrices() == null) {
                return;
            }
            float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
            EventManager.post(new Render3DEvent(context.matrices(), context.consumers(), tickDelta));
        });

        LOGGER.info("Ardion has been initialized!");
    }
}
