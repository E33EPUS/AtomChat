package com.atom.chat.ui;

import com.atom.chat.font.FontManager;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;

import java.util.List;

public class Text extends UiComponent {
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private String text;
    private Font font;
    private int color;
    private Align align = Align.LEFT;
    private boolean shadow;

    public Text(String text, int color) {
        this.text = text;
        this.color = color;
        this.font = FontManager.font(16.0F);
    }

    public Text text(String text) {
        this.text = text;
        return this;
    }

    public Text font(Font font) {
        this.font = font;
        return this;
    }

    public Text color(int color) {
        this.color = color;
        return this;
    }

    public Text align(Align align) {
        this.align = align;
        return this;
    }

    public Text shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public String getText() {
        return text;
    }

    public float getTextWidth() {
        return SkiaFontRenderer.getStringWidth(font, text);
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible || opacity <= 0 || text == null || text.isEmpty()) {
            return;
        }
        int argb = (opacity << 24) | (color & 0xFFFFFF);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        List<String> lines = SkiaFontRenderer.wrap(font, text, width <= 0 ? Float.MAX_VALUE : width);
        float yOffset = y + font.getSize();
        for (String line : lines) {
            float lineWidth = SkiaFontRenderer.getStringWidth(font, line);
            float drawX = switch (align) {
                case LEFT -> x;
                case CENTER -> x + (width - lineWidth) / 2.0F;
                case RIGHT -> x + width - lineWidth;
            };
            if (shadow) {
                SkiaFontRenderer.drawText(canvas, font, line, drawX + 1.0F, yOffset + 1.0F, 0x64000000);
            }
            SkiaFontRenderer.drawText(canvas, font, line, drawX, yOffset, argb);
            yOffset += lineHeight;
        }
    }
}
