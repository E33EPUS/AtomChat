package com.atom.chat.nav;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtomChatStateTest {
    private static NavPage np(AppPage page) {
        return NavPage.of(page);
    }

    @BeforeEach
    void reset() {
        AtomChatState.reset();
    }

    @Test
    void defaultIsChatList() {
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());
    }

    @Test
    void saveRestoreRoundTrip() {
        AtomChatState.save(List.of(np(AppPage.CHAT_LIST), np(AppPage.WORLD_CHAT)));
        assertEquals(List.of(np(AppPage.CHAT_LIST), np(AppPage.WORLD_CHAT)), AtomChatState.snapshot());
    }

    @Test
    void invalidSaveFallsBackToChatList() {
        AtomChatState.save(List.of(np(AppPage.WORLD_CHAT)));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());

        AtomChatState.save(List.of());
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());

        AtomChatState.save(null);
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());
    }

    @Test
    void saveRejectsRootAfterFirstPage() {
        AtomChatState.save(List.of(np(AppPage.CHAT_LIST), np(AppPage.PROFILE)));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());

        AtomChatState.save(List.of(np(AppPage.CHAT_LIST), np(AppPage.WORLD_CHAT), np(AppPage.SETTINGS)));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());
    }

    @Test
    void saveRejectsNullAnywhereInStack() {
        AtomChatState.save(Arrays.asList(null, np(AppPage.WORLD_CHAT)));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());

        AtomChatState.save(Arrays.asList(np(AppPage.CHAT_LIST), null));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());

        AtomChatState.save(Arrays.asList(np(AppPage.CHAT_LIST), np(AppPage.WORLD_CHAT), null));
        assertEquals(List.of(np(AppPage.CHAT_LIST)), AtomChatState.snapshot());
    }
}
