package com.atom.chat.chat;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFilterTest {
    private ChatMessage system(String text) {
        return new ChatMessage(Text.literal(text), false, true, null, null,
                null, null, null, null);
    }

    private ChatMessage player(String name, String text) {
        return new ChatMessage(Text.literal(text), false, false, null, null,
                null, name, name, text);
    }

    @Test
    void cycleIsAllSystemPlayers() {
        assertEquals(MessageFilter.SYSTEM, MessageFilter.ALL.next());
        assertEquals(MessageFilter.PLAYERS, MessageFilter.SYSTEM.next());
        assertEquals(MessageFilter.ALL, MessageFilter.PLAYERS.next());
    }

    @Test
    void allAcceptsEverything() {
        assertTrue(MessageFilter.ALL.accepts(system("join")));
        assertTrue(MessageFilter.ALL.accepts(player("Alice", "hi")));
    }

    @Test
    void systemOnlyAcceptsSystemMessages() {
        assertTrue(MessageFilter.SYSTEM.accepts(system("join")));
        assertFalse(MessageFilter.SYSTEM.accepts(player("Alice", "hi")));
    }

    @Test
    void playersOnlyAcceptsPlayerMessages() {
        assertTrue(MessageFilter.PLAYERS.accepts(player("Alice", "hi")));
        assertFalse(MessageFilter.PLAYERS.accepts(system("join")));
        assertTrue(MessageFilter.PLAYERS.accepts(player("Alice", "hi").withDuplicateCount(2)));
    }

    @Test
    void onlyAllIsNonFiltering() {
        assertFalse(MessageFilter.ALL.isFiltering());
        assertTrue(MessageFilter.SYSTEM.isFiltering());
        assertTrue(MessageFilter.PLAYERS.isFiltering());
    }
}
