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
    /** Extra height currently added to the input bar while the text wraps. */
    public final float inputExtraH;
    /** Reserved height for the reply banner (0 when no quote is pending). */
    public final float replyH;

    public final Rect header;
    public final Rect list;
    public final Rect replyBar;
    public final Rect inputBar;
    public final Rect imageBtn;
    public final Rect emojiBtn;
    public final Rect sendBtn;
    /** Vertical center of the input text's FIRST visible line. */
    public final float inputTextCenterY;

    private UiLayout(float panelX, float panelY, float panelW, float panelH, float inputExtraH, float replyH) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.inputExtraH = inputExtraH;
        this.replyH = Math.max(0.0F, replyH);

        // Header is an inset card (same style as the input bar); its edge gap
        // mirrors PANEL_BOTTOM_PAD so top and bottom breathing space match.
        this.header = new Rect(panelX + UiTokens.LIST_PAD_X, panelY + UiTokens.PANEL_BOTTOM_PAD,
                panelW - UiTokens.LIST_PAD_X * 2.0F, UiTokens.HEADER_HEIGHT);
        float listTop = this.header.bottom() + UiTokens.PANEL_TOP_GAP;
        // The message list's visible area ends at the CURRENT input bar top, so
        // when the draft wraps to a second line the list gives up exactly the
        // height the bar gains. Content is therefore never painted underneath
        // the translucent composer — no images can ghost through it.
        float inputH = UiTokens.INPUT_HEIGHT + inputExtraH;
        float inputY = panelY + panelH - inputH - UiTokens.PANEL_BOTTOM_PAD;
        float listBottom = inputY;
        this.list = new Rect(panelX + UiTokens.LIST_PAD_X, listTop,
                panelW - UiTokens.LIST_PAD_X * 2.0F,
                Math.max(0.0F, listBottom - listTop));
        float replyY = inputY - this.replyH;
        this.replyBar = this.replyH > 0.0F
                ? new Rect(panelX + UiTokens.LIST_PAD_X, replyY,
                panelW - UiTokens.LIST_PAD_X * 2.0F, this.replyH)
                : new Rect(panelX + UiTokens.LIST_PAD_X, replyY, 0.0F, 0.0F);
        this.inputBar = new Rect(panelX + UiTokens.LIST_PAD_X, inputY,
                panelW - UiTokens.LIST_PAD_X * 2.0F, inputH);

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
        return of(panelX, panelY, panelW, panelH, 0.0F, 0.0F);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH, float inputExtraH) {
        return of(panelX, panelY, panelW, panelH, inputExtraH, 0.0F);
    }

    public static UiLayout of(float panelX, float panelY, float panelW, float panelH, float inputExtraH, float replyH) {
        return new UiLayout(panelX, panelY, panelW, panelH, Math.max(0.0F, inputExtraH), Math.max(0.0F, replyH));
    }

    public Rect rect() {
        return new Rect(panelX, panelY, panelW, panelH);
    }

    /**
     * Width the input text may occupy. Independent of how tall the bar is, so
     * it is safe to query before the wrap (and therefore the extra height) is
     * recomputed.
     */
    public float inputTextMaxWidth() {
        return inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F;
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
