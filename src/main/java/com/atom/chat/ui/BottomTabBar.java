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
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Rect;
import net.minecraft.text.Text;

/**
 * Shared bottom tab bar for AtomChat root pages. Rendering and hit-testing are
 * both driven by {@link UiLayout.Rect} plus {@link UiTokens} geometry so the
 * shell never hardcodes a cell position.
 */
public final class BottomTabBar {
    private static final String[] LABELS = {
            "atomchat.tab.chat",
            "atomchat.tab.profile",
            "atomchat.tab.settings"
    };

    private static final Path[] ICONS = {
            AppIcons.ICON_TAB_CHAT_PATH,
            AppIcons.ICON_TAB_PROFILE_PATH,
            AppIcons.ICON_TAB_SETTINGS_PATH
    };

    private static final Path[] FILLED_ICONS = {
            AppIcons.ICON_TAB_CHAT_FILLED_PATH,
            AppIcons.ICON_TAB_PROFILE_FILLED_PATH,
            AppIcons.ICON_TAB_SETTINGS_FILLED_PATH
    };

    private BottomTabBar() {
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    public static void render(Canvas canvas, UiLayout.Rect bar, int selectedIndex,
                              int textPrimary, int accent) {
        if (bar == null || bar.w() <= 0.0F || bar.h() <= 0.0F) {
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(),
                UiTokens.s(18), Color.makeARGB(60, 255, 255, 255));

        float cellWidth = bar.w() / 3.0F;
        Font labelFont = FontManager.font(UiTokens.TAB_LABEL_FONT);
        for (int i = 0; i < 3; i++) {
            boolean selected = i == selectedIndex;
            float cellX = bar.x() + cellWidth * i;
            float cellCenterX = bar.x() + cellWidth * (i + 0.5F);
            float iconCenterY = bar.y() + UiTokens.TAB_ICON_TOP + UiTokens.TAB_ICON_SIZE / 2.0F;
            float labelCenterY = bar.y() + UiTokens.TAB_ICON_TOP + UiTokens.TAB_ICON_SIZE
                    + UiTokens.TAB_LABEL_GAP + UiTokens.s(13) / 2.0F;

            if (selected) {
                // Light capsule behind the icon + label content block.
                float capsuleY = bar.y() + UiTokens.TAB_ICON_TOP - UiTokens.s(2);
                float capsuleBottom = bar.y() + bar.h() - UiTokens.TAB_BOTTOM_PAD + UiTokens.s(2);
                float capsuleH = capsuleBottom - capsuleY;
                float capsuleX = cellX + UiTokens.s(2);
                float capsuleW = cellWidth - UiTokens.s(2) * 2.0F;
                SkiaDraw.drawRoundedRect(canvas, capsuleX, capsuleY, capsuleW, capsuleH,
                        capsuleH / 2.0F, Color.makeARGB(45, 255, 255, 255));
            }

            int itemColor = selected ? accent : textPrimary;
            drawIcon(canvas, selected ? FILLED_ICONS[i] : ICONS[i],
                    cellCenterX, iconCenterY, UiTokens.TAB_ICON_SIZE, itemColor, selected);
            SkiaFontRenderer.drawTextCentered(canvas, labelFont, tr(LABELS[i]),
                    cellCenterX, labelCenterY, itemColor);
        }
    }

    /** Returns 0..2 for the three equal cells, or -1 when outside the bar. */
    public static int hitTest(float x, float y, UiLayout.Rect bar) {
        if (bar == null || bar.w() <= 0.0F || bar.h() <= 0.0F
                || x < bar.x() || x > bar.right() || y < bar.y() || y > bar.bottom()) {
            return -1;
        }
        int index = (int) ((x - bar.x()) / (bar.w() / 3.0F));
        return Math.max(0, Math.min(2, index));
    }

    private static void drawIcon(Canvas canvas, Path icon, float cx, float cy,
                                 float size, int color, boolean filled) {
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
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)) {
                if (filled) {
                    canvas.drawPath(icon, paint);
                } else {
                    paint.setMode(PaintMode.STROKE)
                            .setStrokeWidth(UiTokens.s(1.5F) / scale)
                            .setStrokeCap(PaintStrokeCap.ROUND)
                            .setStrokeJoin(PaintStrokeJoin.ROUND);
                    canvas.drawPath(icon, paint);
                }
            }
        } finally {
            canvas.restore();
        }
    }
}
