package com.atom.chat.page;

import com.atom.chat.font.FontManager;
import com.atom.chat.nav.AppPage;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import net.minecraft.text.Text;

/**
 * Temporary root-page body used while the real profile and settings
 * implementations are still being built. Chat list has its own page.
 */
public final class PlaceholderPage {
    private final AppPage page;

    public PlaceholderPage(AppPage page) {
        this.page = page;
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    public void render(Canvas canvas, UiLayout layout) {
        SkiaDraw.drawRoundedRect(canvas, layout.header.x(), layout.header.y(),
                layout.header.w(), layout.header.h(), UiTokens.HEADER_RADIUS,
                Color.makeARGB(60, 255, 255, 255));
        String title = switch (page) {
            case PROFILE -> tr("atomchat.tab.profile");
            case SETTINGS -> tr("atomchat.tab.settings");
            default -> throw new IllegalArgumentException("PlaceholderPage does not support " + page);
        };
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, title,
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.y() + layout.header.h() / 2.0F,
                Color.makeARGB(255, 255, 255, 255));
        String placeholder = switch (page) {
            case PROFILE -> tr("atomchat.page.profile.placeholder");
            case SETTINGS -> tr("atomchat.page.settings.placeholder");
            default -> throw new IllegalArgumentException("PlaceholderPage does not support " + page);
        };
        Font bodyFont = FontManager.font(UiTokens.FONT_BODY);
        SkiaFontRenderer.drawTextCentered(canvas, bodyFont, placeholder,
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.bottom() + layout.list.h() / 2.0F,
                Color.makeARGB(220, 170, 170, 186));
    }

    public void mouseClicked(float vmx, float vmy) {
        // Placeholder pages have no interactive rows yet.
    }
}
