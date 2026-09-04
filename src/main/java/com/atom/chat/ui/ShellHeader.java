package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.types.Rect;

import java.time.LocalTime;

/**
 * Pure Skia shell chrome for the unified AtomChat header. It owns the
 * translucent card, the optional back affordance, the centered page title and
 * the right-aligned clock; page classes never draw these themselves.
 */
public final class ShellHeader {
    private ShellHeader() {
    }

    public static void render(Canvas canvas, UiLayout.Rect header, String title, boolean showBack,
                              UiLayout.Rect backButton, float backHover, int textPrimary) {
        if (header == null || header.w() <= 0.0F || header.h() <= 0.0F) {
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, header.x(), header.y(), header.w(), header.h(),
                UiTokens.HEADER_RADIUS, Color.makeARGB(60, 255, 255, 255));

        if (showBack && backButton != null) {
            if (backHover > 0.01F) {
                float inset = UiTokens.s(4);
                float x = backButton.x() + inset;
                float y = backButton.y() + inset;
                float w = backButton.w() - inset * 2.0F;
                float h = backButton.h() - inset * 2.0F;
                SkiaDraw.drawVerticalGradient(canvas, x, y, w, h, UiTokens.s(8),
                        Color.makeARGB((int) (45.0F * backHover), 255, 255, 255),
                        Color.makeARGB(0, 255, 255, 255));
            }
            drawIconCentered(canvas, AppIcons.ICON_BACK_PATH,
                    backButton.x() + backButton.w() / 2.0F,
                    backButton.y() + backButton.h() / 2.0F,
                    UiTokens.s(18), textPrimary);
        }

        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, title,
                header.x() + header.w() / 2.0F,
                header.y() + header.h() / 2.0F, textPrimary);

        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time,
                header.right() - UiTokens.HEADER_PAD_X,
                header.y() + header.h() / 2.0F, textPrimary);
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(UiTokens.s(1.5F) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
