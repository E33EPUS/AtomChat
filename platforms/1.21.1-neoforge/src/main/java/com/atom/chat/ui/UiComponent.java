package com.atom.chat.ui;

import io.github.humbleui.skija.Canvas;

public abstract class UiComponent {
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected int opacity = 255;
    protected boolean visible = true;

    public abstract void render(Canvas canvas);

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public void tick() {
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public UiComponent pos(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public UiComponent size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public UiComponent opacity(int opacity) {
        this.opacity = opacity;
        return this;
    }

    public UiComponent visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public int getOpacity() {
        return opacity;
    }

    public boolean isVisible() {
        return visible;
    }
}
