package com.atom.chat.chat;

import net.minecraft.text.Text;

public class ChatMessage {
    private final Text component;
    private final String rawText;
    private final long timestamp;
    private final boolean own;

    public ChatMessage(Text component, boolean own) {
        this.component = component;
        this.rawText = component.getString();
        this.timestamp = System.currentTimeMillis();
        this.own = own;
    }

    public Text getComponent() {
        return component;
    }

    public String getRawText() {
        return rawText;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOwn() {
        return own;
    }
}
