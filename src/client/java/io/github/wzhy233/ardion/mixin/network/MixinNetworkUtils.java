package io.github.wzhy233.ardion.mixin.network;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import io.github.wzhy233.ardion.util.client.SpooferProtectionUtil;
import net.minecraft.util.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

@Mixin(NetworkUtils.class)
public class MixinNetworkUtils {
    @Inject(method = "download", at = @At("HEAD"), cancellable = true)
    private static void blockLocalResourcePackRequest(Path path, URL url, Map<String, String> headers, HashFunction hashFunction, HashCode hashCode, int maxBytes, Proxy proxy, NetworkUtils.DownloadListener listener, CallbackInfoReturnable<Path> cir) {
        if (!SpooferProtectionUtil.protectsLocalHttp() || !SpooferProtectionUtil.isLocalUrl(url)) {
            return;
        }

        listener.onStart();
        listener.onFinish(false);
        throw new IllegalStateException("Blocked local resource pack request: " + url);
    }
}
