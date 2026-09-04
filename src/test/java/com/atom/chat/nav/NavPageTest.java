package com.atom.chat.nav;

import com.atom.chat.chat.PlayerRef;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NavPageTest {
    @Test
    void privateChatRequiresTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new NavPage(AppPage.PRIVATE_CHAT, null));
    }

    @Test
    void nonPrivateForcesNullTarget() {
        NavPage page = new NavPage(AppPage.WORLD_CHAT, PlayerRef.of(UUID.randomUUID(), "Alice"));
        assertNull(page.target());
        assertEquals(AppPage.WORLD_CHAT, page.page());
    }

    @Test
    void rootClassification() {
        assertTrue(NavPage.of(AppPage.CHAT_LIST).isRoot());
        assertFalse(NavPage.of(AppPage.WORLD_CHAT).isRoot());
        assertFalse(NavPage.privateChat(PlayerRef.of(UUID.randomUUID(), "Alice")).isRoot());
    }
}
