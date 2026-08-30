package com.atom.chat.chat;

import net.minecraft.text.Text;

public class ChatMessage {
    private final Text component;
    private final String rawText;
    private final long timestamp;
    private final boolean own;
    private final String quoteName;
    private final String quoteText;

    public ChatMessage(Text component, boolean own) {
        this(component, own, null, null);
    }

    public ChatMessage(Text component, boolean own, String quoteName, String quoteText) {
        this.component = component;
        this.rawText = component.getString();
        this.timestamp = System.currentTimeMillis();
        this.own = own;
        this.quoteName = quoteName;
        this.quoteText = quoteText;
    }

    public Text getComponent() {
        return component;
    }

    public String getRawText() {
        return rawText;
    }

    /**
     * Message content without the vanilla "&lt;sender&gt; " prefix, for copy/quote.
     */
    public String getContentText() {
        if (rawText.startsWith("<")) {
            int end = rawText.indexOf("> ");
            if (end > 0 && end + 2 < rawText.length()) {
                return rawText.substring(end + 2);
            }
        }
        return rawText;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOwn() {
        return own;
    }

    public String getQuoteName() {
        return quoteName;
    }

    public String getQuoteText() {
        return quoteText;
    }

    /**
     * Text to draw inside the bubble: for quoted own messages, the wire prefix
     * "「引用 @name: snippet」" is rendered as a quote block instead.
     */
    public String getDisplayText() {
        if (quoteName != null && rawText.startsWith("「引用")) {
            int end = rawText.indexOf('」');
            if (end >= 0) {
                return rawText.substring(end + 1);
            }
        }
        return getContentText();
    }
}
