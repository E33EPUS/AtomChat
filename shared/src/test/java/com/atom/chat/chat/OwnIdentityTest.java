package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cache rules only: {@code displayNameRich}'s tab/team branches need a running
 * client, so tests pin the local name through the supplier seam and exercise
 * the chat-cache layer (the source that usually wins on vanilla servers).
 */
class OwnIdentityTest {
    @AfterEach
    void restore() {
        OwnIdentity.localNameSupplier = () -> null;
        OwnIdentity.reset();
    }

    @Test
    void cachesDecoratedSelfNameSeenInChat() {
        OwnIdentity.localNameSupplier = () -> "E33EPUS";
        OwnIdentity.cache(RichText.literal("[称号]E33EPUS"));
        assertEquals("[称号]E33EPUS", OwnIdentity.displayNameRich().getString());
    }

    @Test
    void bareSelfNameIsNeverCached() {
        OwnIdentity.localNameSupplier = () -> "E33EPUS";
        OwnIdentity.cache(RichText.literal("E33EPUS"));
        assertEquals("E33EPUS", OwnIdentity.displayNameRich().getString());
    }

    @Test
    void blankNameIsNeverCached() {
        OwnIdentity.localNameSupplier = () -> "E33EPUS";
        OwnIdentity.cache(RichText.literal("  "));
        assertEquals("E33EPUS", OwnIdentity.displayNameRich().getString());
    }

    @Test
    void resetDropsTheCachedName() {
        OwnIdentity.localNameSupplier = () -> "E33EPUS";
        OwnIdentity.cache(RichText.literal("[VIP]Steve"));
        OwnIdentity.reset();
        assertEquals("E33EPUS", OwnIdentity.displayNameRich().getString());
    }
}
