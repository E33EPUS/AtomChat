package com.atom.chat.notification;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Rect;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skia notification banner stack, drawn through {@code SkiaGraphics.INSTANCE}
 * when no screen is open (or from the AtomChat screen canvas when desired).
 *
 * <p>The visual language matches the in-panel popups: an opaque dark surface
 * slightly brighter than the panel, rounded corners and a soft shadow. No
 * vanilla HUD text is used.
 */
public final class NotificationBanner {
    public static final NotificationBanner INSTANCE = new NotificationBanner();

    public enum Type { MENTION, QUOTE, WHISPER }

    private static final long VISIBLE_MS = 4000L;
    private static final int MAX_STACK = 3;
    private static final int SURFACE = Color.makeARGB(245, 35, 39, 47);
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xDCAAAABA;
    private static final int ACCENT = 0xFF60A5FA;

    private final List<Active> banners = new ArrayList<>();

    private record Active(Type type, String sender, String content, long born) {
    }

    private NotificationBanner() {
    }

    public void enqueue(Type type, String sender, String content) {
        long now = System.currentTimeMillis();
        banners.add(0, new Active(type, sender, content, now));
        while (banners.size() > MAX_STACK) {
            banners.remove(banners.size() - 1);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        banners.removeIf(b -> now - b.born() >= VISIBLE_MS);
    }

    public boolean hasActive() {
        return !banners.isEmpty();
    }

    public void render(Canvas canvas, float screenW, float screenH) {
        if (banners.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        float bannerW = Math.min(UiTokens.s(340), Math.max(UiTokens.s(160), screenW - UiTokens.s(48)));
        float bannerH = UiTokens.s(58);
        float gap = UiTokens.s(6);
        float x = (screenW - bannerW) / 2.0F;
        float topY = UiTokens.s(12);

        // Oldest first so the newest banner paints on top.
        for (int i = banners.size() - 1; i >= 0; i--) {
            Active b = banners.get(i);
            float alpha = alphaFor(b, now);
            if (alpha <= 0.01F) {
                continue;
            }
            float y = topY + i * (bannerH + gap);
            drawBanner(canvas, b, x, y, bannerW, bannerH, alpha);
        }
    }

    /** Renders the notification stack inside the AtomChat panel (below its header). */
    public void renderInPanel(Canvas canvas, float panelX, float panelY, float panelW, float panelH) {
        if (banners.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        float bannerW = Math.min(UiTokens.s(320), Math.max(UiTokens.s(180), panelW - UiTokens.s(24)));
        float bannerH = UiTokens.s(58);
        float gap = UiTokens.s(6);
        float x = panelX + (panelW - bannerW) / 2.0F;
        float y = panelY + UiTokens.HEADER_HEIGHT + UiTokens.s(6);

        for (int i = banners.size() - 1; i >= 0; i--) {
            Active b = banners.get(i);
            float alpha = alphaFor(b, now);
            if (alpha <= 0.01F || y + bannerH > panelY + panelH - UiTokens.s(4)) {
                continue;
            }
            drawBanner(canvas, b, x, y, bannerW, bannerH, alpha);
            y += bannerH + gap;
        }
    }

    private float alphaFor(Active b, long now) {
        float age = now - b.born();
        float appear = Math.min(1.0F, age / 200.0F);
        float disappear = Math.max(0.0F, Math.min(1.0F, (VISIBLE_MS - age) / 150.0F));
        return Math.min(appear, disappear);
    }

    private void drawBanner(Canvas canvas, Active b, float x, float y, float w, float h, float alpha) {
        float radius = UiTokens.s(12);
        // Fade whole banner by drawing the surface with multiplied alpha; text
        // uses fixed colors and a parallel fade by clipping? Simpler: draw a
        // layer whose alpha fades the whole group.
        canvas.save();
        try (io.github.humbleui.skija.Paint layer = new io.github.humbleui.skija.Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - UiTokens.s(8), y - UiTokens.s(8), w + UiTokens.s(16), h + UiTokens.s(16)), layer);
        }

        SkiaDraw.drawRoundedShadow(canvas, x, y, w, h, radius, UiTokens.s(8), Color.makeARGB(100, 0, 0, 0));
        SkiaDraw.drawRoundedRect(canvas, x, y, w, h, radius, SURFACE);

        float padX = UiTokens.s(14);
        float line1Y = y + UiTokens.s(16);
        float line2Y = y + UiTokens.s(37);
        Font titleFont = FontManager.boldFont(UiTokens.FONT_NAME);
        Font bodyFont = FontManager.font(UiTokens.FONT_QUOTE);
        String typeLabel = tr(typeKey(b.type()));
        String title = typeLabel + (b.sender() != null && !b.sender().isBlank() ? "  " + b.sender() : "");
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, title, w - padX * 2.0F),
                x + padX, SkiaFontRenderer.centerBaselineY(titleFont, line1Y), ACCENT);

        String preview = b.content() == null ? "" : b.content().replace('\n', ' ');
        SkiaFontRenderer.drawText(canvas, bodyFont,
                SkiaFontRenderer.truncate(bodyFont, preview, w - padX * 2.0F),
                x + padX, SkiaFontRenderer.centerBaselineY(bodyFont, line2Y), TEXT);

        canvas.restore();
    }

    private static String typeKey(Type type) {
        return switch (type) {
            case MENTION -> "atomchat.notify.mention";
            case QUOTE -> "atomchat.notify.quote";
            case WHISPER -> "atomchat.notify.whisper";
        };
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** Whether notifications are enabled for this type at all. */
    public static boolean enabled(Type type) {
        AtomChatConfig config = AtomChatConfig.get();
        return switch (type) {
            case MENTION, QUOTE -> config.mentionBannerEnabled;
            case WHISPER -> config.whisperBannerEnabled;
        };
    }

    /** Whether the sound for this type is enabled. */
    public static boolean soundEnabled(Type type) {
        AtomChatConfig config = AtomChatConfig.get();
        return switch (type) {
            case MENTION, QUOTE -> config.mentionSoundEnabled;
            case WHISPER -> config.whisperSoundEnabled;
        };
    }
}
