package io.github.wzhy233.ardion.mixin.client;

import io.github.wzhy233.ardion.config.ConfigManager;
import io.github.wzhy233.ardion.event.impl.client.ClickEvent;
import io.github.wzhy233.ardion.event.impl.client.MinecraftTickEvent;
import io.github.wzhy233.ardion.event.impl.render.ViewEvent;
import io.github.wzhy233.ardion.event.impl.world.WorldLoadEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.world.BedNuker;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import io.github.wzhy233.ardion.util.render.SkiaRenderManager;
import io.github.wzhy233.ardion.web.WebServer;
import io.github.wzhy233.mcp.McpServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MixinMinecraft {
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void agentItemUse(CallbackInfo ci) {
        if (io.github.wzhy233.mcp.GameTools.isUsingItem()) ci.cancel();
    }
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void agentMining(boolean breaking, CallbackInfo ci) {
        if (io.github.wzhy233.mcp.GameTools.isMining()) ci.cancel();
        BedNuker bedNuker = ModuleManager.getModule(BedNuker.class);
        if (bedNuker != null && bedNuker.isBusy()) ci.cancel();
    }
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void transitionArdionScreen(Screen screen, CallbackInfo ci) {
        if (io.github.wzhy233.ardion.gui.ui.UiManager.deferScreenChange(screen)) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        EventManager.post(new MinecraftTickEvent(client, MinecraftTickEvent.Stage.PRE));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        EventManager.post(new MinecraftTickEvent(client, MinecraftTickEvent.Stage.POST));
    }

    @Inject(method = "joinWorld", at = @At("TAIL"))
    private void onJoinWorld(ClientWorld world, CallbackInfo ci) {
        EventManager.post(new WorldLoadEvent(world));
    }

    @Inject(method = "getCameraEntity", at = @At("HEAD"))
    private void onGetCameraEntity(CallbackInfoReturnable<Entity> cir) {
        EventManager.post(new ViewEvent());
    }

    @Inject(
            method = "handleInputEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void onHandleInputEvents(CallbackInfo ci) {
        ClickEvent event = EventManager.post(new ClickEvent());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V", at = @At("HEAD"))
    private void onDisconnect(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        if (!EventManager.isInitialized()) {
            return;
        }
        ConfigManager.save();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        if (!EventManager.isInitialized()) {
            return;
        }
        ConfigManager.save();
        SkiaRenderManager.get().destroy();
        WebServer.stop();
        McpServer.stop();
    }
}
