package io.github.wzhy233.ardion.event.impl.network;

import io.github.wzhy233.ardion.event.base.CancellableEvent;
import net.minecraft.network.packet.Packet;

public class PacketSendEvent extends CancellableEvent {
    private Packet<?> packet;

    public PacketSendEvent(Packet<?> packet) {
        this.packet = packet;
    }

    public Packet<?> getPacket() {
        return this.packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }
}
