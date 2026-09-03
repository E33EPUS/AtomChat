package com.atom.chat.chat;

import java.util.UUID;

/**
 * Structured identity captured at the MessageHandler channel level, before
 * Minecraft decorates the line for the chat HUD. It travels through a short
 * TTL handoff to {@link ChatHudMixin}, which builds the final {@link ChatMessage}.
 *
 * @param senderUuid   real player UUID when known, else {@code null} (nil UUID is
 *                     stored as null to keep "unknown" explicit)
 * @param senderName   display name to show in the bubble (tab/nick/decorated name
 *                     when known, otherwise profile/raw name)
 * @param profileName  real profile name used for skin/identity lookups
 * @param contentText  message body without sender decorations (may be null when
 *                     the consumer must fall back to the full decorated line)
 * @param system       true when the line is a system/broadcast message
 */
public record SenderMeta(UUID senderUuid, String senderName, String profileName,
                         String contentText, boolean system) {
    public SenderMeta {
        senderName = clean(senderName);
        profileName = clean(profileName);
        contentText = clean(contentText);
    }

    public static SenderMeta systemMeta() {
        return new SenderMeta(null, null, null, null, true);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }
}
