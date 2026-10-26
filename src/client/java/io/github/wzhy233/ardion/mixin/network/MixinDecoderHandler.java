package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.ardion.util.client.SpooferProtectionUtil;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DecoderHandler.class)
public class MixinDecoderHandler {
    @Redirect(method = "decode", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object markRemoteTextDecode(PacketCodec<Object, Object> codec, Object buf) {
        SpooferProtectionUtil.setDecodingRemotePacket(true);
        try {
            return codec.decode(buf);
        } finally {
            SpooferProtectionUtil.setDecodingRemotePacket(false);
        }
    }
}
