package com.atom.chat.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pending-echo bookkeeping for the identity-less relay path (2026-09-11: a
 * {@code /team} prefix made own messages arrive with a decorated name
 * ({@code 123E33EPUS}) and no channel identity, so only the text we just sent
 * can prove it is our echo).
 */
class PublicEchoTrackerTest {

    @AfterEach
    void cleanup() {
        PublicEchoTracker.clear();
    }

    @Test
    void sentTextIsConsumedByExactContent() {
        PublicEchoTracker.markSent("echo1");
        assertEquals(1, PublicEchoTracker.pendingCount());
        assertTrue(PublicEchoTracker.consumeIfEcho("echo1", "<123E33EPUS> echo1"));
        assertEquals(0, PublicEchoTracker.pendingCount());
    }

    @Test
    void rawLineSuffixAlsoMatches() {
        PublicEchoTracker.markSent("hello world");
        assertTrue(PublicEchoTracker.consumeIfEcho(null, "[VIP] Dev: hello world"));
    }

    @Test
    void unrelatedTextIsNotConsumed() {
        PublicEchoTracker.markSent("echo1");
        assertFalse(PublicEchoTracker.consumeIfEcho("echo2", "<Dev> echo2"));
        assertEquals(1, PublicEchoTracker.pendingCount(), "a miss must keep the pending send armed");
    }

    @Test
    void aPartialPrefixIsNotAMatch() {
        PublicEchoTracker.markSent("echo1");
        assertFalse(PublicEchoTracker.consumeIfEcho("echo", "<Dev> echo"));
        assertFalse(PublicEchoTracker.consumeIfEcho(null, "<Dev> echo"));
    }

    @Test
    void newestSendWinsWhenBothArePending() {
        PublicEchoTracker.markSent("first");
        PublicEchoTracker.markSent("second");
        assertTrue(PublicEchoTracker.consumeIfEcho("second", "<Dev> second"));
        assertEquals(1, PublicEchoTracker.pendingCount());
        // the older record is still armed for its own echo
        assertTrue(PublicEchoTracker.consumeIfEcho("first", "<Dev> first"));
    }

    @Test
    void staleRecordsArePurged() {
        PublicEchoTracker.markSent("echo1");
        PublicEchoTracker.purge(System.currentTimeMillis() + PublicEchoTracker.TTL_MS + 1);
        assertEquals(0, PublicEchoTracker.pendingCount());
        assertFalse(PublicEchoTracker.consumeIfEcho("echo1", "<Dev> echo1"));
    }

    @Test
    void blankAndNullSendsAreIgnored() {
        PublicEchoTracker.markSent(null);
        PublicEchoTracker.markSent("   ");
        assertEquals(0, PublicEchoTracker.pendingCount());
        assertFalse(PublicEchoTracker.consumeIfEcho(null, null));
    }

    @Test
    void clearDropsEverything() {
        PublicEchoTracker.markSent("echo1");
        PublicEchoTracker.clear();
        assertFalse(PublicEchoTracker.consumeIfEcho("echo1", "<Dev> echo1"));
    }
}
