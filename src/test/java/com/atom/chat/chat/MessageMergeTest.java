package com.atom.chat.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMergeTest {
    private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("Bob".getBytes());

    @BeforeEach
    void resetStores() {
        ChatStore.reset();
        PrivateChatStore.reset();
        MessageMerge.antiSpamEnabledSupplier = () -> true;
    }

    @AfterEach
    void restoreSupplier() {
        MessageMerge.antiSpamEnabledSupplier = () -> false;
    }

    private ChatMessage msg(String name, String text, long time) {
        UUID uuid = name.equals("Alice") ? ALICE : BOB;
        return new ChatMessage(Component.literal(text), false, false, null, null,
                uuid, name, name, text, time);
    }

    private ChatMessage own(String name, String text, long time) {
        UUID uuid = name.equals("Alice") ? ALICE : BOB;
        return new ChatMessage(Component.literal(text), true, false, null, null,
                uuid, name, name, text, time);
    }

    @Test
    void canMergeSameSenderSameContent() {
        assertTrue(MessageMerge.canMerge(msg("Alice", "hi", 1000), msg("Alice", "hi", 2000)));
    }

    @Test
    void cannotMergeDifferentContent() {
        assertFalse(MessageMerge.canMerge(msg("Alice", "hi", 1000), msg("Alice", "bye", 2000)));
    }

    @Test
    void cannotMergeDifferentSpeakers() {
        assertFalse(MessageMerge.canMerge(msg("Alice", "hi", 1000), msg("Bob", "hi", 2000)));
    }

    @Test
    void cannotMergeSystemMessages() {
        ChatMessage a = new ChatMessage(Component.literal("Server"), false, true, null, null,
                null, null, null, null, 1000);
        ChatMessage b = new ChatMessage(Component.literal("Server"), false, true, null, null,
                null, null, null, null, 2000);
        assertFalse(MessageMerge.canMerge(a, b));
    }

    @Test
    void cannotMergeAcrossOwnershipSides() {
        assertFalse(MessageMerge.canMerge(msg("Alice", "hi", 1000), own("Alice", "hi", 2000)));
    }

    @Test
    void mergeIncrementsCounterAndKeepsNewTimestamp() {
        ChatMessage merged = MessageMerge.merge(msg("Alice", "hi", 1000), msg("Alice", "hi", 4000));
        assertEquals(2, merged.getDuplicateCount());
        assertEquals(4000, merged.getTimestamp());
    }

    @Test
    void chatStoreFoldsDuplicateIntoOneRowWithoutSecondUnread() {
        ChatMessage first = msg("Alice", "spam", 1000);
        ChatMessage second = msg("Alice", "spam", 2000);
        assertFalse(ChatStore.get().add(first));
        assertEquals(1, ChatStore.publicUnread());
        assertTrue(ChatStore.get().add(second));
        assertEquals(1, ChatStore.publicUnread());
        assertEquals(1, ChatStore.get().snapshot().size());
        assertEquals(2, ChatStore.get().snapshot().get(0).getDuplicateCount());
    }

    @Test
    void privateStoreFoldsDuplicateIncomingWithoutSecondUnread() {
        PlayerRef alice = PlayerRef.of(UUID.randomUUID(), "Alice");
        ChatMessage first = msg("Alice", "hi", 1000);
        ChatMessage second = msg("Alice", "hi", 2000);
        assertFalse(PrivateChatStore.addIncoming(alice, first));
        assertEquals(1, PrivateChatStore.unread(alice));
        assertTrue(PrivateChatStore.addIncoming(alice, second));
        assertEquals(1, PrivateChatStore.unread(alice));
        assertEquals(1, PrivateChatStore.messages(alice).size());
        assertEquals(2, PrivateChatStore.messages(alice).get(0).getDuplicateCount());
    }
}
