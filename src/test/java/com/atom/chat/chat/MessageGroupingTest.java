package com.atom.chat.chat;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageGroupingTest {
    private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("Bob".getBytes());

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
    void sameSenderWithinWindowIsGrouped() {
        assertTrue(MessageGrouping.isSameGroup(msg("Alice", "a", 1000), msg("Alice", "b", 1000 + 5 * 60_000L)));
    }

    @Test
    void sameSenderOutsideWindowIsNotGrouped() {
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "a", 1000), msg("Alice", "b", 1000 + 5 * 60_000L + 1L)));
    }

    @Test
    void differentSendersNeverGroup() {
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "a", 1000), msg("Bob", "b", 2000)));
    }

    @Test
    void systemMessagesNeverGroup() {
        ChatMessage a = new ChatMessage(Component.literal("Server"), false, true, null, null,
                null, null, null, null, 1000);
        ChatMessage b = new ChatMessage(Component.literal("Server"), false, true, null, null,
                null, null, null, null, 2000);
        assertFalse(MessageGrouping.isSameGroup(a, b));
    }

    @Test
    void ownAndOtherNeverGroupEvenWithSameName() {
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "a", 1000), own("Alice", "b", 2000)));
    }

    @Test
    void groupedGapIsTightAndNeverBelowTwoDesignPx() {
        float listGap = 12.0F;
        assertEquals(4.0F, MessageGrouping.groupedGap(listGap, 2.0F));
        assertEquals(2.0F, MessageGrouping.groupedGap(4.0F, 2.0F));
    }
}
