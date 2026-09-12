package com.atom.chat.ui;

import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;

public class Box extends UiComponent {
    private int color;
    private float radius;
    private boolean shadow;
    private float shadowBlur = 12.0F;

    public Box(int color, float radius) {
        this.color = color;
        this.radius = radius;
    }

    public Box color(int color) {
        this.color = color;
        return this;
    }

    public Box radius(float radius) {
        this.radius = radius;
        return this;
    }

    public Box shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public Box shadowBlur(float shadowBlur) {
        this.shadowBlur = shadowBlur;
        return this;
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible || opacity <= 0) {
            return;
        }
        int argb = (opacity << 24) | (color & 0xFFFFFF);
        if (shadow) {
            SkiaDraw.drawRoundedShadow(canvas, x, y, width, height, radius, shadowBlur, argb);
        } else {
            SkiaDraw.drawRoundedRect(canvas, x, y, width, height, radius, argb);
        }
    }
}
