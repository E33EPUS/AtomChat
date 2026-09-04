package com.atom.chat.ui;

import io.github.humbleui.skija.Path;

/**
 * Shared shell icon paths (bottom tabs, header back affordance and the
 * conversation-list globe).
 *
 * <p>All icons use a hand-written 20x20 line-icon language. The paths are
 * drawn as clean geometric line work with rounded joins/caps at render time:
 * consistent optical weight, no filled silhouettes, no tool exports.</p>
 *
 * <p>The globe deliberately follows the earlier Lucide-style globe that tested
 * well: a circle with a horizontal equator and an elliptical meridian, which
 * reads as a globe at small sizes better than a plain inner circle.</p>
 */
public final class AppIcons {
    private static final String ICON_BACK_SVG = "M4 10 L10 4 M4 10 L10 16 M4 10 L18 10";

    // Rounded chat bubble: softer corners than the old square bubble, with no
    // interior text lines so the glyph stays calm and perfectly centred at
    // larger tab-icon sizes.
    private static final String ICON_TAB_CHAT_SVG =
            "M5 3 h8 a2 2 0 0 1 2 2 v5 a2 2 0 0 1 -2 2 h-4 l-3 3 v-3 h-1 a2 2 0 0 1 -2 -2 v-5 a2 2 0 0 1 2 -2 z";
    // User: a larger head and a softer shoulder curve; no outer circle so it
    // stays in the same open line language as the rest of the shell.
    private static final String ICON_TAB_PROFILE_SVG =
            "M10 3.2 a3.4 3.4 0 1 1 0 6.8 a3.4 3.4 0 1 1 0 -6.8"
            + " M4.5 16.8 C4.5 13.6 6.9 11.8 10 11.8 C13.1 11.8 15.5 13.6 15.5 16.8";
    // Settings: Lucide's proper gear (ISC-licensed, no-copyright line art).
    // It has real gear teeth instead of a sun/ray look.
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
    // The good historical globe: outer circle + equator + elliptical meridian.
    private static final String ICON_GLOBE_SVG =
            "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20"
            + " M2 12h20"
            + " M12 2a15.3 15.3 0 0 1 4 10a15.3 15.3 0 0 1-4 10a15.3 15.3 0 0 1-4-10a15.3 15.3 0 0 1 4-10z";

    public static final Path ICON_BACK_PATH = Path.makeFromSVGString(ICON_BACK_SVG);
    public static final Path ICON_TAB_CHAT_PATH = Path.makeFromSVGString(ICON_TAB_CHAT_SVG);
    public static final Path ICON_TAB_PROFILE_PATH = Path.makeFromSVGString(ICON_TAB_PROFILE_SVG);
    public static final Path ICON_TAB_SETTINGS_PATH = Path.makeFromSVGString(ICON_TAB_SETTINGS_SVG);
    public static final Path ICON_GLOBE_PATH = Path.makeFromSVGString(ICON_GLOBE_SVG);

    private AppIcons() {
    }
}
