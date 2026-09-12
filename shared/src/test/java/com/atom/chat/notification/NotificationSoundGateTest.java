package com.atom.chat.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSoundGateTest {

    @Test
    void firstCueIsAllowed() {
        assertTrue(NotificationSoundGate.shouldPlay(1_000L, NotificationSoundGate.NEVER));
    }

    @Test
    void cueInsideWindowIsGated() {
        assertFalse(NotificationSoundGate.shouldPlay(1_000L + 1_999L, 1_000L));
    }

    @Test
    void cueAtWindowEdgeIsAllowed() {
        assertTrue(NotificationSoundGate.shouldPlay(1_000L + 2_000L, 1_000L));
    }

    @Test
    void sentinelArithmeticDoesNotOverflow() {
        // Regression: now - Long.MIN_VALUE wraps negative. The explicit
        // sentinel branch must still let the first cue through.
        assertTrue(NotificationSoundGate.shouldPlay(System.currentTimeMillis(),
                NotificationSoundGate.NEVER));
    }
}
