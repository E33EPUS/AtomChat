package com.atom.chat.chat;

import net.minecraft.text.Text;

import java.util.UUID;

public class ChatMessage {
    private final Text component;
    private final String rawText;
    private final long timestamp;
    private final boolean own;
    private final boolean system;
    private final String quoteName;
    private final String quoteText;
    private final UUID senderUuid;
    private final String senderName;
    private final String profileName;
    private final String contentText;

    public ChatMessage(Text component, boolean own) {
        this(component, own, false, null, null, null, null, null, null);
    }

    public ChatMessage(Text component, boolean own, boolean system) {
        this(component, own, system, null, null, null, null, null, null);
    }

    public ChatMessage(Text component, boolean own, String quoteName, String quoteText) {
        this(component, own, false, quoteName, quoteText, null, null, null, null);
    }

    public ChatMessage(Text component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText) {
        this.component = component;
        this.rawText = component.getString();
        this.timestamp = System.currentTimeMillis();
        this.own = own;
        this.system = system;
        this.quoteName = quoteName;
        this.quoteText = quoteText;
        this.senderUuid = senderUuid;
        this.senderName = clean(senderName);
        this.profileName = clean(profileName);
        this.contentText = contentText != null && !contentText.isBlank() ? clean(contentText) : null;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }

    public Text getComponent() {
        return component;
    }

    public String getRawText() {
        return rawText;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    /** Display name to show in the bubble; null for pure system lines. */
    public String getSenderName() {
        return senderName != null ? senderName : profileName;
    }

    /** Real profile name used for skin/identity lookups. */
    public String getProfileName() {
        return profileName != null ? profileName : senderName;
    }

    /**
     * Message content without the vanilla "&lt;sender&gt; " prefix, for copy/quote.
     * Nested quote prefixes ("「引用 @x: 「引用 @y: ...」 text」") are stripped
     * recursively so a quoted quote shows only the original message.
     */
    public String getContentText() {
        String text = contentText != null ? contentText : rawText;
        while (text.startsWith("<")) {
            int end = text.indexOf("> ");
            if (end > 0 && end + 2 < text.length()) {
                text = text.substring(end + 2);
            } else {
                break;
            }
        }
        while (text.startsWith("「引用")) {
            int end = text.indexOf('」');
            if (end >= 0 && end + 1 < text.length()) {
                text = text.substring(end + 1);
            } else {
                break;
            }
        }
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOwn() {
        return own;
    }

    /** Server/System line without a player sender (join, death, command feedback...). */
    public boolean isSystem() {
        return system;
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
