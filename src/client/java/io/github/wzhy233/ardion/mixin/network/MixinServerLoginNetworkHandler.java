package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.mcp.GameBotTools;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLoginNetworkHandler.class)
public abstract class MixinServerLoginNetworkHandler {
    @Shadow @Final private ClientConnection connection;

    @Redirect(method = "onHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isOnlineMode()Z"))
    private boolean ardionLocalAlexLogin(MinecraftServer server, LoginHelloC2SPacket packet) {
        return !GameBotTools.consumeLocalLogin(server, connection.getAddress(), packet.name()) && server.isOnlineMode();
    }
}
