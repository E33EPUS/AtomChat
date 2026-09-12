package com.atom.chat.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatStoreMentionTest {
    @BeforeEach
    void reset() {
        ChatStore.reset();
    }

    @Test
    void mentionsAccumulateAndClearOnActive() {
        ChatStore.noteMention();
        ChatStore.noteMention();
        assertEquals(2, ChatStore.mentionUnread());
        ChatStore.setPublicActive(true);
        assertEquals(0, ChatStore.mentionUnread());
        assertEquals(0, ChatStore.publicUnread());
    }

    @Test
    void markPublicReadAlsoClearsMentions() {
        ChatStore.noteMention();
        ChatStore.markPublicRead();
        assertEquals(0, ChatStore.mentionUnread());
    }

    @Test
    void resetClearsMentions() {
        ChatStore.noteMention();
        ChatStore.reset();
        assertEquals(0, ChatStore.mentionUnread());
    }
}
