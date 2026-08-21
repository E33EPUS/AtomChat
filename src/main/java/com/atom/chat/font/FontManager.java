package com.atom.chat.font;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.io.InputStream;
import java.util.Locale;

/**
 * Loads and caches Skia fonts. Prefers a bundled open-source CJK font when present,
 * otherwise falls back to a system font so development can proceed without assets.
 */
public final class FontManager {
    private static final String BUNDLED_FONT = "/assets/atomchat/font/NotoSansSC-Regular.otf";
    private static Typeface defaultTypeface;
    private static final java.util.Map<String, Font> CACHE = new java.util.HashMap<>();

    private FontManager() {
    }

    public static Typeface getDefaultTypeface() {
        if (defaultTypeface != null) {
            return defaultTypeface;
        }

        // Bundled font first.
        try (InputStream in = FontManager.class.getResourceAsStream(BUNDLED_FONT)) {
            if (in != null) {
                defaultTypeface = Typeface.makeFromData(Data.makeFromBytes(in.readAllBytes()));
                AtomChat.LOGGER.info("Loaded bundled font {}", BUNDLED_FONT);
                return defaultTypeface;
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to load bundled font, falling back to system font", e);
        }

        // System fallbacks (Windows/other).
        String[] candidates = {"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Arial"};
        for (String name : candidates) {
            Typeface tf = Typeface.makeFromName(name, FontStyle.NORMAL);
            if (tf != null && !tf.getFamilyName().toLowerCase(Locale.ROOT).contains("arial") || tf != null && tf.getFamilyName().toLowerCase(Locale.ROOT).contains("yahei")) {
                defaultTypeface = tf;
                AtomChat.LOGGER.info("Using system font {}", tf.getFamilyName());
                return defaultTypeface;
            }
            if (tf != null) {
                defaultTypeface = tf;
                AtomChat.LOGGER.info("Using system font {}", tf.getFamilyName());
                return defaultTypeface;
            }
        }
        defaultTypeface = Typeface.makeDefault();
        return defaultTypeface;
    }

    public static Font font(float size) {
        String key = size + "px";
        Font cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = new Font(getDefaultTypeface(), size);
        CACHE.put(key, font);
        return font;
    }
}
