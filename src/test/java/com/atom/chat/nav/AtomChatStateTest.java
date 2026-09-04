package com.atom.chat.nav;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtomChatStateTest {
    @BeforeEach
    void reset() {
        AtomChatState.reset();
    }

    @Test
    void defaultIsChatList() {
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }

    @Test
    void saveRestoreRoundTrip() {
        AtomChatState.save(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT));
        assertEquals(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT), AtomChatState.snapshot());
    }

    @Test
    void invalidSaveFallsBackToChatList() {
        AtomChatState.save(List.of(AppPage.WORLD_CHAT));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(List.of());
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(null);
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }

    @Test
    void saveRejectsRootAfterFirstPage() {
        AtomChatState.save(List.of(AppPage.CHAT_LIST, AppPage.PROFILE));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT, AppPage.SETTINGS));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }

    @Test
    void saveRejectsNullAnywhereInStack() {
        AtomChatState.save(Arrays.asList(null, AppPage.WORLD_CHAT));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(Arrays.asList(AppPage.CHAT_LIST, null));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());

        AtomChatState.save(Arrays.asList(AppPage.CHAT_LIST, AppPage.WORLD_CHAT, null));
        assertEquals(List.of(AppPage.CHAT_LIST), AtomChatState.snapshot());
    }
}
