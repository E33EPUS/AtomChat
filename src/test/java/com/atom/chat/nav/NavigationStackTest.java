package com.atom.chat.nav;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NavigationStackTest {
    @Test
    void startsAtRootAndPushes() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        assertEquals(AppPage.CHAT_LIST, stack.peek());
        assertEquals(1, stack.size());

        stack.push(AppPage.WORLD_CHAT);
        assertEquals(AppPage.WORLD_CHAT, stack.peek());
        assertEquals(2, stack.size());
    }

    @Test
    void popReturnsFalseAtSingleRoot() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        assertFalse(stack.pop());
        assertEquals(AppPage.CHAT_LIST, stack.peek());
    }

    @Test
    void popReturnsTrueAndRestoresPreviousPage() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        assertTrue(stack.pop());
        assertEquals(AppPage.CHAT_LIST, stack.peek());
        assertEquals(1, stack.size());
    }

    @Test
    void snapshotIsUnmodifiableCopy() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        List<AppPage> snap = stack.snapshot();
        assertEquals(List.of(AppPage.CHAT_LIST, AppPage.WORLD_CHAT), snap);
        assertThrows(UnsupportedOperationException.class, () -> snap.add(AppPage.PROFILE));
    }

    @Test
    void replaceWithRootClearsStack() {
        NavigationStack<AppPage> stack = new NavigationStack<>(AppPage.CHAT_LIST);
        stack.push(AppPage.WORLD_CHAT);
        stack.replaceWithRoot(AppPage.SETTINGS);
        assertEquals(1, stack.size());
        assertEquals(AppPage.SETTINGS, stack.peek());
    }
}
