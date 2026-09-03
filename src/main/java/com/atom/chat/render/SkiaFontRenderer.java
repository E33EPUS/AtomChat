package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Skia text renderer with Minecraft color-code support (ported from Tuui's FontRenderer).
 */
public final class SkiaFontRenderer {
    private static final int[] COLOR_CODE_RGB = new int[]{
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    /** Per-codepoint+size cache of resolved fallback fonts (emoji / chars missing from the primary). */
    private static final java.util.Map<String, Font> FALLBACK_FONT_CACHE = new java.util.HashMap<>();

    private SkiaFontRenderer() {
    }

    public static float getStringWidth(Font font, String text) {
        float width = 0.0F;
        for (TextSegment segment : parseColoredText(text)) {
            if (!segment.text.isEmpty()) {
                width += measureRuns(font, segment.text);
            }
        }
        return width;
    }

    public static float getHeight(Font font) {
        var metrics = font.getMetrics();
        return metrics.getDescent() - metrics.getAscent() + metrics.getLeading();
    }

    public static float textHeight(Font font) {
        var metrics = font.getMetrics();
        return metrics.getDescent() - metrics.getAscent();
    }

    /**
     * Baseline y so that the text visual box is vertically centered at centerY.
     */
    public static float baselineY(Font font, float centerY) {
        var metrics = font.getMetrics();
        return centerY - (metrics.getAscent() + metrics.getDescent()) / 2.0F;
    }

    /**
     * Baseline so the visual glyph body is centered at centerY. Uses capHeight:
     * CJK fonts carry huge ascender space for diacritics, which pushes the plain
     * metrics-center formula visually up inside the bubble.
     */
    public static float centerBaselineY(Font font, float centerY) {
        var metrics = font.getMetrics();
        float capHeight = metrics.getCapHeight();
        if (capHeight > 0.0F) {
            return centerY + capHeight / 2.0F;
        }
        return baselineY(font, centerY);
    }

    /**
     * Centers text on centerX and vertically on centerY using the cap-height
     * baseline (see centerBaselineY) so it matches every other centered label.
     */
    public static void drawTextCentered(Canvas canvas, Font font, String text, float centerX, float centerY, int color) {
        drawText(canvas, font, text, centerX - getStringWidth(font, text) / 2.0F,
                centerBaselineY(font, centerY), color);
    }

    /**
     * Right-aligns text at rightX, vertically centered on centerY with the same
     * cap-height baseline used by drawTextCentered.
     */
    public static void drawTextRight(Canvas canvas, Font font, String text, float rightX, float centerY, int color) {
        drawText(canvas, font, text, rightX - getStringWidth(font, text),
                centerBaselineY(font, centerY), color);
    }

    public static void drawText(Canvas canvas, Font font, String text, float x, float y, int color) {
        canvas.save();
        try (Paint paint = new Paint().setColor(color)) {
            int currentColor = color;
            float drawX = x;
            for (TextSegment segment : parseColoredText(text)) {
                if (segment.colorCode != null) {
                    currentColor = getColorFromCode(segment.colorCode, currentColor, color);
                }
                if (!segment.text.isEmpty()) {
                    paint.setColor(currentColor);
                    drawRuns(canvas, segment.text, drawX, y, font, paint);
                    drawX += measureRuns(font, segment.text);
                }
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Draws pre-wrapped lines as one block, vertically centered on centerY.
     * Every line keeps Minecraft color-code support.
     */
    public static void drawLines(Canvas canvas, Font font, java.util.List<String> lines, float x, float centerY, float lineHeight, int color) {
        if (lines.isEmpty()) {
            return;
        }
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        canvas.save();
        try (Paint paint = new Paint()) {
            for (int i = 0; i < lines.size(); i++) {
                float baseline = centerBaselineY(font, blockTop + (i + 0.5F) * lineHeight);
                int currentColor = color;
                float drawX = x;
                for (TextSegment segment : parseColoredText(lines.get(i))) {
                    if (segment.colorCode != null) {
                        currentColor = getColorFromCode(segment.colorCode, currentColor, color);
                    }
                    if (!segment.text.isEmpty()) {
                        paint.setColor(currentColor);
                        drawRuns(canvas, segment.text, drawX, baseline, font, paint);
                        drawX += measureRuns(font, segment.text);
                    }
                }
            }
        } finally {
            canvas.restore();
        }
    }

    /** Families probed for glyphs the bundled subset cannot render. */
    private static final String[] FALLBACK_FAMILIES = {
            "Microsoft YaHei", "DengXian", "Segoe UI", "Segoe UI Symbol",
            "MS Gothic", "Yu Gothic UI", "Malgun Gothic", "Leelawadee UI",
            "Cambria", "Calibri", "Arial", "Noto Sans CJK SC"
    };

    /**
     * Glyph-level fallback: codepoints missing from the primary (bundled subset)
     * resolve through the system FontMgr. Emoji-range codepoints first try Segoe
     * UI Emoji, but only when that font actually contains the glyph — many
     * non-emoji symbols share the 0x2600-0x27BF block (e.g. ✧ U+2727) and would
     * otherwise render as tofu even though Segoe UI Symbol has them.
     */
    private static Font fontFor(Font primary, int codepoint) {
        if (primary.getUTF32Glyph(codepoint) != 0) {
            return primary;
        }
        String key = codepoint + "@" + (int) primary.getSize();
        Font cached = FALLBACK_FONT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Font resolved = null;
        try {
            io.github.humbleui.skija.FontMgr mgr = io.github.humbleui.skija.FontMgr.getDefault();
            if (mgr != null) {
                io.github.humbleui.skija.Typeface match = null;
                if (isEmojiCodepoint(codepoint)) {
                    io.github.humbleui.skija.Typeface emoji =
                            mgr.matchFamilyStyle("Segoe UI Emoji", io.github.humbleui.skija.FontStyle.NORMAL);
                    if (emoji != null && emoji.getUTF32Glyph(codepoint) != 0) {
                        match = emoji;
                    }
                }
                if (match == null) {
                    // The bundled font is a GB2312 subset, so kaomoji lean on
                    // exotic ranges (kana, Thai, Hangul, phonetic, symbols). A
                    // narrow list leaves tofu even though Windows has the glyphs:
                    // DengXian/MS Gothic cover kana, Malgun Gothic covers Hangul,
                    // Leelawadee UI covers Thai, Cambria/Calibri cover symbols.
                    match = mgr.matchFamiliesStyleCharacter(
                            FALLBACK_FAMILIES,
                            io.github.humbleui.skija.FontStyle.NORMAL, null, codepoint);
                }
                if (match != null) {
                    resolved = new Font(match, primary.getSize());
                }
            }
        } catch (Throwable t) {
            // No fallback available: primary renders tofu, same as before.
        }
        if (resolved == null) {
            resolved = primary;
        }
        FALLBACK_FONT_CACHE.put(key, resolved);
        return resolved;
    }

    private static boolean isEmojiCodepoint(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF)
                || cp == 0xFE0F || cp == 0x200D || (cp >= 0x2B00 && cp <= 0x2BFF);
    }

    private static void drawRuns(Canvas canvas, String text, float x, float y, Font primary, Paint paint) {
        float drawX = x;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            Font runFont = fontFor(primary, cp);
            int j = i;
            while (j < n) {
                int cp2 = text.codePointAt(j);
                if (fontFor(primary, cp2) != runFont) {
                    break;
                }
                j += Character.charCount(cp2);
            }
            String run = text.substring(i, j);
            canvas.drawString(run, drawX, y, runFont, paint);
            drawX += runFont.measureTextWidth(run);
            i = j;
        }
    }

    private static float measureRuns(Font primary, String text) {
        float width = 0.0F;
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            Font runFont = fontFor(primary, cp);
            int j = i;
            while (j < n) {
                int cp2 = text.codePointAt(j);
                if (fontFor(primary, cp2) != runFont) {
                    break;
                }
                j += Character.charCount(cp2);
            }
            width += runFont.measureTextWidth(text.substring(i, j));
            i = j;
        }
        return width;
    }

    public static List<String> wrap(Font font, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        float currentWidth = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                continue;
            }
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            float charWidth = fontFor(font, cp).measureTextWidth(text.substring(i, i + charCount));
            if (currentWidth + charWidth > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                if (Character.isWhitespace(c)) {
                    i += charCount - 1;
                    continue;
                }
            }
            current.append(text, i, i + charCount);
            currentWidth += charWidth;
            if (charCount > 1) {
                i += charCount - 1;
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int getColorFromCode(String code, int currentColor, int originalColor) {
        if (code == null) {
            return currentColor;
        }
        int alpha = (currentColor >>> 24) & 0xFF;
        if (code.startsWith("#")) {
            return (alpha << 24) | Integer.parseInt(code.substring(1), 16);
        }
        if (code.equals("r")) {
            return originalColor;
        }
        int index = "0123456789abcdef".indexOf(code.charAt(0));
        if (index >= 0 && index < 16) {
            return (alpha << 24) | COLOR_CODE_RGB[index];
        }
        return currentColor;
    }

    private static List<TextSegment> parseColoredText(String text) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String pendingColor = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                if (current.length() > 0) {
                    segments.add(new TextSegment(current.toString(), pendingColor));
                    current.setLength(0);
                }
                i++;
                char code = text.charAt(i);
                if (code == '#' && i + 6 < text.length()) {
                    String hex = text.substring(i + 1, i + 7);
                    if (isHex(hex)) {
                        pendingColor = "#" + hex;
                        i += 6;
                        continue;
                    }
                }
                if ("0123456789abcdefr".indexOf(code) >= 0) {
                    pendingColor = String.valueOf(code);
                } else {
                    current.append('\u00A7').append(code);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            segments.add(new TextSegment(current.toString(), pendingColor));
        }
        return segments;
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (!Character.isDigit(c) && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private record TextSegment(String text, String colorCode) {
    }
}
