package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timing guards. The UI once used {@code v += (target - v) * dt / D}, whose
 * asymptotic tail kept a hover highlight lit for ~550ms after the pointer
 * left. These tests pin the two properties that fix it: a transition finishes
 * within its declared duration, and it lands exactly on the target.
 */
class UiMotionTest {
    private static final float EPS = 0.0001F;

    /** Simulates frames of frameMs and returns the value after durationMs elapses. */
    private static float run(float from, float to, long durationMs, long frameMs) {
        float value = from;
        for (long elapsed = 0; elapsed < durationMs; elapsed += frameMs) {
            value = UiMotion.approach(value, to, frameMs, durationMs);
        }
        return value;
    }

    @Test
    void hoverClearsWithinDeclaredDuration() {
        // Regression: the pointer left the button, the highlight must be gone.
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 16L), EPS,
                "hover fully clears within HOVER_MS");
    }

    @Test
    void hoverClearingIsFrameRateIndependent() {
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 5L), EPS, "200fps clears in time");
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 33L), EPS, "30fps clears in time");
        assertEquals(0.0F, run(1.0F, 0.0F, UiMotion.HOVER_MS, 50L), EPS, "frame spike clears in time");
    }

    @Test
    void approachSnapsExactlyToTarget() {
        // No asymptotic residue: scrollbar alpha / popup fade must hit 0 or 1.
        float v = 1.0F;
        for (int i = 0; i < 1000; i++) {
            v = UiMotion.approach(v, 0.0F, 16L, UiMotion.SCROLLBAR_FADE_MS);
        }
        assertEquals(0.0F, v, 0.0F, "no residual value left behind");
        assertEquals(1.0F, UiMotion.approach(0.0F, 1.0F, 10_000L, UiMotion.POPUP_MS), 0.0F);
    }

    @Test
    void approachNeverOvershoots() {
        float v = 0.0F;
        for (int i = 0; i < 60; i++) {
            v = UiMotion.approach(v, 1.0F, 16L, UiMotion.HOVER_MS);
            assertTrue(v >= 0.0F && v <= 1.0F, "value stays inside [0,1]");
        }
    }

    @Test
    void durationsStaySnappy() {
        // Any transition slower than this reads as sticky on screen.
        long[] all = {
                UiMotion.PANEL_MS, UiMotion.MESSAGE_MS, UiMotion.SCROLL_SNAP_MS,
                UiMotion.SCROLL_WHEEL_MS, UiMotion.HOVER_MS, UiMotion.SCROLLBAR_FADE_MS,
                UiMotion.SCROLLBAR_EMPHASIS_MS, UiMotion.POPUP_MS
        };
        for (long ms : all) {
            assertTrue(ms > 0 && ms <= 200, "duration " + ms + "ms must stay at or under 200ms");
        }
    }
}
