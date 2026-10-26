package io.github.wzhy233.ardion.mixin.network;

import io.github.wzhy233.ardion.util.client.SpooferProtectionUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Downloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;
import java.util.UUID;

@Mixin(Downloader.class)
public class MixinDownloader {
    @Redirect(method = "method_55485", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"))
    private Path isolateServerPackCache(Path directory, String fileName, Downloader.Config config, Downloader.DownloadResult result, UUID uuid, Downloader.DownloadEntry entry) {
        if (!SpooferProtectionUtil.protectsFingerprinting()) {
            return directory.resolve(fileName);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSession() == null || client.getSession().getUuidOrNull() == null) {
            return directory.resolve(fileName);
        }

        return directory.resolve(client.getSession().getUuidOrNull().toString()).resolve(fileName);
    }
}
