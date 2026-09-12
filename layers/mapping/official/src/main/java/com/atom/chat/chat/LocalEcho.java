package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Builds the local copy of an outgoing own message.
 *
 * <p>Quote replies travel as {@code 「引用 @Name: text」body} so every client can
 * reconstruct the capsule without a server plugin. The local echo must keep that
 * prefix on the <em>wire</em> text (image/CICode extraction still reads it) but
 * render only {@code body} inside the bubble — passing the wire text as the
 * content made the prefix show up as an extra line under the capsule.
 */
public final class LocalEcho {
    private LocalEcho() {
    }

    public static ChatMessage build(String wireText, String body, String quoteName, String quoteText,
                                    UUID ownUuid, String ownProfile, RichText senderRich) {
        String content = body == null ? "" : body;
        return new ChatMessage(Component.literal(wireText), true, false, quoteName, quoteText,
                ownUuid, ownProfile, ownProfile, content,
                senderRich, RichText.literal(content).linkifyUrls());
    }
}
