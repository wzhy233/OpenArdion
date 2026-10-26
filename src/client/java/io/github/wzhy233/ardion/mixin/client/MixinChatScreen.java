package io.github.wzhy233.ardion.mixin.client;

import io.github.wzhy233.ardion.setting.GlobalSetting;
import io.github.wzhy233.ardion.ai.AiService;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.wzhy233.ardion.module.impl.misc.IRC;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void sendIrcMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (IRC.interceptChat(message) || AiService.interceptChat(message)) {
            if (addToHistory) net.minecraft.client.MinecraftClient.getInstance()
                    .inGameHud.getChatHud().addToMessageHistory(message);
            ci.cancel();
        }
    }

    @ModifyVariable(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String modifyChatMessage(String chatText) {
        return rewrite(chatText);
    }

    private String rewrite(String chatText) {
        if (chatText == null || chatText.isEmpty()) {
            return chatText;
        }
        if (chatText.startsWith("$")) return chatText;

        String channel = GlobalSetting.getChatChannel().getValue();
        if (chatText.startsWith("/")) return chatText;
        return switch (channel.toLowerCase()) {
            case "normal" -> chatText;
            case "party" -> "/pc " + chatText;
            case "shout" -> "/shout " + chatText;
            case "custom" -> GlobalSetting.getChatChannelPrefix().getValue() + " " + chatText;
            case "irc" -> chatText;
            case "ai" -> chatText;
            default -> chatText;
        };
    }
}
