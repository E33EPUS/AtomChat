package com.atom.chat.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour that has to hold no matter what the host machine looks like: a test
 * JVM is usually headless, a developer's machine is not, and the answer is
 * cached for the life of the process either way.
 */
class AwtDisplayTest {
    private static final Set<String> OUTCOMES = Set.of(
            AwtDisplay.CLAIMED, AwtDisplay.LOCKED_HEADLESS, AwtDisplay.UNAVAILABLE);

    @Test
    void claimIsIdempotentAndReportsAKnownOutcome() {
        String first = AwtDisplay.claim();
        assertEquals(first, AwtDisplay.claim(), "a repeated claim must not re-decide");
        assertTrue(OUTCOMES.contains(first), "unexpected outcome: " + first);
    }

    @Test
    void aSuccessfulClaimLeavesTheDisplayUsable() {
        if (AwtDisplay.CLAIMED.equals(AwtDisplay.claim())) {
            assertTrue(AwtDisplay.usable(), "claiming the toolkit must leave AWT usable");
        }
    }

    @Test
    void onlyAnUnavailableToolkitCarriesAFailure() {
        if (!AwtDisplay.UNAVAILABLE.equals(AwtDisplay.claim())) {
            assertNull(AwtDisplay.failure(), "a claim that did not fail carries no throwable");
        }
    }
}
