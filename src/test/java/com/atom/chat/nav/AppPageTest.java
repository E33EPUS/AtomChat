package com.atom.chat.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppPageTest {
    @Test
    void rootClassification() {
        assertTrue(AppPage.CHAT_LIST.isRoot());
        assertTrue(AppPage.PROFILE.isRoot());
        assertTrue(AppPage.SETTINGS.isRoot());
        assertFalse(AppPage.WORLD_CHAT.isRoot());
    }

    @Test
    void idRoundTrip() {
        for (AppPage page : AppPage.values()) {
            assertEquals(page, AppPage.fromId(page.id()));
        }
    }

    @Test
    void unknownIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> AppPage.fromId("nope"));
    }
}
