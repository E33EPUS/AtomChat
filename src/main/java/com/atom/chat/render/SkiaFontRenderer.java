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

    private SkiaFontRenderer() {
    }

    public static float getStringWidth(Font font, String text) {
        float width = 0.0F;
        for (TextSegment segment : parseColoredText(text)) {
            if (!segment.text.isEmpty()) {
                width += font.measureTextWidth(segment.text);
            }
        }
        return width;
    }

    public static float getHeight(Font font) {
        var metrics = font.getMetrics();
        return (metrics.getDescent() - metrics.getAscent() + metrics.getLeading()) / 2.0F;
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

    public static void drawTextCentered(Canvas canvas, Font font, String text, float centerX, float centerY, int color) {
        try (Paint paint = new Paint().setColor(color)) {
            canvas.drawString(text, centerX - font.measureTextWidth(text) / 2.0F, baselineY(font, centerY), font, paint);
        }
    }

    public static void drawTextRight(Canvas canvas, Font font, String text, float rightX, float centerY, int color) {
        try (Paint paint = new Paint().setColor(color)) {
            canvas.drawString(text, rightX - font.measureTextWidth(text), baselineY(font, centerY), font, paint);
        }
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
                    canvas.drawString(segment.text, drawX, y, font, paint);
                    drawX += font.measureTextWidth(segment.text);
                }
            }
        } finally {
            canvas.restore();
        }
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
            float charWidth = font.measureTextWidth(String.valueOf(c));
            if (currentWidth + charWidth > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0.0F;
                if (Character.isWhitespace(c)) {
                    continue;
                }
            }
            current.append(c);
            currentWidth += charWidth;
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
