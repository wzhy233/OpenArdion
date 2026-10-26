package io.github.wzhy233.ardion.mixin.render;

import io.github.wzhy233.ardion.ai.AiService;
import io.github.wzhy233.ardion.event.impl.network.ChatReceiveEvent;
import io.github.wzhy233.ardion.event.manager.EventManager;
import io.github.wzhy233.ardion.module.impl.misc.Spoofer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class MixinChatHud {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void onMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        EventManager.post(new ChatReceiveEvent(message));
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("RETURN"))
    private void pinStreamingAi(CallbackInfo ci) {
        AiService.holdBottom((ChatHud) (Object) this);
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text modifyMessage(Text message) {
        return Spoofer.spoof(message);
    }

    @ModifyConstant(method = "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", constant = @Constant(intValue = 100))
    private int unlimitedVisibleMessages(int limit) {
        return Integer.MAX_VALUE;
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", constant = @Constant(intValue = 100))
    private int unlimitedMessages(int limit) {
        return Integer.MAX_VALUE;
    }

    @ModifyConstant(method = "addToMessageHistory(Ljava/lang/String;)V", constant = @Constant(intValue = 100))
    private int unlimitedMessageHistory(int limit) {
        return Integer.MAX_VALUE;
    }
}
