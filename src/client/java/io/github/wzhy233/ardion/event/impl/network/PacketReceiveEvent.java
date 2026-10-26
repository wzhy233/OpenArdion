package io.github.wzhy233.ardion.event.impl.network;

import io.github.wzhy233.ardion.event.base.CancellableEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.packet.Packet;

public class PacketReceiveEvent extends CancellableEvent {
    private final ChannelHandlerContext context;
    private Packet<?> packet;

    public PacketReceiveEvent(ChannelHandlerContext context, Packet<?> packet) {
        this.context = context;
        this.packet = packet;
    }

    public ChannelHandlerContext getContext() {
        return this.context;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
