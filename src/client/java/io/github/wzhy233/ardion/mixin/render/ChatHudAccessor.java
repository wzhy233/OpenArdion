package io.github.wzhy233.ardion.mixin.render;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("messages")
    List<ChatHudLine> ardion$getMessages();

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> ardion$getVisibleMessages();

    @Invoker("addVisibleMessage")
    void ardion$addVisibleMessage(ChatHudLine line);

    @Invoker("getVisibleLineCount")
    int ardion$getVisibleLineCount();

    @Accessor("scrolledLines")
    int ardion$getScrolledLines();

    @Accessor("scrolledLines")
    void ardion$setScrolledLines(int lines);

    @Accessor("hasUnreadNewMessages")
    boolean ardion$hasUnreadNewMessages();

    @Accessor("hasUnreadNewMessages")
    void ardion$setUnreadNewMessages(boolean unread);

}
