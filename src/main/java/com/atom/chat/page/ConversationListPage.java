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

public final class ConversationListPage {
    private static final float ROW_H = UiTokens.s(64);

    private final PageHost host;

    public ConversationListPage(PageHost host) {
        this.host = host;
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    private UiLayout.Rect rowRect(UiLayout layout) {
        float rowX = layout.list.x();
        float rowY = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        return new UiLayout.Rect(rowX, rowY, layout.list.w(), ROW_H);
    }

    public void render(Canvas canvas, UiLayout layout) {
        UiLayout.Rect row = rowRect(layout);
        SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                s(12), Color.makeARGB(60, 255, 255, 255));

        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font subFont = FontManager.font(UiTokens.FONT_QUOTE);
        float textX = row.x() + s(18);
        float nameCenterY = row.y() + row.h() / 2.0F - s(8);
        float subCenterY = row.y() + row.h() / 2.0F + s(12);
        SkiaFontRenderer.drawText(canvas, nameFont, tr("atomchat.conversation.world"), textX,
                SkiaFontRenderer.centerBaselineY(nameFont, nameCenterY),
                Color.makeARGB(255, 255, 255, 255));
        SkiaFontRenderer.drawText(canvas, subFont, tr("atomchat.conversation.world.subtitle"), textX,
                SkiaFontRenderer.centerBaselineY(subFont, subCenterY),
                Color.makeARGB(220, 170, 170, 186));
    }

    public boolean mouseClicked(float vmx, float vmy, UiLayout layout) {
        if (rowRect(layout).contains(vmx, vmy)) {
            host.pushPage(AppPage.WORLD_CHAT);
            return true;
        }
        return false;
    }
}
