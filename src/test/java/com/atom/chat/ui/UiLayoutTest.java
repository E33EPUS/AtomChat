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

        // The message list stays inside the panel and has room. A grown input
        // bar yields the list's bottom, so no list content sits under the
        // translucent composer.
        assertTrue(panel.contains(l.list), "list inside panel");
        assertTrue(l.list.h() > 0, "list has room");
    }

    @Test
    void defaultSize() {
        assertSane(UiLayout.of(24, 100, 525, 975));
    }

    @Test
    void grownInputBarYieldsListHeight() {
        float lineH = 29.0F; // one wrapped line of the input font
        UiLayout base = UiLayout.of(24, 100, 525, 975);

        for (float extra : new float[]{0.0F, lineH * 0.5F, lineH}) {
            UiLayout grown = UiLayout.of(24, 100, 525, 975, extra);
            assertSane(grown);
            // The bar grows upward: its bottom edge is nailed to the panel.
            assertEquals(base.inputBar.bottom(), grown.inputBar.bottom(), EPS,
                    "bar bottom stays put, extra " + extra);
            assertEquals(UiTokens.INPUT_HEIGHT + extra, grown.inputBar.h(), EPS,
                    "bar height grows by the requested amount");
            // The list top stays put and the list gives up exactly the extra
            // height, so its bottom always meets the grown bar's top — nothing
            // is ever painted underneath the translucent bar.
            assertEquals(base.list.y(), grown.list.y(), EPS, "list top stays put");
            assertEquals(base.list.h() - extra, grown.list.h(), EPS, "list yields the extra height");
            assertEquals(grown.inputBar.y(), grown.list.bottom(), EPS,
                    "list bottom meets the grown bar top, no overlap");
            // Buttons ride up with the bar's top edge.
            assertEquals(grown.inputBar.y() + UiTokens.INPUT_ROW_PAD, grown.sendBtn.y(), EPS,
                    "button row pinned to the bar top");
        }
    }

    @Test
    void messageTokensStayConsistent() {
        // The avatar column on both sides must fit inside the bubble's reserved
        // retract, or bubbles would overlap the avatars.
        assertTrue(UiTokens.BUBBLE_RETRACT > 2.0F * (UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP),
                "bubble retract covers both avatar columns");
        assertTrue(UiTokens.BUBBLE_PAD_Y > 0.0F, "bubble padding is positive");
        assertTrue(UiTokens.SYSTEM_BUBBLE_PAD_Y > 0.0F, "system bubble padding is positive");
        // The name band doubles as the name/bubble gap, so it must clear the name text.
        assertTrue(UiTokens.NAME_BAND > UiTokens.FONT_NAME, "name band clears the name text");
    }

    @Test
    void headerIsCompactAndMirrorsTopGap() {
        UiLayout l = UiLayout.of(24, 100, 525, 975);

        // Height comes from the shared token only — no local override may creep back in.
        assertEquals(UiTokens.HEADER_HEIGHT, l.header.h(), EPS, "header uses HEADER_HEIGHT");
        // The channel card must not dominate the panel; the input bar stays taller.
        assertTrue(l.header.h() < l.inputBar.h(), "header shorter than the input bar");
        // Top breathing space mirrors the bottom one.
        assertEquals(UiTokens.PANEL_BOTTOM_PAD, l.header.y() - l.rect().y(), EPS, "top gap mirrors bottom pad");
        // Centering the channel label needs room on both sides of the card.
        assertTrue(l.header.w() > UiTokens.HEADER_PAD_X * 4.0F, "header wide enough to center a label");
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
