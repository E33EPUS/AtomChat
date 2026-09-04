package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/**
 * Shared shell icon paths (bottom tabs and header back affordance).
 *
 * <p>All icons use a 20x20 logical space and the same Feather-like line-icon
 * language as the toolbar/menu icons in AtomChatScreen. Filled variants are
 * closed silhouette paths used for the selected tab state.</p>
 */
public final class AppIcons {
    private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";
    private static final String ICON_TAB_CHAT_SVG = "M4 3 L16 3 L16 13 L10 13 L6 17 L7 13 L4 13 Z";
    private static final String ICON_TAB_PROFILE_SVG = "M10 3 a3.5 3.5 0 1 1 0 7 a3.5 3.5 0 1 1 0 -7"
            + " M4 17 C4 13.5 6.5 11.5 10 11.5 C13.5 11.5 16 13.5 16 17";
    private static final String ICON_TAB_SETTINGS_SVG = "M10 6.5 a3.5 3.5 0 1 0 0 7 a3.5 3.5 0 1 0 0 -7"
            + " M10 2.5 v2 M10 15.5 v2 M3.5 10 h2 M14.5 10 h2"
            + " M5.3 5.3 l1.4 1.4 M13.3 13.3 l1.4 1.4 M14.7 5.3 l-1.4 1.4 M6.7 13.3 l-1.4 1.4";

    private static final String ICON_TAB_PROFILE_FILLED_SVG = "M10 3 a3.5 3.5 0 1 1 0 7 a3.5 3.5 0 1 1 0 -7 Z"
            + " M4 17 C4 13.5 6.5 11.5 10 11.5 C13.5 11.5 16 13.5 16 17 Z";
    private static final String ICON_TAB_SETTINGS_FILLED_SVG =
            "M10 2 L12.2 4.6 L15.7 4.3 L15.4 7.8 L18 10 L15.4 12.2 L15.7 15.7 L12.2 15.4"
            + " L10 18 L7.8 15.4 L4.3 15.7 L4.6 12.2 L2 10 L4.6 7.8 L4.3 4.3 L7.8 4.6 Z";

    public static final Path ICON_BACK_PATH = Path.makeFromSVGString(ICON_BACK_SVG);
    public static final Path ICON_TAB_CHAT_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    /** Filled chat is the same closed bubble outline, rendered as a silhouette. */
    public static final Path ICON_TAB_CHAT_FILLED_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    public static final Path ICON_TAB_PROFILE_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    public static final Path ICON_TAB_PROFILE_FILLED_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_FILLED_SVG);
    public static final Path ICON_TAB_SETTINGS_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    public static final Path ICON_TAB_SETTINGS_FILLED_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_FILLED_SVG);

    private AppIcons() {
    }
}
