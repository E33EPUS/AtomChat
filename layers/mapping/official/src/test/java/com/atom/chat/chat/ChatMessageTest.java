package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageTest {
    @Test
    void capturedContentThatLooksLikeAngleChatIsNotStripped() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "<Alice> hi");
        assertEquals("<Alice> hi", msg.getContentText());
    }

    @Test
    void rawFallbackStillStripsVanillaSenderPrefix() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", null);
        assertEquals("hi", msg.getContentText());
    }

    @Test
    void richPartsAreStored() {
        RichText sender = RichText.literal("Alice");
        RichText content = RichText.literal("hi").linkifyUrls();
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi", sender, content);
        assertEquals("Alice", msg.getSenderRich().getString());
        assertEquals("hi", msg.getContentRich().getString());
    }

    @Test
    void legacyConstructorBuildsPlainRichParts() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi");
        assertEquals("Alice", msg.getSenderRich().getString());
        assertEquals("hi", msg.getContentRich().getString());
    }

    @Test
    void legacyConstructorLinkifiesSynthesizedContent() {
        ChatMessage msg = new ChatMessage(Component.literal("see https://example.com/x now"), false, false,
                null, null, null, "Alice", "Alice", "see https://example.com/x now");
        assertTrue(msg.getContentRich().runs().stream().anyMatch(r -> r.style().getClickEvent() != null
                && r.style().getClickEvent().getAction() == net.minecraft.network.chat.ClickEvent.Action.OPEN_URL));
        assertEquals("see https://example.com/x now", msg.getContentRich().getString());
    }

    @Test
    void getSenderNamePrefersRichSenderText() {
        ChatMessage msg = new ChatMessage(Component.literal("<Alice> hi"), false, false,
                null, null, null, "Alice", "Alice", "hi",
                RichText.literal("[VIP]Alice"), RichText.literal("hi"));
        assertEquals("[VIP]Alice", msg.getSenderName());
    }

    @Test
    void systemMessageHasEmptySenderRich() {
        ChatMessage msg = new ChatMessage(Component.literal("Server: hello"), false, true,
                null, null, null, null, null, null);
        assertTrue(msg.getSenderRich().isEmpty());
        assertEquals("Server: hello", msg.getContentRich().getString());
    }

    @Test
    void fullConstructorForcesEmptySenderRichForSystemMessages() {
        RichText sender = RichText.literal("Server");
        RichText content = RichText.literal("Server: hello");
        ChatMessage msg = new ChatMessage(Component.literal("Server: hello"), false, true,
                null, null, null, "Server", "Server", "Server: hello", sender, content);
        assertTrue(msg.getSenderRich().isEmpty());
    }

    @Test
    void displayTextComesFromRichContent() {
        ChatMessage msg = new ChatMessage(Component.literal("prefix"), false, false,
                null, null, null, "Alice", "Alice", "plain", RichText.literal("Alice"),
                RichText.literal("rich hi"));
        assertEquals("rich hi", msg.getDisplayText());
    }

    @Test
    void legacyQuotedConstructorPreservesDisplayText() {
        ChatMessage msg = new ChatMessage(Component.literal("「引用 @Alice: hi」hello"), true, "Alice", "hi");
        assertEquals("hello", msg.getDisplayText());
    }

    @Test
    void messagesHaveDistinctIds() {
        ChatMessage a = new ChatMessage(Component.literal("a"), false, false, null, null,
                null, "Alice", "Alice", "a");
        ChatMessage b = new ChatMessage(Component.literal("b"), false, false, null, null,
                null, "Alice", "Alice", "b");
        assertTrue(a.getId() != b.getId());
        assertTrue(a.sameAs(a));
        assertFalse(a.sameAs(b));
    }

    @Test
    void mergeCopyKeepsIdentity() {
        ChatMessage msg = new ChatMessage(Component.literal("hi"), false, false, null, null,
                null, "Alice", "Alice", "hi");
        ChatMessage merged = msg.withDuplicateCount(2, 999L);
        assertTrue(merged.sameAs(msg));
        assertTrue(msg.sameAs(merged));
        assertEquals(2, merged.getDuplicateCount());
        assertEquals(999L, merged.getTimestamp());
    }

    @Test
    void sameAsRejectsNull() {
        ChatMessage msg = new ChatMessage(Component.literal("a"), false, false, null, null,
                null, "Alice", "Alice", "a");
        assertFalse(msg.sameAs(null));
    }
}
