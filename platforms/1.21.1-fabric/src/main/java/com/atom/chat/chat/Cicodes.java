package com.atom.chat.chat;

import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Text;

/**
 * Static helpers around CICode image messages, shared by the screen and the
 * message list view: URL extraction, metadata parsing, on-screen bubble
 * sizing, the image placeholder marker and width-aware truncation.
 */
public final class Cicodes {

    private static final Pattern CICODE = Pattern.compile(
            "\\[\\[CICode,url=([^,\\]]+),name=([^,\\]]*)(?:,w=(\\d+),h=(\\d+))?\\]\\]");

    private Cicodes() {
    }

    /** url / name / intrinsic size carried by a CICode. width and height are 0 in codes written before they existed. */
    public record ImageMeta(String url, String name, int width, int height) {
    }

    public static ImageMeta parseImageMeta(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = CICODE.matcher(text);
        if (!m.find()) {
            return null;
        }
        int w = 0;
        int h = 0;
        if (m.group(3) != null) {
            try {
                w = Integer.parseInt(m.group(3));
                h = Integer.parseInt(m.group(4));
            } catch (NumberFormatException ignored) {
                // Malformed size: fall back to the placeholder box.
            }
        }
        return new ImageMeta(m.group(1), m.group(2), w, h);
    }

    /**
     * On-screen size of an image bubble: the intrinsic size scaled down to fit
     * IMAGE_MAX_W x IMAGE_MAX_H, never upscaled, so a small picture is never
     * blown up into a blur. Messages with no usable size — codes written before
     * w/h existed, or images whose header ImageIO cannot read — fall back to the
     * placeholder box, which is also what is drawn until the image downloads.
     */
    public static float[] imageBubbleSize(ImageMeta meta, float maxWidth) {
        float maxW = Math.min(UiTokens.IMAGE_MAX_W, maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.s(30));
        float maxH = UiTokens.IMAGE_MAX_H;
        if (meta == null || meta.width() <= 0 || meta.height() <= 0) {
            return new float[]{maxW, maxH};
        }
        float scale = Math.min(1.0F, Math.min(maxW / meta.width(), maxH / meta.height()));
        return new float[]{Math.max(1.0F, meta.width() * scale), Math.max(1.0F, meta.height() * scale)};
    }

    public static String extractImageUrl(String text) {
        int start = text.indexOf("[[CICode,url=");
        if (start < 0) {
            start = text.indexOf("[CICode,url=");
        }
        if (start < 0) {
            return null;
        }
        int urlStart = text.indexOf("url=", start) + 4;
        int end = text.indexOf(',', urlStart);
        if (end < 0) {
            end = text.indexOf(']', urlStart);
        }
        if (end < 0 || end <= urlStart) {
            return null;
        }
        return text.substring(urlStart, end);
    }

    public static boolean isImagePlaceholder(String text) {
        return text != null && (text.equals(tr("atomchat.hud.image"))
                || text.equals("[图片]") || text.equalsIgnoreCase("[image]"));
    }

    public static String truncateToWidth(Font font, String text, float maxW) {
        if (SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /** Minecraft language lookup, same rule as the screen's tr(). */
    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }
}
