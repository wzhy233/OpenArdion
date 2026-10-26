package io.github.wzhy233.ardion.mixin.network;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientConnection.class)
public interface ClientConnectionAccessor {
    @Invoker("channelRead0")
    void ardion$channelRead0(ChannelHandlerContext context, Packet<?> packet);
}
