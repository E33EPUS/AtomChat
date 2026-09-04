package com.atom.chat.chat;

import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PrivateChatStoreTest {
    @BeforeEach
    void reset() {
        PrivateChatStore.reset();
    }

    private ChatMessage incoming(String name, String text) {
        return new ChatMessage(Text.literal(text), false, false,
                null, null, UUID.randomUUID(), name, name, text);
    }

    @Test
    void incomingAddsHistoryUnreadAndLatest() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PrivateChatStore.addIncoming(alice, incoming("Alice", "hello"));
        assertTrue(PrivateChatStore.hasHistory(alice));
        assertEquals(1, PrivateChatStore.unread(alice));
        assertEquals("hello", PrivateChatStore.latest(alice).getContentText());
    }

    @Test
    void markReadClearsUnread() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PrivateChatStore.addIncoming(alice, incoming("Alice", "hello"));
        PrivateChatStore.markRead(alice);
        assertEquals(0, PrivateChatStore.unread(alice));
    }

    @Test
    void activePartnerSuppressesUnread() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PrivateChatStore.setActive(alice);
        PrivateChatStore.addIncoming(alice, incoming("Alice", "hello"));
        assertEquals(0, PrivateChatStore.unread(alice));
        PrivateChatStore.clearActive();
        PrivateChatStore.addIncoming(alice, incoming("Alice", "again"));
        assertEquals(1, PrivateChatStore.unread(alice));
    }

    @Test
    void outgoingDoesNotIncreaseUnread() {
        PlayerRef bob = PlayerRef.of(UUID.randomUUID(), "Bob");
        PrivateChatStore.addOutgoing(bob, incoming("Me", "hi"));
        assertEquals(0, PrivateChatStore.unread(bob));
        assertTrue(PrivateChatStore.hasHistory(bob));
    }

    @Test
    void knownPartnersAreSortedByLatestDescending() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PlayerRef bob = PlayerRef.of(UUID.randomUUID(), "Bob");
        PrivateChatStore.addIncoming(bob, incoming("Bob", "old"));
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        PrivateChatStore.addIncoming(alice, incoming("Alice", "new"));
        assertEquals(alice, PrivateChatStore.knownPartnersByLatest().get(0));
        assertEquals(bob, PrivateChatStore.knownPartnersByLatest().get(1));
    }

    @Test
    void resetClearsAllConversations() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        PrivateChatStore.addIncoming(alice, incoming("Alice", "hello"));
        PrivateChatStore.reset();
        assertFalse(PrivateChatStore.hasHistory(alice));
        assertEquals(0, PrivateChatStore.knownPartners().size());
    }
}
