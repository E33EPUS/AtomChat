package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/**
 * Shared shell icon paths (bottom tabs, header back affordance and the
 * conversation-list globe).
 *
 * <p>All icons use AtomChat's own 20x20 line-icon language, matching the
 * toolbar/menu icons already used in the chat detail page. No generic 24px
 * Lucide curves.</p>
 */
public final class AppIcons {
    private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";
    private static final String ICON_TAB_CHAT_SVG =
            "M4 3 L16 3 L16 13 L10 13 L6 17 L7 13 L4 13 Z"
            + " M7 6 L13 6 M7 9 L11 9";
    private static final String ICON_TAB_PROFILE_SVG =
            "M10 3 a3.5 3.5 0 1 1 0 7 a3.5 3.5 0 1 1 0 -7"
            + " M4 17 C4 13.5 6.5 11.5 10 11.5 C13.5 11.5 16 13.5 16 17";
    private static final String ICON_TAB_SETTINGS_SVG =
            "M10 6.5 a3.5 3.5 0 1 0 0 7 a3.5 3.5 0 1 0 0 -7"
            + " M10 2.5 v2 M10 15.5 v2 M3.5 10 h2 M14.5 10 h2"
            + " M5.3 5.3 l1.4 1.4 M13.3 13.3 l1.4 1.4 M14.7 5.3 l-1.4 1.4 M6.7 13.3 l-1.4 1.4";
    private static final String ICON_GLOBE_SVG =
            "M10 3 a7 7 0 1 0 0 14 a7 7 0 1 0 0 -14"
            + " M3 10 h14"
            + " M10 3 a5.5 5.5 0 0 1 0 14 a5.5 5.5 0 0 1 0 -14";

    public static final Path ICON_BACK_PATH = Path.makeFromSVGString(ICON_BACK_SVG);
    public static final Path ICON_TAB_CHAT_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    public static final Path ICON_TAB_PROFILE_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    public static final Path ICON_TAB_SETTINGS_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    public static final Path ICON_GLOBE_PATH = Path.makeFromSVGString(ICON_GLOBE_SVG);

    private AppIcons() {
    }
}
