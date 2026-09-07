package com.atom.chat.font;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches Skia fonts.
 *
 * <p>Font pairing: Inter renders Latin/digits/time first; MiSans is the
 * bundled CJK fallback for Chinese text. Each weight keeps its own primary
 * and fallback typeface so {@link SkiaFontRenderer} can resolve glyphs by
 * script instead of letting MiSans' Latin glyphs override Inter.
 */
public final class FontManager {
    private static final String BUNDLED_LATIN = "/assets/atomchat/font/Inter-Regular.ttf";
    private static final String BUNDLED_LATIN_BOLD = "/assets/atomchat/font/Inter-SemiBold.ttf";
    private static final String BUNDLED_CJK = "/assets/atomchat/font/MiSans-Regular.ttf";
    private static final String BUNDLED_CJK_BOLD = "/assets/atomchat/font/MiSans-Semibold.ttf";

    private static Typeface defaultTypeface;
    private static Typeface cjkFallbackTypeface;
    private static Typeface boldTypeface;
    private static Typeface boldCjkFallbackTypeface;

    private static final Map<String, Font> CACHE = new HashMap<>();
    private static final Map<String, Font> BOLD_CACHE = new HashMap<>();
    /** Per cached Font, the bundled typeface to try before system fallbacks. */
    private static final Map<Font, Typeface> FALLBACKS = new HashMap<>();

    private FontManager() {
    }

    /** Primary Latin typeface for normal text. */
    public static Typeface getDefaultTypeface() {
        if (defaultTypeface == null) {
            defaultTypeface = loadTypeface(BUNDLED_LATIN, "Inter", "Segoe UI", "Arial");
        }
        return defaultTypeface;
    }

    /** Bundled MiSans typeface used when Inter lacks a CJK glyph. */
    public static Typeface cjkFallbackTypeface() {
        if (cjkFallbackTypeface == null) {
            cjkFallbackTypeface = loadTypeface(BUNDLED_CJK, "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC");
        }
        return cjkFallbackTypeface;
    }

    /** Primary Latin typeface for bold/heading text. */
    public static Typeface boldTypeface() {
        if (boldTypeface == null) {
            boldTypeface = loadTypeface(BUNDLED_LATIN_BOLD, "Inter", "Segoe UI", "Arial");
        }
        return boldTypeface;
    }

    /** Bundled MiSans typeface for bold CJK fallback. */
    public static Typeface boldCjkFallbackTypeface() {
        if (boldCjkFallbackTypeface == null) {
            boldCjkFallbackTypeface = loadTypeface(BUNDLED_CJK_BOLD, "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC");
        }
        return boldCjkFallbackTypeface;
    }

    /** Bundled fallback for a cached Font, or null when it has none. */
    public static Typeface fallbackTypeface(Font font) {
        return FALLBACKS.get(font);
    }

    public static Font font(float size) {
        String key = size + "px";
        Font cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = new Font(getDefaultTypeface(), size);
        CACHE.put(key, font);
        FALLBACKS.put(font, cjkFallbackTypeface());
        return font;
    }

    /**
     * A separate cached bold face for headings and labels. Uses Inter SemiBold
     * as the Latin primary and MiSans Semibold for CJK, so bold text never
     * depends on faux-emboldening a regular weight.
     */
    public static Font boldFont(float size) {
        String key = size + "px-bold";
        Font cached = BOLD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = new Font(boldTypeface(), size);
        BOLD_CACHE.put(key, font);
        FALLBACKS.put(font, boldCjkFallbackTypeface());
        return font;
    }

    private static Typeface loadTypeface(String resource, String... systemFallbacks) {
        try (InputStream in = FontManager.class.getResourceAsStream(resource)) {
            if (in != null) {
                Typeface typeface = Typeface.makeFromData(Data.makeFromBytes(in.readAllBytes()));
                if (typeface != null) {
                    AtomChat.LOGGER.info("Loaded bundled font {}", resource);
                    return typeface;
                }
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to load bundled font {}, falling back to system font", resource, e);
        }
        for (String name : systemFallbacks) {
            Typeface tf = Typeface.makeFromName(name, FontStyle.NORMAL);
            if (tf != null && !tf.getFamilyName().isEmpty()) {
                AtomChat.LOGGER.info("Using system font {} instead of {}", tf.getFamilyName(), resource);
                return tf;
            }
        }
        return Typeface.makeDefault();
    }
}
