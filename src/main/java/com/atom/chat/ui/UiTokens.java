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
    public static final float HEADER_HEIGHT = s(56);
    public static final float HEADER_PAD_X = s(20);

    // Input bar
    public static final float INPUT_HEIGHT = s(76);
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
    public static final float FONT_TITLE = s(23);
    public static final float FONT_TIME = s(16);
    public static final float FONT_NAME = s(14);
    public static final float FONT_BODY = s(18);
    public static final float FONT_INPUT = s(18);
    public static final float FONT_BUTTON = s(16);
    public static final float FONT_EMOJI = s(26);
    public static final float FONT_QUOTE = s(14);

    // Messages
    public static final float AVATAR_SIZE = s(34);
    public static final float NAME_BAND = s(22);
    public static final float LIST_GAP = s(10);
    public static final float LIST_PAD_X = s(12);
    public static final float BUBBLE_RADIUS = s(12);
    public static final float BUBBLE_PAD = s(14);
    public static final float BUBBLE_MIN_W = s(36);
    public static final float BUBBLE_RETRACT = s(106);

    // Quote pill (e33chat style: a small capsule above the bubble)
    public static final float QUOTE_HEIGHT = s(20);
    public static final float QUOTE_GAP = s(2);
    public static final float QUOTE_PAD_X = s(5);

    // Emoji panel
    public static final float EMOJI_CELL = s(38);
    public static final int EMOJI_COLS = 6;

    // Context menu
    public static final float MENU_W = s(110);
    public static final float MENU_H = s(64);

    private UiTokens() {
    }
}
