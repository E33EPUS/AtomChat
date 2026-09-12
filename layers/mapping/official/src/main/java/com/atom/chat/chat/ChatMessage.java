package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ChatMessage {
    private final Component component;
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
    private final RichText senderRich;
    private final RichText contentRich;
    /** 1 = ordinary message; >1 = anti-spam merged consecutive identical messages. */
    private final int duplicateCount;
    /**
     * Stable identity for UI state (selection anchors, jump highlight). An
     * anti-spam merge replaces the stored row object, so identity-by-reference
     * silently drops any state held on the message; copies made for a merge
     * carry this id over.
     */
    private final long id;

    private static final java.util.concurrent.atomic.AtomicLong ID_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();

    public ChatMessage(Component component, boolean own) {
        this(component, own, false);
    }

    public ChatMessage(Component component, boolean own, boolean system) {
        this(component, own, system, null, null, null, null, null, null);
    }

    public ChatMessage(Component component, boolean own, String quoteName, String quoteText) {
        this(component, own, false, quoteName, quoteText, null, null, null, null);
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                System.currentTimeMillis());
    }

    /** Variant used when rehydrating persisted history: keeps the original time. */
    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText, long timestamp) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                legacySenderRich(system, senderName, profileName),
                RichText.literal(legacyDisplayText(component.getString(), quoteName, contentText)).linkifyUrls(),
                timestamp);
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, System.currentTimeMillis());
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich, long timestamp) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, 1);
    }

    /** Full constructor with anti-spam merge count ({@code duplicateCount} 1 = normal). */
    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich, long timestamp, int duplicateCount) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, duplicateCount, ID_SEQUENCE.incrementAndGet());
    }

    /** Private tail: {@code id} lets merge copies keep the original identity. */
    private ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                        UUID senderUuid, String senderName, String profileName, String contentText,
                        RichText senderRich, RichText contentRich, long timestamp, int duplicateCount, long id) {
        this.component = component;
        this.rawText = component.getString();
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.own = own;
        this.system = system;
        this.quoteName = quoteName;
        this.quoteText = quoteText;
        this.senderUuid = senderUuid;
        this.senderName = clean(senderName);
        this.profileName = clean(profileName);
        this.contentText = contentText != null && !contentText.isBlank() ? clean(contentText) : null;
        this.senderRich = !system && senderRich != null ? senderRich
                : legacySenderRich(system, this.senderName, this.profileName);
        this.contentRich = contentRich != null ? contentRich
                : RichText.literal(legacyDisplayText(rawText, quoteName, this.contentText)).linkifyUrls();
        this.duplicateCount = Math.max(1, duplicateCount);
        this.id = id;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }

    private static String cleanContent(String s) {
        return s == null || s.isBlank() ? null : clean(s);
    }

    /** Rich text for the sender name shown in the bubble; empty for system lines. */
    private static RichText legacySenderRich(boolean system, String senderName, String profileName) {
        if (system) {
            return RichText.empty();
        }
        String name = clean(senderName);
        if (name == null) {
            name = clean(profileName);
        }
        return name != null ? RichText.literal(name) : RichText.empty();
    }

    /** Plain display string used by pre-rich constructors, matching legacy display behavior. */
    private static String legacyDisplayText(String rawText, String quoteName, String contentText) {
        if (quoteName != null && rawText.startsWith("「引用")) {
            int end = rawText.indexOf('」');
            if (end >= 0) {
                return rawText.substring(end + 1);
            }
        }
        return legacyContentText(rawText, contentText);
    }

    /** Plain content string used by pre-rich constructors, matching {@link #getContentText()}. */
    private static String legacyContentText(String rawText, String contentText) {
        String cleaned = cleanContent(contentText);
        String text = cleaned != null ? cleaned : rawText;
        // The vanilla "<sender> " prefix only exists on the final HUD line.
        // contentText is captured before decoration, so a message that really
        // starts with "<Alice> hi" must keep that text — only the raw fallback
        // needs the prefix stripped.
        if (cleaned == null) {
            while (text.startsWith("<")) {
                int end = text.indexOf("> ");
                if (end > 0 && end + 2 < text.length()) {
                    text = text.substring(end + 2);
                } else {
                    break;
                }
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

    public Component getComponent() {
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
        if (senderRich != null && !senderRich.isEmpty()) {
            String rich = senderRich.getString();
            if (rich != null && !rich.isBlank()) {
                return rich;
            }
        }
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
        return legacyContentText(rawText, contentText);
    }

    /** Rich sender part, or empty for system messages. */
    public RichText getSenderRich() {
        return senderRich;
    }

    /** Rich content part backing display text and future styled rendering. */
    public RichText getContentRich() {
        return contentRich;
    }

    /** Anti-spam merge count: 1 for a normal message, N for N identical sends. */
    public int getDuplicateCount() {
        return duplicateCount;
    }

    /** Copy with a different anti-spam merge count (used when a duplicate lands). */
    public ChatMessage withDuplicateCount(int count) {
        return new ChatMessage(component, own, system, quoteName, quoteText,
                senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, count, id);
    }

    /** Copy with a different anti-spam merge count and an updated timestamp. */
    public ChatMessage withDuplicateCount(int count, long newTimestamp) {
        return new ChatMessage(component, own, system, quoteName, quoteText,
                senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, newTimestamp, count, id);
    }

    public long getId() {
        return id;
    }

    /** Identity comparison that survives anti-spam merges replacing the row. */
    public boolean sameAs(ChatMessage other) {
        return other != null && (other == this || other.id == this.id);
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
     * Component to draw inside the bubble. Rich-content aware callers supply the final
     * content through {@link #getContentRich()}; legacy constructors populate that
     * rich part from the plain text after stripping quote/prefix decorations.
     */
    public String getDisplayText() {
        return contentRich.getString();
    }
}
