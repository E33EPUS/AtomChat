package com.atom.chat.avatar;

/**
 * Pure math for the image cropper: a fixed frame in the middle (circle for the
 * avatar, rounded rectangle for the wallpaper), the image pans underneath it
 * (drag) and zooms around the frame centre (wheel). The one hard rule is that
 * the image must always fully cover the frame — no transparent edges may peek
 * inside.
 *
 * <p>The frame is described by half-extents {@code rx}/{@code ry}; a circle is
 * the special case {@code rx == ry}.
 */
public final class CropMath {
    private CropMath() {
    }

    /** Smallest allowed scale: the image exactly covers the frame. */
    public static float baseScale(float imgW, float imgH, float frameW, float frameH) {
        return Math.max(frameW / Math.max(1.0F, imgW), frameH / Math.max(1.0F, imgH));
    }

    /** Zoom is clamped to [base, base * 4]. */
    public static float clampScale(float scale, float base) {
        return Math.max(base, Math.min(base * 4.0F, scale));
    }

    /**
     * Clamps the image-centre offset so the scaled image keeps covering the
     * frame at ({@code cx},{@code cy}) with half-extents {@code rx}/{@code ry}.
     *
     * @return {@code {ox, oy}}
     */
    public static float[] clampCover(float imgW, float imgH, float scale,
                                     float cx, float cy, float rx, float ry,
                                     float ox, float oy) {
        float hw = imgW * scale / 2.0F;
        float hh = imgH * scale / 2.0F;
        return new float[]{clampAxis(ox, cx, rx, hw), clampAxis(oy, cy, ry, hh)};
    }

    private static float clampAxis(float o, float c, float r, float half) {
        // Cover constraints: o - half <= c - r  and  o + half >= c + r.
        float min = c + r - half;
        float max = c - r + half;
        if (min >= max) {
            // Axis exactly at the base scale: the image half equals the frame
            // half, so the centre is pinned.
            return c;
        }
        return Math.max(min, Math.min(max, o));
    }

    /**
     * Zoom keeping the image point currently under the frame centre pinned
     * there, then re-clamps the offset. Returns {@code {scale, ox, oy}}.
     */
    public static float[] zoomAtCentre(float imgW, float imgH, float scale,
                                       float cx, float cy, float rx, float ry,
                                       float ox, float oy, float factor, float base) {
        float newScale = clampScale(scale * factor, base);
        if (newScale == scale) {
            return new float[]{scale, ox, oy};
        }
        // The image point under the centre stays put across the zoom.
        float ux = (cx - ox) / scale;
        float uy = (cy - oy) / scale;
        float nx = cx - ux * newScale;
        float ny = cy - uy * newScale;
        float[] clamped = clampCover(imgW, imgH, newScale, cx, cy, rx, ry, nx, ny);
        return new float[]{newScale, clamped[0], clamped[1]};
    }

    /**
     * Source-pixel rectangle that the frame sees. The image's left edge sits at
     * {@code ox - imgW*scale/2}, so the frame's left edge maps to
     * {@code (cx - rx - ox)/scale + imgW/2} in image pixels. Clamped to the
     * image bounds.
     *
     * @return {@code {sx, sy, sw, sh}}
     */
    public static float[] visibleRect(float imgW, float imgH, float scale,
                                      float cx, float cy, float rx, float ry,
                                      float ox, float oy) {
        float sw = 2.0F * rx / scale;
        float sh = 2.0F * ry / scale;
        float sx = (cx - rx - ox) / scale + imgW / 2.0F;
        float sy = (cy - ry - oy) / scale + imgH / 2.0F;
        sx = Math.max(0.0F, Math.min(imgW - sw, sx));
        sy = Math.max(0.0F, Math.min(imgH - sh, sy));
        return new float[]{sx, sy, sw, sh};
    }
}
