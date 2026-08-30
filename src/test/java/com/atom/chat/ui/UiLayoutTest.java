package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout formula verification without launching the game. Any new UI rule
 * (alignment, breathing space, no-overlap) gets an assertion here.
 */
class UiLayoutTest {
    private static final float EPS = 0.01F;

    private static void assertSane(UiLayout l) {
        UiLayout.Rect panel = l.rect();

        // Everything lives inside the panel.
        assertTrue(panel.contains(l.header), "header inside panel");
        assertTrue(panel.contains(l.list), "list inside panel");
        assertTrue(panel.contains(l.inputBar), "input bar inside panel");
        assertTrue(panel.contains(l.imageBtn), "image button inside panel");
        assertTrue(panel.contains(l.emojiBtn), "emoji button inside panel");
        assertTrue(panel.contains(l.sendBtn), "send button inside panel");

        // Buttons never overlap each other.
        assertTrue(l.imageBtn.right() <= l.emojiBtn.x() + EPS, "image/emoji do not overlap");
        assertTrue(l.emojiBtn.right() <= l.sendBtn.x() + EPS, "emoji/send do not overlap");

        // Button row is vertically aligned and mirrors padding on both sides.
        assertEquals(l.imageBtn.y(), l.emojiBtn.y(), EPS, "buttons share one row");
        assertEquals(l.imageBtn.y(), l.sendBtn.y(), EPS, "send button on the same row");
        assertEquals(l.imageBtn.h(), l.sendBtn.h(), EPS, "send button same size");
        assertEquals(l.imageBtn.x() - l.inputBar.x(), l.inputBar.right() - l.sendBtn.right(), EPS,
                "button row padding mirrors left/right");
        assertEquals(l.imageBtn.x() - l.inputBar.x(), UiTokens.INPUT_ROW_PAD, EPS, "row uses INPUT_ROW_PAD");

        // Breathing space below the input bar.
        assertEquals(panel.bottom() - l.inputBar.bottom(), UiTokens.PANEL_BOTTOM_PAD, EPS, "bottom breathing space");

        // The message list ends above the input bar.
        assertTrue(l.list.bottom() <= l.inputBar.y() + EPS, "list above input bar");
        assertTrue(l.list.h() > 0, "list has room");
    }

    @Test
    void defaultSize() {
        assertSane(UiLayout.of(24, 100, 525, 975));
    }

    @Test
    void clampedSmallPanel() {
        // panel clamps to the window; simulate a short window result
        assertSane(UiLayout.of(24, 8, 400, 300));
    }

    @Test
    void veryLargePanel() {
        assertSane(UiLayout.of(24, 10, 900, 1400));
    }

    @Test
    void minimumClampStillFits() {
        // panelWidth()/panelHeight() clamp to viewport-32; the smallest realistic case
        assertSane(UiLayout.of(24, 16, 360, 280));
    }
}
