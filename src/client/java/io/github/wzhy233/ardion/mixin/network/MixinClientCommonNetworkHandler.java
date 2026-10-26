package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import io.github.wzhy233.ardion.module.manager.ModuleManager;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class MixinClientCommonNetworkHandler {
    @Shadow
    public abstract void sendPacket(net.minecraft.network.packet.Packet<?> packet);

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void spoofResourcePack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        Spoofer spoofer = ModuleManager.getModule(Spoofer.class);

        if (spoofer == null || !spoofer.isEnabled() || !spoofer.spoofsResourcePack()) {
            return;
        }

        UUID id = packet.id();
        this.sendResourcePackStatus(id, ResourcePackStatusC2SPacket.Status.ACCEPTED);
        this.sendResourcePackStatus(id, ResourcePackStatusC2SPacket.Status.DOWNLOADED);
        this.sendResourcePackStatus(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED);
        ci.cancel();
    }

    private void sendResourcePackStatus(UUID id, ResourcePackStatusC2SPacket.Status status) {
        this.sendPacket(new ResourcePackStatusC2SPacket(id, status));
    }
}
