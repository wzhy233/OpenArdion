package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.ardion.event.impl.network.PacketReceiveEvent;
import io.github.wzhy233.ardion.event.impl.network.PacketSendEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.util.network.PacketUtil;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class MixinClientConnection {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        if (PacketUtil.consumeQueued(packet)) {
            return;
        }

        PacketSendEvent event = EventManager.post(new PacketSendEvent(packet));
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.getPacket() != packet) {
            ci.cancel();
            ((ClientConnection) (Object) this).send(event.getPacket());
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onChannelRead(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (PacketUtil.consumeQueued(packet)) {
            return;
        }

        PacketReceiveEvent event = EventManager.post(new PacketReceiveEvent(context, packet));
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (event.getPacket() != packet) {
            ci.cancel();
            PacketUtil.receiveQueued(context, event.getPacket());
        }
    }
}
