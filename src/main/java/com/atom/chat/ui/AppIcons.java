package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/**
 * Shared shell icon paths (bottom tabs, header back affordance and the
 * conversation-list globe).
 *
 * <p>All icons use a 24x24 Lucide/Feather-style logical space and a single
 * line-icon language. {@code drawIconCentered} scales paths by bounds, so the
 * 24x24 space is safe to mix with any existing 20x20 toolbar icons.</p>
 */
public final class AppIcons {
    private static final String ICON_BACK_SVG = "M19 12H5 M12 19l-7-7 7-7";
    private static final String ICON_TAB_CHAT_SVG =
            "M21 11.5a8.5 8.5 0 0 1-8.5 8.5 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7"
            + "a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 8.5-8.5h.5a8.48 8.48 0 0 1 8 8v.5z";
    private static final String ICON_TAB_PROFILE_SVG =
            "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"
            + " M12 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8";
    private static final String ICON_TAB_SETTINGS_SVG =
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6"
            + " M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0"
            + "l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2"
            + " 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06"
            + "a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82"
            + " 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0"
            + " 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0"
            + "l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2"
            + " 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06"
            + "a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9"
            + "a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z";
    private static final String ICON_GLOBE_SVG =
            "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20"
            + " M2 12h20"
            + " M12 2a15.3 15.3 0 0 1 4 10a15.3 15.3 0 0 1-4 10a15.3 15.3 0 0 1-4-10"
            + "a15.3 15.3 0 0 1 4-10z";

    public static final Path ICON_BACK_PATH = Path.makeFromSVGString(ICON_BACK_SVG);
    public static final Path ICON_TAB_CHAT_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    public static final Path ICON_TAB_PROFILE_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    public static final Path ICON_TAB_SETTINGS_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    public static final Path ICON_GLOBE_PATH = Path.makeFromSVGString(ICON_GLOBE_SVG);

    private AppIcons() {
    }
}
