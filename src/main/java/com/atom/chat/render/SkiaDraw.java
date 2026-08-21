package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

public final class SkiaDraw {
    private SkiaDraw() {
    }

    public static void drawRoundedRect(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
        try (Paint paint = new Paint().setColor(color).setAntiAlias(true)) {
            canvas.drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        }
    }

    public static void drawRoundedShadow(Canvas canvas, float x, float y, float width, float height, float radius, float blur, int color) {
        try (Paint shadow = new Paint().setColor(color).setAntiAlias(true)
                .setMaskFilter(MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur))) {
            canvas.drawRRect(RRect.makeXYWH(x + blur * 0.5F, y + blur * 0.5F, width, height, radius), shadow);
        }
    }

    public static void drawRoundedImage(Canvas canvas, Image image, float x, float y, float width, float height, float radius) {
        if (image == null) {
            return;
        }
        canvas.save();
        try {
            canvas.clipRRect(RRect.makeXYWH(x, y, width, height, radius), ClipMode.INTERSECT, true);
            Rect src = Rect.makeXYWH(0, 0, image.getWidth(), image.getHeight());
            Rect dst = Rect.makeXYWH(x, y, width, height);
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(image, src, dst, SamplingMode.LINEAR, paint, false);
            }
        } finally {
            canvas.restore();
        }
    }

    public static void clip(Canvas canvas, float x, float y, float width, float height, float radius) {
        canvas.clipRRect(RRect.makeXYWH(x, y, width, height, radius), ClipMode.INTERSECT, true);
    }
}
