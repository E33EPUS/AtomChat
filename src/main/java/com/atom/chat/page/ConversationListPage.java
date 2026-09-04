package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.font.FontManager;
import com.atom.chat.nav.AppPage;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ConversationListPage {
    private static final float ROW_H = UiTokens.s(64);

    private final PageHost host;

    /** Hover fade for the conversation card; shares the UI-wide 90ms language. */
    private float rowHover;
    private long lastFrameMs = System.currentTimeMillis();

    public ConversationListPage(PageHost host) {
        this.host = host;
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    private UiLayout.Rect rowRect(UiLayout layout) {
        float rowX = layout.list.x();
        float rowY = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        return new UiLayout.Rect(rowX, rowY, layout.list.w(), ROW_H);
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        boolean hovered = rowRect(layout).contains(vmx, vmy);
        rowHover = UiMotion.approach(rowHover, hovered ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);

        UiLayout.Rect row = rowRect(layout);
        SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                s(12), Color.makeARGB(60, 255, 255, 255));
        if (rowHover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                    s(12), Color.makeARGB((int) (45.0F * rowHover), 255, 255, 255));
        }

        // Public card icon: container and inner globe are deliberately larger
        // than the old compact tile. The container keeps a uniform s(10) inset
        // from the card edge, matching the vertical breathing room inside the
        // s(64) row height.
        float iconSize = s(44);
        float iconRadius = s(12);
        float iconInset = s(10);
        float iconX = row.x() + iconInset;
        float iconY = row.y() + (row.h() - iconSize) / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, iconX, iconY, iconSize, iconSize, iconRadius,
                Color.makeARGB(60, 255, 255, 255));
        drawIconCentered(canvas, AppIcons.ICON_GLOBE_PATH,
                iconX + iconSize / 2.0F, iconY + iconSize / 2.0F, s(26),
                Color.makeARGB(255, 255, 255, 255));

        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font subFont = FontManager.font(UiTokens.FONT_QUOTE);
        Font timeFont = FontManager.font(UiTokens.FONT_QUOTE);
        float textX = iconX + iconSize + s(12);
        float nameCenterY = row.y() + row.h() / 2.0F - s(9);
        float previewCenterY = row.y() + row.h() / 2.0F + s(12);

        // Time sits at the top right, aligned with the title row.
        String time = latestMessageTime();
        float timeW = time.isEmpty() ? 0.0F : SkiaFontRenderer.getStringWidth(timeFont, time);
        float timeX = row.right() - s(10) - timeW;
        float nameMaxW = Math.max(0.0F, timeX - textX - s(8));
        String name = truncateToWidth(nameFont, tr("atomchat.conversation.world"), nameMaxW);
        SkiaFontRenderer.drawText(canvas, nameFont, name, textX,
                SkiaFontRenderer.centerBaselineY(nameFont, nameCenterY),
                Color.makeARGB(255, 255, 255, 255));
        if (!time.isEmpty()) {
            SkiaFontRenderer.drawText(canvas, timeFont, time, timeX,
                    SkiaFontRenderer.centerBaselineY(timeFont, nameCenterY),
                    Color.makeARGB(190, 170, 170, 186));
        }

        float maxPreviewW = Math.max(0.0F, row.right() - textX - s(8));
        String preview = truncateToWidth(subFont, latestPreview(), maxPreviewW);
        SkiaFontRenderer.drawText(canvas, subFont, preview, textX,
                SkiaFontRenderer.centerBaselineY(subFont, previewCenterY),
                Color.makeARGB(220, 170, 170, 186));
    }

    public boolean mouseClicked(float vmx, float vmy, UiLayout layout) {
        if (rowRect(layout).contains(vmx, vmy)) {
            host.pushPage(AppPage.WORLD_CHAT);
            return true;
        }
        return false;
    }

    private static String latestPreview() {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        if (messages.isEmpty()) {
            return tr("atomchat.conversation.empty");
        }
        return previewFor(messages.get(messages.size() - 1));
    }

    private static String latestMessageTime() {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        if (messages.isEmpty()) {
            return "";
        }
        return formatMessageTime(messages.get(messages.size() - 1));
    }

    private static String formatMessageTime(ChatMessage msg) {
        ZonedDateTime dt = Instant.ofEpochMilli(msg.getTimestamp()).atZone(ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (date.equals(today.minusDays(1))) {
            return tr("atomchat.time.yesterday");
        }
        if (date.equals(today.minusDays(2))) {
            return tr("atomchat.time.beforeYesterday");
        }
        return tr("atomchat.time.date", dt.getMonthValue(), dt.getDayOfMonth());
    }

    private static String previewFor(ChatMessage msg) {
        if (msg.isSystem()) {
            String content = friendlyContent(msg);
            return content.isEmpty() ? tr("atomchat.conversation.empty") : content;
        }
        String name = msg.getSenderName();
        if (name == null || name.isBlank()) {
            name = tr("atomchat.sender.player");
        }
        String content = friendlyContent(msg);
        return name + ": " + (content.isEmpty() ? tr("atomchat.conversation.empty") : content);
    }

    /** Friendly single-line body: image codes collapse to [Image], quotes are stripped. */
    private static String friendlyContent(ChatMessage msg) {
        String raw = msg.getRawText();
        if (hasImageCode(raw)) {
            return tr("atomchat.hud.image");
        }
        String text = msg.getContentText();
        return text == null ? "" : text.trim();
    }

    private static boolean hasImageCode(String text) {
        return text != null
                && (text.contains("[[CICode,url=") || text.contains("[CICode,url="));
    }

    private static String truncateToWidth(Font font, String text, float maxW) {
        if (text.isEmpty() || maxW <= 0.0F || SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
                                         float size, int color) {
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
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
