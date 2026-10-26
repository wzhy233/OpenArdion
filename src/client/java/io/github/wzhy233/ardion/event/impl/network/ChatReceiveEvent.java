package io.github.wzhy233.ardion.event.impl.network;

import io.github.wzhy233.ardion.event.base.Event;
import net.minecraft.text.Text;

public class ChatReceiveEvent extends Event {
    private final Text message;

    public ChatReceiveEvent(Text message) {
        this.message = message;
    }

    public Text getMessage() {
        return this.message;
    }

    public String getPlainMessage() {
        return this.message == null ? "" : this.message.getString();
    }
}
