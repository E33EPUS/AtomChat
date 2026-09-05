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
    /**
     * Image messages are scaled to fit this box and the bubble then hugs the
     * result, so there is no stretching, cropping or letterboxing. Also the
     * fallback size while the image is still downloading.
     */
    public static final float IMAGE_MAX_W = s(220);
    public static final float IMAGE_MAX_H = s(140);
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

    // Emote (sticker) grid: bigger cells than emoji so the images read clearly.
    // The pack cap of 10 fills two rows of six, so the grid never needs to scroll.
    public static final int EMOTE_COLS = 6;
    public static final float EMOTE_CELL = s(44);
    /** Corner remove button shown on a hovered emote cell. */
    public static final float EMOTE_REMOVE_SIZE = s(14);

    // Context menu
    public static final float MENU_W = s(110);
    public static final float MENU_H = s(64);
    /** Context-menu icon size; also the reference size for icon stroke scaling. */
    public static final float CONTEXT_ICON_SIZE = s(16);

    // Icon line language. All AtomChat SVG paths use a hand-written line-icon
    // language. Stroke width follows an optical taper rather than a strict
    // linear scale: tiny 16px icons keep 1.5 stroke, 24px uses 2.0 and 32px
    // uses 2.5 (the svg-design reference table), so large tab icons do not get
    // proportionally heavy.
    public static final float ICON_STROKE_REF = s(1.5F);
    public static final float ICON_STROKE_LARGE = s(2.5F);
    public static final float ICON_LARGE_SIZE = s(32);

    /** Returns the stroke width (UI units) that matches {@code iconSize}. */
    public static float iconStroke(float iconSize) {
        if (iconSize <= CONTEXT_ICON_SIZE) {
            return ICON_STROKE_REF;
        }
        float t = (iconSize - CONTEXT_ICON_SIZE) / (ICON_LARGE_SIZE - CONTEXT_ICON_SIZE);
        return ICON_STROKE_REF + t * (ICON_STROKE_LARGE - ICON_STROKE_REF);
    }

    // Bottom tab bar (root pages only; hidden on detail pages). Icon-only:
    // no text labels, so the bar is a formula layout around the icon:
    //   bar height = icon size
    //              + 2 * capsule padding (icon -> highlight pill)
    //              + 2 * edge padding    (highlight pill -> bar edge)
    // The pill is full-cell wide; the icon sits on the pill's vertical centre,
    // and the pill keeps s(8) breathing room from the bar edge on all sides.
    public static final float TAB_EDGE_PAD = s(8);
    public static final float TAB_CAPSULE_PAD = s(4);
    public static final float TAB_ICON_SIZE = s(24);
    public static final float TAB_BAR_H = TAB_ICON_SIZE + 2.0F * (TAB_CAPSULE_PAD + TAB_EDGE_PAD);
    /** Vertical inset between the root content list and content rows/controls. */
    public static final float ROOT_CONTENT_GAP = s(10);

    // Settings home: a 2-column tile grid. Tile width is derived from the list
    // width ((listW - TILE_GAP) / 2 = 188.75 at the default 420 panel), so the
    // grid always fills the column exactly and never needs its own constant.
    public static final int SETTINGS_TILE_COLS = 2;
    /**
     * Tiles are square: the side equals the computed tile width, so the grid is
     * always 2x2 of perfect squares regardless of panel width. The glyph and
     * its label form one centred group inside that square.
     */
    public static final float SETTINGS_TILE_GAP = s(10);
    public static final float SETTINGS_TILE_RADIUS = s(12);
    public static final float SETTINGS_TILE_ICON = s(34);
    public static final float SETTINGS_TILE_TITLE = s(15);
    public static final float SETTINGS_TILE_SUB = s(12);
    /** Gap between the glyph and its label inside the centred group. */
    public static final float SETTINGS_TILE_TEXT_GAP = s(10);

    // Settings section rows (switch items, blocked players, about entries).
    public static final float SETTINGS_ROW_H = s(56);
    /** About-page hero card: logo plate on the left, wordmark on the right. */
    public static final float SETTINGS_HERO_H = s(88);
    public static final float SETTINGS_HERO_PLATE = s(56);
    public static final float SETTINGS_HERO_FONT = s(24);
    public static final float SETTINGS_ROW_GAP = s(8);
    public static final float SETTINGS_ROW_PAD = s(14);
    public static final float SETTINGS_ROW_RADIUS = s(12);
    /** Group heading inside a section (e.g. the blocked-players list title). */
    public static final float SETTINGS_LABEL_H = s(32);
    /** Glyph size for the "nothing here" empty state under a group heading. */
    public static final float SETTINGS_EMPTY_ICON = s(40);
    /** Avatar inside a blocked-player row. */
    public static final float SETTINGS_ROW_AVATAR = s(36);

    // Profile page: hero identity card (large circular avatar with an edit
    // badge) above an info-card list of copyable rows.
    public static final float PROFILE_AVATAR = s(96);
    public static final float PROFILE_AVATAR_HERO_H = s(180);
    public static final float PROFILE_EDIT_BADGE = s(28);
    public static final float PROFILE_NAME_FONT = s(20);
    public static final float PROFILE_ROW_H = s(48);
    public static final float PROFILE_ROW_PAD = s(14);
    public static final float PROFILE_ROW_RADIUS = s(12);
    public static final float PROFILE_ROW_FONT = s(15);
    public static final float PROFILE_ROW_VALUE_FONT = s(13);

    // Slider row: title line on top, track below. The knob is deliberately a
    // touch larger than the track so it reads as a handle, not a filled bar.
    public static final float SETTINGS_SLIDER_ROW_H = s(64);
    public static final float SETTINGS_SLIDER_TRACK_Y = s(40);
    public static final float SLIDER_TRACK_H = s(6);
    public static final float SLIDER_KNOB = s(18);

    // Toggle switch. Geometry is iOS-proportioned: knob diameter is track
    // height minus the two insets, so the travel is exactly
    // SWITCH_W - SWITCH_KNOB - 2 * SWITCH_INSET = s(40) - s(18) - s(4) = 22.5.
    public static final float SWITCH_W = s(40);
    public static final float SWITCH_H = s(22);
    public static final float SWITCH_KNOB = s(18);
    public static final float SWITCH_INSET = s(2);

    // Panel background blur (gated by AtomChatConfig.blurEnabled). The tint sits
    // on top of the blurred snapshot so text stays legible without smothering
    // it — 0x66 read as a bare oil film over the world, 0x99 keeps the panel's
    // blue-grey character while still letting the blur show through.
    public static final float PANEL_BLUR_SIGMA = 30.0F;
    public static final int PANEL_BLUR_TINT = 0xCC16191F;

    private UiTokens() {
    }
}
