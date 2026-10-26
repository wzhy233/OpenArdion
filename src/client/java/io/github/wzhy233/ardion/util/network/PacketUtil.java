package io.github.wzhy233.ardion.util.network;

import io.github.wzhy233.ardion.mixin.network.ClientConnectionAccessor;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PacketUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Set<Packet<?>> QUEUED_PACKETS = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>())
    );

    private PacketUtil() {
    }

    public static boolean consumeQueued(Packet<?> packet) {
        return QUEUED_PACKETS.remove(packet);
    }

    public static void receiveQueued(ChannelHandlerContext context, Packet<?> packet) {
        QUEUED_PACKETS.add(packet);
        ((ClientConnectionAccessor) context.handler()).ardion$channelRead0(context, packet);
    }

    public static void sendQueued(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) {
            return;
        }

        QUEUED_PACKETS.add(packet);
        mc.getNetworkHandler().sendPacket(packet);
    }
}
