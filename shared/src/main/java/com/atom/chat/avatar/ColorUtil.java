package com.atom.chat.avatar;

/**
 * RGB ↔ HSV conversions and hex formatting for the colour picker. Pure math.
 *
 * <p>H, S, V are all 0..1. H wraps (0 and 1 are both red); S and V clamp.
 */
public final class ColorUtil {
    private ColorUtil() {
    }

    /** ARGB (opaque) → {@code {h, s, v}}. */
    public static float[] rgbToHsv(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d <= 0.0F) {
            h = 0.0F;
        } else if (max == r) {
            h = ((g - b) / d) % 6.0F;
        } else if (max == g) {
            h = (b - r) / d + 2.0F;
        } else {
            h = (r - g) / d + 4.0F;
        }
        h /= 6.0F;
        if (h < 0.0F) {
            h += 1.0F;
        }
        float s = max <= 0.0F ? 0.0F : d / max;
        return new float[]{h, s, max};
    }

    /** {@code h, s, v} (0..1) → opaque ARGB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp01(s);
        v = clamp01(v);
        float r;
        float g;
        float b;
        float i = h * 6.0F;
        int sector = (int) Math.floor(i);
        float f = i - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        int ir = Math.round(clamp01(r) * 255.0F);
        int ig = Math.round(clamp01(g) * 255.0F);
        int ib = Math.round(clamp01(b) * 255.0F);
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    /** {@code #RRGGBB} of the RGB part (alpha ignored). */
    public static String formatHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
