package com.atom.chat.avatar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorUtilTest {
    private static final float EPS = 1e-3F;

    @Test
    void hsvToRgbPrimaries() {
        assertEquals(0xFFFF0000, ColorUtil.hsvToRgb(0.0F, 1.0F, 1.0F));
        assertEquals(0xFF00FF00, ColorUtil.hsvToRgb(1.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFF0000FF, ColorUtil.hsvToRgb(2.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFF000000, ColorUtil.hsvToRgb(0.7F, 1.0F, 0.0F));
        assertEquals(0xFFFFFFFF, ColorUtil.hsvToRgb(0.7F, 0.0F, 1.0F));
    }

    @Test
    void rgbToHsvRoundTrips() {
        int[] samples = {
                0xFF1E90FF, 0xFF343A44, 0xFFFFFFFF, 0xFF000000,
                0xFF2ECC71, 0xFFE91E63, 0xFF4A90E2, 0xFFE67E22
        };
        for (int argb : samples) {
            float[] hsv = ColorUtil.rgbToHsv(argb);
            int back = ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            int dr = Math.abs(((back >> 16) & 0xFF) - ((argb >> 16) & 0xFF));
            int dg = Math.abs(((back >> 8) & 0xFF) - ((argb >> 8) & 0xFF));
            int db = Math.abs((back & 0xFF) - (argb & 0xFF));
            assertTrue(dr <= 1 && dg <= 1 && db <= 1,
                    "round trip drift on " + ColorUtil.formatHex(argb));
        }
    }

    @Test
    void hueWraps() {
        int a = ColorUtil.hsvToRgb(0.0F, 1.0F, 1.0F);
        int b = ColorUtil.hsvToRgb(1.0F, 1.0F, 1.0F);
        assertEquals(a, b);
    }

    @Test
    void formatHex() {
        assertEquals("#1E90FF", ColorUtil.formatHex(0xFF1E90FF));
        assertEquals("#343A44", ColorUtil.formatHex(0xFF343A44));
    }
}
