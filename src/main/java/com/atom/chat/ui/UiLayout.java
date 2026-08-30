package com.atom.chat.ui;

/**
 * Pure layout math: given the panel rect, derives every chrome rect. Rendering
 * (AtomChatScreen.drawPanel) and input hit-testing (mouseClicked/mouseScrolled)
 * must both read from here so the two can never drift, and tests can verify
 * geometry without launching the game.
 */
public final class UiLayout {
    public final float panelX;
    public final float panelY;
    public final float panelW;
    public final float panelH;

    public final Rect header;
    public final Rect list;
    public final Rect inputBar;
    public final Rect imageBtn;
    public final Rect emojiBtn;
    public final Rect sendBtn;
    /** Vertical center of the input text row inside the input bar. */
    public final float inputTextCenterY;

    private UiLayout(float panelX, float panelY, float panelW, float panelH) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;

        // Header is an inset card (same style as the input bar); its edge gap
        // mirrors PANEL_BOTTOM_PAD so top and bottom breathing space match.
        this.header = new Rect(panelX + UiTokens.LIST_PAD_X, panelY + UiTokens.PANEL_BOTTOM_PAD,
                panelW - UiTokens.LIST_PAD_X * 2.0F, UiTokens.HEADER_HEIGHT);
        float listTop = this.header.bottom() + UiTokens.PANEL_TOP_GAP;
        this.list = new Rect(panelX + UiTokens.LIST_PAD_X, listTop,
                panelW - UiTokens.LIST_PAD_X * 2.0F,
                panelH - (listTop - panelY) - UiTokens.INPUT_HEIGHT - UiTokens.PANEL_BOTTOM_PAD);
        this.inputBar = new Rect(panelX + UiTokens.LIST_PAD_X, panelY + panelH - UiTokens.INPUT_HEIGHT - UiTokens.PANEL_BOTTOM_PAD,
                panelW - UiTokens.LIST_PAD_X * 2.0F, UiTokens.INPUT_HEIGHT);

        float rowLeft = inputBar.x + UiTokens.INPUT_ROW_PAD;
        float rowRight = inputBar.x + inputBar.w - UiTokens.INPUT_ROW_PAD;
        float rowY = inputBar.y + UiTokens.INPUT_ROW_PAD;
        this.imageBtn = new Rect(rowLeft, rowY, UiTokens.BUTTON_W, UiTokens.BUTTON_H);
        this.emojiBtn = new Rect(rowLeft + UiTokens.BUTTON_W + UiTokens.BUTTON_GAP, rowY, UiTokens.BUTTON_W, UiTokens.BUTTON_H);
        this.sendBtn = new Rect(rowRight - UiTokens.BUTTON_W, rowY, UiTokens.BUTTON_W, UiTokens.BUTTON_H);

        float rowBottom = rowY + UiTokens.BUTTON_H;
        this.inputTextCenterY = rowBottom + UiTokens.s(20);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH) {
        return new UiLayout(panelX, panelY, panelW, panelH);
    }

    public Rect rect() {
        return new Rect(panelX, panelY, panelW, panelH);
    }

    public record Rect(float x, float y, float w, float h) {
        public float right() {
            return x + w;
        }

        public float bottom() {
            return y + h;
        }

        public boolean contains(float px, float py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }

        public boolean contains(Rect other) {
            return other.x >= x && other.right() <= right() && other.y >= y && other.bottom() <= bottom();
        }
    }
}
