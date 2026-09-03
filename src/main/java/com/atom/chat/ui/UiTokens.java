package com.atom.chat.ui;

/**
 * Single source of truth for every UI size. No layout code may hardcode pixel
 * values; one-off paddings go through s() so the global scale stays coherent.
 */
public final class UiTokens {
    /** Global multiplier — bump this to scale the whole UI. */
    public static final float SCALE = 1.25F;

    public static float s(float v) {
        return v * SCALE;
    }

    // Panel
    public static final float PANEL_RADIUS = s(28);
    public static final float PANEL_ANCHOR_X = s(24);
    public static final float PANEL_TOP_GAP = s(8);

    // Header
    public static final float HEADER_HEIGHT = s(44);
    public static final float HEADER_RADIUS = s(16);
    public static final float HEADER_PAD_X = s(20);

    // Input bar. INPUT_HEIGHT is the one-line baseline; the bar grows upward by
    // one line height while the text wraps onto a second line and never beyond
    // INPUT_MAX_LINES — past that the text scrolls inside the fixed box.
    public static final float INPUT_HEIGHT = s(76);
    public static final int INPUT_MAX_LINES = 2;
    public static final float INPUT_BAR_PAD = s(12);
    public static final float INPUT_ROW_PAD = s(8);
    public static final float INPUT_TEXT_X = s(14);
    public static final float PANEL_BOTTOM_PAD = s(14);

    // Buttons (image / emoji / send share one row and one size)
    public static final float BUTTON_W = s(56);
    public static final float BUTTON_H = s(30);
    public static final float BUTTON_RADIUS = s(9);
    public static final float BUTTON_GAP = s(6);

    // Fonts
    public static final float FONT_TITLE = s(19);
    public static final float FONT_TIME = s(16);
    public static final float FONT_NAME = s(16);
    public static final float FONT_BODY = s(18);
    public static final float FONT_INPUT = s(18);
    public static final float FONT_BUTTON = s(16);
    public static final float FONT_EMOJI = s(22);
    public static final float FONT_KAOMOJI = s(17);
    public static final float FONT_QUOTE = s(15);

    // Messages
    public static final float AVATAR_SIZE = s(40);
    /** Gap between the avatar's outer edge and the bubble. */
    public static final float AVATAR_GAP = s(8);
    /** Name band doubles as the gap between the name and the bubble top. */
    public static final float NAME_BAND = s(26);
    public static final float LIST_GAP = s(10);
    public static final float LIST_PAD_X = s(12);
    public static final float BUBBLE_RADIUS = s(12);
    /** Horizontal bubble padding (both sides). Shared by drawing and messageHeight(). */
    public static final float BUBBLE_PAD = s(12);
    /** Vertical bubble padding. Shared by drawing and messageHeight() — never inline it. */
    public static final float BUBBLE_PAD_Y = s(11);
    /** Same, for the centered system-message capsule. */
    public static final float SYSTEM_BUBBLE_PAD_Y = s(6);
    public static final float BUBBLE_MIN_W = s(24);
    public static final float BUBBLE_RETRACT = s(106);
    /** Image card height (also mirrored in messageHeight()). */
    public static final float IMAGE_HEIGHT = s(140);
    /**
     * Horizontal QQ-style entrance slide distance (own from right, other from
     * left). Kept small on purpose: a long travel dominates the fade and the
     * entrance reads as "a bubble flew in" instead of "a bubble appeared".
     */
    public static final float MESSAGE_SLIDE = s(14);

    // Quote pill (e33chat style: a small capsule above the bubble)
    public static final float QUOTE_HEIGHT = s(24);
    public static final float QUOTE_GAP = s(3);
    public static final float QUOTE_PAD_X = s(8);

    // Emoji panel. Sized so 8 columns fill most of the 420-wide panel:
    // cell width is (panelW - LIST_PAD_X*2 - PANEL_PAD*2) / EMOJI_COLS.
    public static final float EMOJI_CELL = s(34);
    public static final int EMOJI_COLS = 8;
    public static final int EMOJI_VISIBLE_ROWS = 5;
    public static final float EMOJI_TAB_H = s(34);
    public static final float EMOJI_PANEL_PAD = s(12);
    /** Kaomoji rows hold long strings, so they get their own (taller) row height. */
    public static final float EMOJI_KAOMOJI_ROW_H = s(24);

    // Context menu
    public static final float MENU_W = s(110);
    public static final float MENU_H = s(64);

    // Panel background blur (gated by AtomChatConfig.blurEnabled). The tint sits
    // on top of the blurred snapshot so text stays legible without smothering
    // it — 0x66 read as a bare oil film over the world, 0x99 keeps the panel's
    // blue-grey character while still letting the blur show through.
    public static final float PANEL_BLUR_SIGMA = 30.0F;
    public static final int PANEL_BLUR_TINT = 0xCC16191F;

    private UiTokens() {
    }
}
