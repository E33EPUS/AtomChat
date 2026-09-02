package net.minecraft.client.gui.screen;
import com.atom.chat.AtomChat;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.AvatarRenderer;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.SkinResolver;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageUploader;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import com.atom.chat.util.FilePicker;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Image;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AtomChatScreen extends ChatScreen {
    private final String originalChatText;
    private final SkiaGraphics graphics = new SkiaGraphics();
    private final ImageUploader imageUploader = new ImageUploader();
    private final List<MessageHit> hits = new ArrayList<>();

    private boolean inputFocused = true;
    /** Vanilla command completion over ChatScreen's chatField, anchored to our input row. */
    private float scrollY;
    private float maxScroll;
    private ChatMessage replyTarget;
    private boolean emojiOpen;
    private ChatMessage contextMessage;
    private float contextX;
    private float contextY;


    // Animation state — durations live in UiMotion so every transition is tuned
    // in one place and none of them can drift back to a sluggish value.
    private static final long OPEN_ANIM_MS = UiMotion.PANEL_MS;
    private static final long MESSAGE_ANIM_MS = UiMotion.MESSAGE_MS;
    private static final long SCROLL_ANIM_MS = UiMotion.SCROLL_SNAP_MS;
    private static final long WHEEL_ANIM_MS = UiMotion.SCROLL_WHEEL_MS;
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    private long closeStart;
    private float panelProgress = 1.0F;
    private boolean blurDrawnThisFrame;
    private boolean firstRender = true;
    private boolean scrollToBottom = true;
    private float scrollTarget;
    private boolean scrollAnimActive;
    private float scrollAnimFrom;
    private float scrollAnimTo;
    private long scrollAnimStart;
    private long scrollAnimMs = SCROLL_ANIM_MS;
    private int pressedButton = -1;
    private long pressTime;

    // Scrollbar state (e33chat style: hover/sheet fade, drag to scroll)
    private float scrollBarAlpha;
    // Per-frame animation state (smooth hover/popup transitions)
    private final float[] buttonHover = new float[3];
    private float scrollEmphasis;
    private float emojiAnim;
    private float contextAnim;
    private ChatMessage lastContextMessage;
    private long frameDt = 16;
    private long lastFrameMs = System.currentTimeMillis();
    private boolean draggingScrollbar;
    private float dragStartY;
    private float dragStartScroll;
    private long lastScrollbarFrame;
    /** Scrollbar colour state: only a held left button turns the thumb blue. */
    private float scrollActive;

    // Multi-line input: the bar grows upward by whole line heights, and once the
    // text passes INPUT_MAX_LINES it scrolls inside the fixed box.
    private float inputExtraH;
    /**
     * Tracks the height transition as start/end/time rather than per-frame lerp:
     * UiMotion.approach is unitless, so on a pixel-valued target it only covers
     * a fraction of the distance per frame and leaves a multi-hundred-ms tail.
     * Animator guarantees the bar reaches its target height within INPUT_GROW_MS.
     */
    private final Animator inputHeightAnim = new Animator(t -> t);
    private int inputScrollLine;
    private String inputWrapText;
    private float inputWrapWidth = -1.0F;
    private List<String> inputWrapCache;

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    private int pokeIndex = -1;
    private long pokeStartTime;

    // Message text drag-selection state (Skia-drawn highlight; Ctrl+C copies).
    private ChatMessage selectionMessage;
    private int selectionAnchorLine = -1;
    private int selectionAnchorChar = -1;
    private int selectionFocusLine = -1;
    private int selectionFocusChar = -1;
    private boolean selecting;
    private boolean selectionMoved;
    private List<String> selectionMessageLines = List.of();

    public AtomChatScreen(String originalChatText) {
        super(originalChatText);
        this.originalChatText = originalChatText;
    }

    public String getOriginalChatText() {
        return originalChatText;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (closing && System.currentTimeMillis() - closeStart >= OPEN_ANIM_MS) {
            this.client.setScreen(null);
            return;
        }
        panelProgress = currentPanelProgress();

        // The blur pre-pass is raw GL and must run before Skia paints the panel.
        // Load the shader first so drawPanel knows whether it may use the
        // translucent tint or must keep the solid fallback. The actual draw
        // result is tracked in blurDrawnThisFrame so a silent shader no-op can
        // never strip the solid background again.
        blurDrawnThisFrame = false;
        if (AtomChatConfig.get().blurEnabled) {
            PanelBlurRenderer.ensureLoaded();
        }

        graphics.checkFrameBufferId();
        Runnable preUi = null;
        if (AtomChatConfig.get().blurEnabled && PanelBlurRenderer.isAvailable()) {
            preUi = () -> {
                try {
                    float strokeWidth = s(3);
                    float slide = (panelProgress - 1.0F) * 36.0F;
                    float vx = panelX() + strokeWidth + slide;
                    float vy = panelY() + strokeWidth;
                    float vw = panelWidth() - strokeWidth * 2.0F;
                    float vh = panelHeight() - strokeWidth * 2.0F;
                    float vRadius = UiTokens.PANEL_RADIUS - strokeWidth;
                    double density = uiDensity();
                    double scaleFactor = this.client.getWindow().getScaleFactor();
                    float gx = (float) (vx * density / scaleFactor);
                    float gy = (float) (vy * density / scaleFactor);
                    float gw = (float) (vw * density / scaleFactor);
                    float gh = (float) (vh * density / scaleFactor);
                    float gr = (float) (vRadius * density / scaleFactor);
                    blurDrawnThisFrame = PanelBlurRenderer.render(
                            context.getMatrices().peek().getPositionMatrix(),
                            gx, gy, gw, gh, gr, panelProgress);
                } catch (Throwable t) {
                    AtomChat.LOGGER.warn("AtomChat panel blur pre-pass failed, using solid background", t);
                    blurDrawnThisFrame = false;
                }
            };
        }

        // No super.render: ChatScreen/Screen would draw the vanilla input box and
        // widget chrome; our UI is fully Skia-drawn, the suggestor renders explicitly.
        graphics.draw(preUi, (canvas, worldSnapshot) -> drawPhone(canvas, worldSnapshot, mouseX, mouseY, delta));
        // The hidden EditBox stays positioned so the IME floating window anchors
        // correctly; its text/cursor are drawn by Skia above. The suggestion popup
        // still renders through the vanilla pipeline on top.
        if (!closing && chatField != null) {
            positionInputField(layout());
            if (chatInputSuggestor != null) {
                chatInputSuggestor.render(context, mouseX, mouseY);
            }
        }
    }

    private float currentPanelProgress() {
        long now = System.currentTimeMillis();
        if (closing) {
            return 1.0F - Easing.easeOutCubic(Math.min(1.0F, (now - closeStart) / (float) OPEN_ANIM_MS));
        }
        return Easing.easeOutCubic(Math.min(1.0F, (now - openStart) / (float) OPEN_ANIM_MS));
    }

    /** Collapses the suggestion popup and clears the gray ghost suffix. */
    private void dismissSuggestor() {
        if (chatInputSuggestor != null) {
            chatInputSuggestor.setWindowActive(false);
            chatField.setSuggestion(null);
        }
    }

    /** Maps the virtual input-text row back to GUI coordinates for the EditBox. */
    /** GUI-space anchor for the suggestion window: bottom edge of the popup. */
    private int anchorInputTopY() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        return (int) Math.round(caretLineTopY() * density / scaleFactor);
    }

    private int anchorInputLeftX() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        return (int) Math.round((layout().inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor);
    }

    private void positionInputField(UiLayout layout) {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        chatField.setX((int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor));
        chatField.setY((int) Math.round(caretLineTopY() * density / scaleFactor));
        chatField.setWidth((int) Math.max(10.0F, Math.round((layout.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * density / scaleFactor)));
        chatField.setHeight((int) Math.round(inputLineHeight() * density / scaleFactor));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // World stays fully visible, same as vanilla chat; the panel provides its own background.
    }

    @Override
    protected void init() {
        super.init();
        // Swap the vanilla suggestor for our anchored one on the same chat field;
        // ChatScreen's changed listener drives whatever sits in chatInputSuggestor.
        this.chatInputSuggestor = new AtomChatSuggestor(this.client, this, this.chatField,
                this.client.textRenderer, false, false, 1, 10, true, -805306368,
                this::anchorInputTopY, this::anchorInputLeftX);
        this.chatInputSuggestor.setCanLeave(false);
        this.chatInputSuggestor.setWindowActive(false);
    }

    private String inputGetText() {
        return chatField != null ? chatField.getText() : "";
    }

    private void inputSetText(String text) {
        if (chatField != null) {
            chatField.setText(text == null ? "" : text);
        }
    }

    private void inputAppend(String text) {
        if (chatField == null) {
            return;
        }
        String current = chatField.getText();
        if (current.length() + text.length() <= 256) {
            chatField.setText(current + text);
        }
        inputFocused = true;
        setFocused(chatField);
        chatField.setFocused(true);
    }

    private void drawPhone(Canvas canvas, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();
        float progress = panelProgress;
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * progress), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - 32.0F, y - 32.0F, panelWidth() + 64.0F, panelHeight() + 64.0F), layer);
            canvas.translate((progress - 1.0F) * 36.0F, 0.0F);
            // The world snapshot sits inside the saveLayer/translate stack so it
            // fades in with the panel and slides with it — no special handling.
            drawPanel(canvas, x, y, worldSnapshot, mouseX, mouseY, delta);
            canvas.restore();
        }
        canvas.restore();
    }

    private void requestClose() {
        if (!closing) {
            closing = true;
            closeStart = System.currentTimeMillis();
        }
    }

    @Override
    public void removed() {
        // Give back the GPU texture the panel blur was sampling.
        graphics.releaseWorldSnapshot();
        super.removed();
    }

    private void drawPanel(Canvas canvas, float x, float y, Image worldSnapshot, int mouseX, int mouseY, float delta) {
        inputFocused = chatField != null && chatField.isFocused();
        long nowMs = System.currentTimeMillis();
        frameDt = Math.min(50L, Math.max(1L, nowMs - lastFrameMs));
        lastFrameMs = nowMs;
        float emojiTarget = emojiOpen ? 1.0F : 0.0F;
        emojiAnim = UiMotion.approach(emojiAnim, emojiTarget, frameDt, UiMotion.POPUP_MS);
        UiLayout layout = layout();
        UiLayout.Rect panel = layout.rect();
        // Phone bezel: background is inset by the full stroke width so nothing can
        // bleed outside; the white ring itself is drawn LAST (see end of method)
        // so every component sits beneath a clean edge.
        float strokeWidth = s(3);
        // The raw-GL blur pre-pass already painted the rounded blurred image on
        // the main framebuffer. When it is available we only add the translucent
        // tint; otherwise the solid panelBg() stays as the safe fallback.
        boolean blurred = AtomChatConfig.get().blurEnabled && blurDrawnThisFrame;
        int tint = blurred ? UiTokens.PANEL_BLUR_TINT : panelBg();
        try (Paint bg = new Paint().setColor(tint)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth, panel.y() + strokeWidth,
                    panel.w() - strokeWidth * 2.0F, panel.h() - strokeWidth * 2.0F, UiTokens.PANEL_RADIUS - strokeWidth), bg);
        }

        // Header: inset card, same style as the input bar.
        try (Paint header = new Paint().setColor(Color.makeARGB(60, 255, 255, 255))) {
            canvas.drawRRect(RRect.makeXYWH(layout.header.x(), layout.header.y(), layout.header.w(), layout.header.h(), UiTokens.HEADER_RADIUS), header);
        }
        // Channel name is centered in the card (both axes); the clock stays
        // pinned to the right edge.
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawTextCentered(canvas, titleFont, "世界频道",
                layout.header.x() + layout.header.w() / 2.0F,
                layout.header.y() + layout.header.h() / 2.0F, textPrimary());
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time, layout.header.right() - UiTokens.HEADER_PAD_X,
                layout.header.y() + layout.header.h() / 2.0F, textPrimary());

        // Grow the input bar before the list is measured, so the list loses
        // exactly the height the bar gains.
        layout = updateInputLayout(layout);

        drawMessages(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h());

        // Reply bar floats above the input bar. It is drawn after the message
        // list so it always sits on top; the layout keeps an 8px gap below it.
        if (replyTarget != null) {
            UiLayout.Rect reply = layout.replyBar;
            float replyH = s(26);
            SkiaDraw.drawRoundedRect(canvas, reply.x(), reply.y(), reply.w(), replyH, s(8), Color.makeARGB(90, 74, 144, 226));
            Font replyFont = FontManager.font(UiTokens.FONT_NAME);
            String replyLabel = "回复 @" + (replyTarget.isOwn() ? ownName() : "玩家") + ": " + abbreviate(replyTarget.getContentText(), 26);
            SkiaFontRenderer.drawText(canvas, replyFont, replyLabel, reply.x() + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.centerBaselineY(replyFont, reply.y() + s(13)), textPrimary());
        }

        // Input bar: one button row (image / emoji … send), text row below
        UiLayout.Rect bar = layout.inputBar;
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), s(18), Color.makeARGB(60, 255, 255, 255));
        drawIconButton(canvas, "图片", layout.imageBtn.x(), layout.imageBtn.y(), 0, mouseX, mouseY);
        drawIconButton(canvas, "表情", layout.emojiBtn.x(), layout.emojiBtn.y(), 1, mouseX, mouseY);
        drawSendButton(canvas, layout.sendBtn.x(), layout.sendBtn.y(), mouseX, mouseY);

        // Input text: rendered by Skia at fixed density; the hidden EditBox is the
        // input backend (IME/keys) only. It wraps onto a second line (the bar has
        // already grown for it) and scrolls past INPUT_MAX_LINES.
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = inputLineHeight();
        String current = inputGetText();
        float textX = bar.x() + UiTokens.INPUT_TEXT_X;
        List<String> lines = wrappedInput(layout.inputTextMaxWidth());
        int total = lines.size();
        int caretRow = total == 0 ? 0 : caretLine(lines, caretIndex());
        scrollInputToCaret(caretRow, total);
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, total);
        int from = total == 0 ? 0 : Math.min(inputScrollLine, total - shown);

        // Clip to whatever the bar currently has room for, so the text can never
        // spill past the card while the height is still animating.
        float clipTop = layout.inputTextCenterY - lineH / 2.0F;
        float clipBottom = bar.bottom() - UiTokens.INPUT_ROW_PAD;
        canvas.save();
        SkiaDraw.clip(canvas, textX, clipTop, layout.inputTextMaxWidth(), Math.max(0.0F, clipBottom - clipTop), 0.0F);
        if (current.isEmpty() && !inputFocused) {
            String hint = truncateToWidth(inputFont, "输入点什么，可以直接粘贴文件或图片哦~", layout.inputTextMaxWidth());
            SkiaFontRenderer.drawText(canvas, inputFont, hint, textX,
                    SkiaFontRenderer.centerBaselineY(inputFont, layout.inputTextCenterY), textSecondary());
        } else {
            drawInputSelection(canvas, inputFont, lines, from, shown, textX, layout.inputTextCenterY, lineH);
            for (int i = from; i < from + shown && i < total; i++) {
                float cy = layout.inputTextCenterY + (i - from) * lineH;
                SkiaFontRenderer.drawText(canvas, inputFont, lines.get(i), textX,
                        SkiaFontRenderer.centerBaselineY(inputFont, cy), textPrimary());
            }
        }
        if (inputFocused && chatField != null && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caret = caretIndex();
            float cursorY;
            String measure;
            if (total == 0) {
                // Empty draft: caret sits at the start of the first visible line.
                cursorY = layout.inputTextCenterY;
                measure = "";
            } else {
                int lineStart = 0;
                for (int i = 0; i < caretRow; i++) {
                    lineStart += lines.get(i).length();
                }
                int col = MathHelper.clamp(caret - lineStart, 0, lines.get(caretRow).length());
                cursorY = layout.inputTextCenterY + (caretRow - from) * lineH;
                measure = lines.get(caretRow).substring(0, col);
            }
            float cursorX = textX + SkiaFontRenderer.getStringWidth(inputFont, measure) + 2.0F;
            float cursorH = SkiaFontRenderer.textHeight(inputFont);
            SkiaDraw.drawRoundedRect(canvas, cursorX, cursorY - cursorH / 2.0F, 2.0F, cursorH, 1.0F, textPrimary());
        }
        canvas.restore();

        // Scrollbar (e33chat style): fades in near/hinting scroll, draggable, highlights.
        drawScrollbar(canvas, layout, toVirtualX(mouseX), toVirtualY(mouseY));

        drawEmojiPanel(canvas);
        drawContextMenu(canvas);

        // Bezel ring last: nothing at the panel edge can sit on top of it.
        try (Paint border = new Paint().setMode(PaintMode.STROKE).setStrokeWidth(strokeWidth).setColor(0xFFFFFFFF)) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth / 2.0F, panel.y() + strokeWidth / 2.0F,
                    panel.w() - strokeWidth, panel.h() - strokeWidth, UiTokens.PANEL_RADIUS), border);
        }
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    private void drawIconButton(Canvas canvas, String label, float bx, float by, int id, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[id] = UiMotion.approach(buttonHover[id], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        int fill = Math.min(255, (int) (70 + buttonHover[id] * 45.0F + (buttonPressed(id) ? 50 : 0)));
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, Color.makeARGB(fill, 255, 255, 255));
        Font buttonFont = FontManager.font(UiTokens.FONT_BUTTON);
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, label, bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F, textPrimary());
    }

    private void drawSendButton(Canvas canvas, float bx, float by, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[2] = UiMotion.approach(buttonHover[2], hover ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, accent());
        float overlay = buttonHover[2] * 55.0F + (buttonPressed(2) ? 90.0F : 0.0F);
        if (overlay > 0.5F) {
            SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS,
                    Color.makeARGB((int) Math.min(160, overlay), 255, 255, 255));
        }
        Font sendFont = FontManager.font(UiTokens.FONT_BUTTON);
        SkiaFontRenderer.drawTextCentered(canvas, sendFont, "发送", bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F, textPrimary());
    }

    /**
     * Draws the hidden EditBox's selection as Skia highlight blocks over the
     * wrapped input lines. AtomChat renders its own text, so the vanilla field's
     * selection highlight would otherwise be invisible.
     */
    private void drawInputSelection(Canvas canvas, Font font, List<String> lines, int from, int shown,
                                    float textX, float centerY, float lineH) {
        if (chatField == null || lines.isEmpty()) {
            return;
        }
        int len = inputGetText().length();
        int a = MathHelper.clamp(chatField.selectionStart, 0, len);
        int b = MathHelper.clamp(chatField.selectionEnd, 0, len);
        int selStart = Math.min(a, b);
        int selEnd = Math.max(a, b);
        if (selStart >= selEnd) {
            return;
        }

        for (int i = from; i < from + shown && i < lines.size(); i++) {
            int lineStart = 0;
            for (int j = 0; j < i; j++) {
                lineStart += lines.get(j).length();
            }
            int lineLen = lines.get(i).length();
            int lineEnd = lineStart + lineLen;
            if (selEnd <= lineStart || selStart >= lineEnd) {
                continue;
            }
            int c0 = Math.max(0, Math.min(selStart - lineStart, lineLen));
            int c1 = Math.max(0, Math.min(selEnd - lineStart, lineLen));
            if (c0 >= c1) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c0));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, c1));
            float cy = centerY + (i - from) * lineH;
            SkiaDraw.drawRoundedRect(canvas, x0, cy - lineH / 2.0F, Math.max(1.0F, x1 - x0), lineH, s(2), 0xE02D6FD6);
        }
    }

    /**
     * Circular avatar from the player's real skin face (face + hat layer sampled
     * from the 64x64 skin). The face image is an opaque square; the circle is
     * produced by drawRoundedImage's clip only, so there is exactly one rounded
     * edge (no CPU mask + clip double edge, and no placeholder bleeding through
     * the avatar). Falls back to a flat gray circle while the skin is missing.
     */
    private void drawAvatar(Canvas canvas, ChatMessage msg, float avatarX, float avatarY) {
        UUID uuid = msg.isOwn() && this.client.player != null ? this.client.player.getUuid() : null;
        String name = msg.isOwn() ? ownName() : "玩家";
        Image face = AvatarRenderer.face(SkinResolver.getSkin(uuid, name));
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
    }

    private boolean buttonPressed(int id) {
        return pressedButton == id && System.currentTimeMillis() - pressTime < 150L;
    }

    private void pressButton(int id) {
        pressedButton = id;
        pressTime = System.currentTimeMillis();
    }

    /**
     * Rounded scrollbar: fades in only while the pointer is near the track or
     * while dragged, and fades straight back out otherwise.
     * Thumb maps scrollY to the track; drag maps 1:1 with clamping.
     */
    private void drawScrollbar(Canvas canvas, UiLayout layout, float vmx, float vmy) {
        long now = System.currentTimeMillis();
        long dt = Math.min(50L, now - lastScrollbarFrame);
        lastScrollbarFrame = now;

        UiLayout.Rect list = layout.list;
        float trackW = s(6);
        float trackX = list.right() - trackW - s(2);
        float trackH = list.h();
        float visibleRatio = Math.min(1.0F, trackH / (trackH + maxScroll));
        float thumbH = Math.max(s(30), trackH * visibleRatio);
        float thumbY = list.y() + (trackH - thumbH) * (scrollY / maxScroll);

        // Fade in only when the pointer is near the scrollbar itself (or dragging it).
        boolean nearTrack = vmx >= trackX - s(12) && vmx <= trackX + trackW + s(12)
                && vmy >= list.y() - s(12) && vmy <= list.bottom() + s(12);
        boolean active = maxScroll > 0.0F && (draggingScrollbar || nearTrack);
        float target = active ? 1.0F : 0.0F;
        scrollBarAlpha = UiMotion.approach(scrollBarAlpha, target, dt, UiMotion.SCROLLBAR_FADE_MS);
        if (scrollBarAlpha <= 0.0F) {
            return;
        }

        if (draggingScrollbar) {
            float travel = trackH - thumbH;
            float delta = (vmy - dragStartY) * (travel > 0.0F ? maxScroll / travel : 0.0F);
            scrollY = Math.max(0.0F, Math.min(dragStartScroll + delta, maxScroll));
            scrollTarget = scrollY;
            scrollToBottom = false;
            scrollAnimActive = false;
        }

        boolean hover = !draggingScrollbar
                && vmx >= trackX - s(8) && vmx <= trackX + trackW + s(8)
                && vmy >= list.y() && vmy <= list.bottom();
        // Two separate states: hovering only thickens the thumb (so it reads as
        // grabbable), while the accent colour is reserved for a held left button.
        scrollEmphasis = UiMotion.approach(scrollEmphasis, (hover || draggingScrollbar) ? 1.0F : 0.0F, frameDt, UiMotion.SCROLLBAR_EMPHASIS_MS);
        scrollActive = UiMotion.approach(scrollActive, draggingScrollbar ? 1.0F : 0.0F, frameDt, UiMotion.SCROLLBAR_EMPHASIS_MS);
        float w = trackW + scrollEmphasis * s(3);
        int ar = (accent() >> 16) & 0xFF;
        int ag = (accent() >> 8) & 0xFF;
        int ab = accent() & 0xFF;
        int r = (int) (255 + (ar - 255) * scrollActive);
        int g = (int) (255 + (ag - 255) * scrollActive);
        int bch = (int) (255 + (ab - 255) * scrollActive);
        int alpha = MathHelper.clamp((int) ((170 + 60 * scrollEmphasis) * scrollBarAlpha), 0, 255);
        int color = (alpha << 24) | (r << 16) | (g << 8) | bch;
        SkiaDraw.drawRoundedRect(canvas, trackX - (w - trackW) / 2.0F, thumbY, w, thumbH, w / 2.0F, color);
    }

    private boolean overScrollbarTrack(UiLayout layout, float vmx, float vmy) {
        float trackW = s(6);
        float trackX = layout.list.right() - trackW - s(2);
        return maxScroll > 0.0F
                && vmx >= trackX - s(8) && vmx <= trackX + trackW + s(8)
                && vmy >= layout.list.y() && vmy <= layout.list.bottom();
    }

    private void drawMessages(Canvas canvas, float x, float y, float width, float height) {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        hits.clear();
        // Snapshot "was at bottom" before maxScroll grows: after new messages
        // arrive the old target is no longer near the new max, so comparing after
        // recompute would make us miss the follow and leave a growing gap.
        boolean wasAtBottom = scrollToBottom || scrollTarget >= maxScroll - 3.0F;
        recomputeMaxScroll(messages, width, y, height);
        if (wasAtBottom) {
            scrollToBottom = true;
        }
        updateScrollAnimation();
        canvas.save();
        try {
            SkiaDraw.clip(canvas, x, y, width, height, 0.0F);
            canvas.translate(0.0F, -scrollY);
            long now = System.currentTimeMillis();
            float cursorY = y;
            for (ChatMessage msg : messages) {
                float h = messageHeight(msg, width);
                float offset = cursorY - y;
                if (offset > scrollY + height + 80.0F) {
                    break;
                }
                if (offset + h >= scrollY - 80.0F) {
                    float ease = Easing.easeOutCubic(Math.min(1.0F, (now - msg.getTimestamp()) / (float) MESSAGE_ANIM_MS));
                    boolean layered = ease < 0.999F;
                    canvas.save();
                    if (layered) {
                        // QQ-style entrance: own bubbles come in from the right
                        // (toward the left), other bubbles from the left. The
                        // layer rectangle must cover the full travel so a sliding
                        // bubble is never clipped by its own offscreen layer.
                        float travel = UiTokens.MESSAGE_SLIDE;
                        // System capsules are centered and have no sender side;
                        // they fade in place rather than pretending to be someone's
                        // bubble.
                        float dx = msg.isSystem() ? 0.0F
                                : msg.isOwn() ? (1.0F - ease) * travel
                                : -(1.0F - ease) * travel;
                        try (Paint layer = new Paint()) {
                            layer.setColor(Color.makeARGB((int) (255.0F * ease), 0, 0, 0));
                            canvas.saveLayer(Rect.makeXYWH(x - travel - 4.0F, cursorY - 4.0F,
                                    width + travel * 2.0F + 8.0F, h + 28.0F), layer);
                            canvas.translate(dx, 0.0F);
                        }
                    }
                    MessageHit hit = drawMessage(canvas, msg, x, cursorY, width, hits.size());
                    if (layered) {
                        canvas.restore();
                    }
                    canvas.restore();
                    // Hits are hit-tested in screen space; drawing happens in content space.
                    hits.add(new MessageHit(hit.message(), hit.index(), hit.x(), hit.y() - scrollY, hit.maxWidth(),
                            hit.bottom() - scrollY, hit.avatarX(), hit.avatarY() - scrollY, hit.avatarSize(),
                            hit.bubbleY() - scrollY, hit.bubbleX(), hit.bubbleWidth(), hit.bubbleBottom() - scrollY));
                }
                cursorY += h + UiTokens.LIST_GAP;
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * Tuui WheelUtils-style scrolling: wheel only moves a target value and the
     * actual offset eases toward it. Bottom-stickiness (e33chat pattern) retargets
     * to maxScroll while the view is at the bottom.
     */
    private void updateScrollAnimation() {
        long now = System.currentTimeMillis();
        if (firstRender) {
            scrollY = maxScroll;
            scrollTarget = maxScroll;
            scrollToBottom = false;
            firstRender = false;
            return;
        }
        // Stickiness follows the target: a wheel-up retarget immediately releases it.
        boolean wasAtBottom = scrollTarget >= maxScroll - 3.0F;
        if (scrollToBottom || wasAtBottom) {
            scrollTarget = maxScroll;
            scrollToBottom = false;
            startScrollAnim(scrollTarget, SCROLL_ANIM_MS);
        }
        if (scrollAnimActive) {
            float t = Math.min(1.0F, (now - scrollAnimStart) / (float) scrollAnimMs);
            scrollY = scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * Easing.easeOutCubic(t);
            if (t >= 1.0F) {
                scrollY = scrollAnimTo;
                scrollAnimActive = false;
            }
        }
    }

    private void startScrollAnim(float to, long durationMs) {
        scrollTarget = to;
        if (Math.abs(scrollY - to) <= 0.5F) {
            scrollY = to;
            scrollAnimActive = false;
            return;
        }
        if (scrollAnimActive && scrollAnimTo == to) {
            return;
        }
        scrollAnimFrom = scrollY;
        scrollAnimTo = to;
        scrollAnimStart = System.currentTimeMillis();
        scrollAnimMs = durationMs;
        scrollAnimActive = true;
    }

    private MessageHit drawMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        if (msg.isSystem()) {
            return drawSystemMessage(canvas, msg, x, y, maxWidth, index);
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float bubbleMaxWidth = maxWidth - UiTokens.BUBBLE_RETRACT;
        String raw = msg.getRawText();
        String imageUrl = extractImageUrl(raw);
        if (imageUrl != null) {
            return drawImageMessage(canvas, msg, raw, imageUrl, x, y, maxWidth, index);
        }
        String display = msg.getDisplayText();
        float singleLineWidth = SkiaFontRenderer.getStringWidth(font, display);
        float bubbleWidth;
        java.util.List<String> lines;
        if (singleLineWidth + UiTokens.BUBBLE_PAD * 2.0F <= bubbleMaxWidth) {
            bubbleWidth = Math.max(UiTokens.BUBBLE_MIN_W, singleLineWidth + UiTokens.BUBBLE_PAD * 2.0F);
            lines = java.util.List.of(display);
        } else {
            bubbleWidth = bubbleMaxWidth;
            lines = SkiaFontRenderer.wrap(font, display, bubbleWidth - UiTokens.BUBBLE_PAD * 2.0F);
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, lines.size() * lineHeight);

        // Layout formula: name band -> quote pill -> bubble; bubble hugs the avatar side.
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;
        float bubbleHeight = textHeight + UiTokens.BUBBLE_PAD_Y;
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float bubbleX = msg.isOwn() ? x + maxWidth - bubbleWidth - nameOffset : x + nameOffset;

        // Name hugs the bubble's outer edge: right-aligned for own, left for others.
        String name = msg.isOwn() ? ownName() : "玩家";
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        float nameCenterY = y + UiTokens.NAME_BAND / 2.0F;
        if (msg.isOwn()) {
            SkiaFontRenderer.drawTextRight(canvas, nameFont, name, bubbleX + bubbleWidth, nameCenterY, textPrimary());
        } else {
            SkiaFontRenderer.drawText(canvas, nameFont, name, bubbleX, nameCenterY, textPrimary());
        }

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);

        // Poke animation: shake avatar horizontally for ~600ms after double-click.
        if (pokeIndex == index && pokeStartTime > 0) {
            long elapsed = System.currentTimeMillis() - pokeStartTime;
            if (elapsed < 600) {
                float offset = (float) Math.sin(elapsed / 40.0) * 6.0F * (1.0F - elapsed / 600.0F);
                avatarX += offset;
            } else {
                pokeIndex = -1;
            }
        }
        drawAvatar(canvas, msg, avatarX, avatarY);

        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.BUBBLE_RADIUS, msg.isOwn() ? ownBubble() : otherBubble());
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        SkiaFontRenderer.drawLines(canvas, font, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, textPrimary());

        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * System lines (death, command feedback, join...) render as a compact
     * centered gray capsule: no avatar, no name, smaller text.
     */
    private MessageHit drawSystemMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        String display = msg.getDisplayText();
        java.util.List<String> lines = SkiaFontRenderer.wrap(font, display, maxWidth - UiTokens.BUBBLE_PAD * 2.0F);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, lines.size() * lineHeight);
        float bubbleHeight = textHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        float lineMax = 0.0F;
        for (String line : lines) {
            lineMax = Math.max(lineMax, SkiaFontRenderer.getStringWidth(font, line));
        }
        float bubbleWidth = Math.min(maxWidth, Math.max(s(40), lineMax + UiTokens.BUBBLE_PAD * 2.0F));
        float bubbleX = x + (maxWidth - bubbleWidth) / 2.0F;
        float bubbleTop = y + s(2);
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, s(10), Color.makeARGB(150, 35, 39, 47));
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        SkiaFontRenderer.drawLines(canvas, font, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, textSecondary());
        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, 0.0F, 0.0F, 0.0F, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * e33chat-style quote capsule: anchored to the avatar edge (right side for
     * own messages, left for others) with a full-row width budget, truncated
     * with an ellipsis only when exceeding that budget.
     */
    private void drawQuotePill(Canvas canvas, ChatMessage msg, float x, float maxWidth, float pillY, boolean own) {
        Font quoteFont = FontManager.font(UiTokens.FONT_QUOTE);
        float capW = maxWidth - UiTokens.AVATAR_SIZE - s(18);
        float barW = s(3);
        float textMaxW = capW - UiTokens.QUOTE_PAD_X * 2.0F - barW - s(4);
        String name = msg.getQuoteName().startsWith("@") ? msg.getQuoteName() : "@" + msg.getQuoteName();
        String quote = name + ": " + msg.getQuoteText();
        String display = truncateToWidth(quoteFont, quote, textMaxW);
        float pillW = Math.min(capW, SkiaFontRenderer.getStringWidth(quoteFont, display) + UiTokens.QUOTE_PAD_X * 2.0F + barW + s(4));
        float pillX = own ? x + maxWidth - UiTokens.AVATAR_SIZE - s(6) - pillW : x + UiTokens.AVATAR_SIZE + s(6);
        // Quote pill shares the same light gray-white fill as the header/input
        // cards (translucent white over the panel), so it reads as one family.
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, UiTokens.QUOTE_HEIGHT, s(6), Color.makeARGB(60, 255, 255, 255));
        SkiaDraw.drawRoundedRect(canvas, pillX + UiTokens.QUOTE_PAD_X, pillY + s(3), barW, UiTokens.QUOTE_HEIGHT - s(6), barW / 2.0F, accent());
        SkiaFontRenderer.drawText(canvas, quoteFont, display, pillX + UiTokens.QUOTE_PAD_X + barW + s(4),
                SkiaFontRenderer.centerBaselineY(quoteFont, pillY + UiTokens.QUOTE_HEIGHT / 2.0F), textPrimary());
    }

    private static String truncateToWidth(Font font, String text, float maxW) {
        if (SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl, float x, float y, float maxWidth, int index) {
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        String name = msg.isOwn() ? ownName() : "玩家";
        float nameX = msg.isOwn() ? x + maxWidth - UiTokens.BUBBLE_RETRACT : x + nameOffset;
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        SkiaFontRenderer.drawText(canvas, nameFont, name, nameX, SkiaFontRenderer.centerBaselineY(nameFont, y + UiTokens.NAME_BAND / 2.0F), textPrimary());

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);
        drawAvatar(canvas, msg, avatarX, avatarY);

        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;

        float imageW = Math.min(s(220), maxWidth - UiTokens.BUBBLE_RETRACT - s(30));
        float imageH = UiTokens.IMAGE_HEIGHT;
        float bubbleX = msg.isOwn() ? x + maxWidth - imageW - nameOffset : x + nameOffset;
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS, otherBubble());

        Image image = ImageLoader.get().get(imageUrl);
        if (image != null) {
            float aspect = (float) image.getWidth() / Math.max(1, image.getHeight());
            float drawH = Math.min(imageH, imageW / aspect);
            SkiaDraw.drawRoundedImage(canvas, image, bubbleX, bubbleTop + (imageH - drawH) / 2.0F, imageW, drawH, UiTokens.BUBBLE_RADIUS);
        } else {
            Font loadingFont = FontManager.font(UiTokens.FONT_QUOTE);
            SkiaFontRenderer.drawText(canvas, loadingFont, "图片加载中…", bubbleX + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.baselineY(loadingFont, bubbleTop + imageH / 2.0F), textSecondary());
        }

        float bottom = bubbleTop + imageH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, imageW, bottom);
    }

    private void recomputeMaxScroll(List<ChatMessage> messages, float width, float top, float height) {
        float contentHeight = 0;
        for (ChatMessage msg : messages) {
            contentHeight += messageHeight(msg, width) + UiTokens.LIST_GAP;
        }
        maxScroll = Math.max(0, contentHeight - height);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
    }

    /**
     * Must match what drawMessage/drawImageMessage actually lay out:
     * name band + (quote pill + gap) + bubble; image bubbles are s(140) tall.
     */
    private float messageHeight(ChatMessage msg, float maxWidth) {
        if (msg.isSystem()) {
            Font font = FontManager.font(UiTokens.FONT_QUOTE);
            float lineHeight = SkiaFontRenderer.getHeight(font);
            int lines = SkiaFontRenderer.wrap(font, msg.getDisplayText(), maxWidth - UiTokens.BUBBLE_PAD * 2.0F).size();
            return s(2) + Math.max(lineHeight, lines * lineHeight) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        }
        float quoteH = msg.getQuoteName() != null ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        if (extractImageUrl(msg.getRawText()) != null) {
            return UiTokens.NAME_BAND + quoteH + UiTokens.IMAGE_HEIGHT;
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float wrapW = Math.max(s(20), maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.BUBBLE_PAD * 2.0F);
        int lines = SkiaFontRenderer.wrap(font, msg.getDisplayText(), wrapW).size();
        return UiTokens.NAME_BAND + quoteH + UiTokens.BUBBLE_PAD_Y + Math.max(lineHeight, lines * lineHeight);
    }

    private static float emojiPanelW() {
        return UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + s(24);
    }

    private static final String[] EMOJIS = {
            "👍", "😂", "❤️", "🎉", "🔥", "😮",
            "😢", "👀", "✨", "💯", "🙏", "🤔",
            "😭", "😡", "🤡", "🙏", "👻", "🎃",
            "😎", "🤤", "😴", "🤯", "🥳", "🐱"
    };

    private static float emojiPanelH() {
        return (EMOJIS.length + UiTokens.EMOJI_COLS - 1) / UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + s(28);
    }

    private float emojiPanelX() {
        return panelX() + UiTokens.LIST_PAD_X;
    }

    /** Sits directly above the input bar, so it follows the bar's grown height. */
    private float emojiPanelY() {
        return layout().inputBar.y() - UiTokens.PANEL_TOP_GAP - emojiPanelH() - s(6);
    }

    private boolean overEmojiPanel(float mx, float my) {
        float px = emojiPanelX();
        float py = emojiPanelY();
        return mx >= px && mx <= px + emojiPanelW() && my >= py && my <= py + emojiPanelH();
    }

    private void drawEmojiPanel(Canvas canvas) {
        if (emojiAnim < 0.01F) {
            return;
        }
        float panelX = emojiPanelX();
        float panelY = emojiPanelY();
        float panelW = emojiPanelW();
        float panelH = emojiPanelH();
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * emojiAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(panelX - s(24), panelY - s(24), panelW + s(48), panelH + s(48)), layer);
            float sc = 0.92F + 0.08F * emojiAnim;
            float cx = panelX + panelW / 2.0F;
            float cy = panelY + panelH / 2.0F;
            canvas.translate(cx, cy);
            canvas.scale(sc, sc);
            canvas.translate(-cx, -cy);
            canvas.translate(0.0F, (1.0F - emojiAnim) * s(10));
            SkiaDraw.drawRoundedRect(canvas, panelX, panelY, panelW, panelH, s(14), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, panelX, panelY, panelW, panelH, s(14), s(8), Color.makeARGB(100, 0, 0, 0));

            Font emojiFont = FontManager.font(UiTokens.FONT_EMOJI);
            for (int i = 0; i < EMOJIS.length; i++) {
                int col = i % UiTokens.EMOJI_COLS;
                int row = i / UiTokens.EMOJI_COLS;
                float ex = panelX + s(12) + col * UiTokens.EMOJI_CELL;
                float ey = panelY + s(16) + row * UiTokens.EMOJI_CELL;
                SkiaFontRenderer.drawText(canvas, emojiFont, EMOJIS[i], ex, SkiaFontRenderer.centerBaselineY(emojiFont, ey + UiTokens.EMOJI_CELL / 2.0F), textPrimary());
            }
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawContextMenu(Canvas canvas) {
        ChatMessage shown = contextMessage != null ? contextMessage : lastContextMessage;
        if (shown == null) {
            contextAnim = 0.0F;
            return;
        }
        float target = contextMessage != null ? 1.0F : 0.0F;
        contextAnim = UiMotion.approach(contextAnim, target, frameDt, UiMotion.POPUP_MS);
        if (contextAnim < 0.01F) {
            if (target == 0.0F) {
                lastContextMessage = null;
                contextAnim = 0.0F;
            }
            return;
        }
        float menuW = UiTokens.MENU_W;
        float menuH = UiTokens.MENU_H;
        float menuX = Math.min(contextX, panelX() + panelWidth() - menuW - s(8));
        float menuY = Math.min(contextY, panelY() + panelHeight() - menuH - s(8));
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * contextAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menuX - s(20), menuY - s(20), menuW + s(40), menuH + s(40)), layer);
            float sc = 0.94F + 0.06F * contextAnim;
            canvas.translate(menuX + menuW / 2.0F, menuY);
            canvas.scale(sc, sc);
            canvas.translate(-(menuX + menuW / 2.0F), -menuY);
            SkiaDraw.drawRoundedRect(canvas, menuX, menuY, menuW, menuH, s(10), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, menuX, menuY, menuW, menuH, s(10), s(8), Color.makeARGB(100, 0, 0, 0));
            Font menuFont = FontManager.font(UiTokens.FONT_BUTTON);
            SkiaFontRenderer.drawText(canvas, menuFont, "复制", menuX + s(12), SkiaFontRenderer.centerBaselineY(menuFont, menuY + menuH * 0.25F), textPrimary());
            SkiaFontRenderer.drawText(canvas, menuFont, "引用", menuX + s(12), SkiaFontRenderer.centerBaselineY(menuFont, menuY + menuH * 0.75F), textPrimary());
            canvas.restore();
        }
        canvas.restore();
    }

    /**
     * Hands the open menu over to lastContextMessage so it can play the closing
     * animation instead of vanishing. Never recurse: it used to call itself,
     * which left contextMessage set forever and blew the stack on the caller.
     */
    private void closeContextMenu() {
        if (contextMessage != null) {
            lastContextMessage = contextMessage;
            contextMessage = null;
        }
    }

    private void copyToClipboard(String text) {
        try {
            this.client.keyboard.setClipboard(text);
        } catch (Throwable t) {
            // Never let a clipboard failure abort the click handler: it used to
            // leave the menu stuck open with no clue why.
            AtomChat.LOGGER.warn("Failed to copy message to clipboard", t);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Suggestion popup scrolls first when open.
        if (chatInputSuggestor != null && chatInputSuggestor.mouseScrolled(verticalAmount)) {
            return true;
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        UiLayout.Rect list = layout().list;
        if (list.contains((float) mx, (float) my)) {
            scrollToBottom = false;
            scrollTarget = Math.max(0, Math.min(scrollTarget - (float) verticalAmount * 45.0F, maxScroll));
            startScrollAnim(scrollTarget, WHEEL_ANIM_MS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---------------------------------------------------------------- message text selection

    private void clearTextSelection() {
        selectionMessage = null;
        selectionAnchorLine = -1;
        selectionAnchorChar = -1;
        selectionFocusLine = -1;
        selectionFocusChar = -1;
        selecting = false;
        selectionMoved = false;
        selectionMessageLines = List.of();
    }

    private boolean hasTextSelection() {
        if (selectionMessage == null || selectionAnchorLine < 0 || selectionFocusLine < 0) {
            return false;
        }
        return selectionAnchorLine != selectionFocusLine || selectionAnchorChar != selectionFocusChar;
    }

    private List<MessageTextLine> textLinesForHit(MessageHit hit) {
        List<MessageTextLine> out = new ArrayList<>();
        ChatMessage msg = hit.message();
        if (extractImageUrl(msg.getRawText()) != null) {
            return out;
        }
        Font font = FontManager.font(msg.isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float textMax = Math.max(s(20), hit.bubbleWidth() - UiTokens.BUBBLE_PAD * 2.0F);
        List<String> lines = SkiaFontRenderer.wrap(font, msg.getDisplayText(), textMax);
        if (lines.isEmpty()) {
            return out;
        }
        float lineH = SkiaFontRenderer.getHeight(font);
        float bubbleH = hit.bubbleBottom() - hit.bubbleY();
        float centerY = hit.bubbleY() + bubbleH / 2.0F;
        float blockTop = centerY - lines.size() * lineH / 2.0F;
        float textX = hit.bubbleX() + UiTokens.BUBBLE_PAD;
        for (int i = 0; i < lines.size(); i++) {
            out.add(new MessageTextLine(msg, i, lines.get(i), textX, blockTop + i * lineH, lineH));
        }
        return out;
    }

    private int charAtLine(MessageTextLine line, float mx) {
        String text = line.text();
        if (text.isEmpty()) {
            return 0;
        }
        Font font = FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float x = line.x();
        for (int i = 0; i < text.length(); i++) {
            float w = SkiaFontRenderer.getStringWidth(font, text.substring(i, i + 1));
            if (mx < x + w / 2.0F) {
                return i;
            }
            x += w;
        }
        return text.length();
    }

    /**
     * Draws the active selection highlight for one message before its text is
     * drawn, so the glyphs stay readable above the blue block.
     */
    private void drawMessageSelection(Canvas canvas, ChatMessage msg, List<String> lines, float textX,
                                      float centerY, float lineHeight, Font font) {
        if (selectionMessage != msg || !hasTextSelection()) {
            return;
        }
        int aLine = selectionAnchorLine;
        int aChar = selectionAnchorChar;
        int fLine = selectionFocusLine;
        int fChar = selectionFocusChar;
        if (aLine > fLine || (aLine == fLine && aChar > fChar)) {
            int tmpLine = aLine;
            aLine = fLine;
            fLine = tmpLine;
            int tmpChar = aChar;
            aChar = fChar;
            fChar = tmpChar;
        }
        float totalH = lines.size() * lineHeight;
        float blockTop = centerY - totalH / 2.0F;
        for (int i = 0; i < lines.size(); i++) {
            if (i < aLine || i > fLine) {
                continue;
            }
            int start;
            int end;
            if (aLine == fLine) {
                start = aChar;
                end = fChar;
            } else if (i == aLine) {
                start = aChar;
                end = lines.get(i).length();
            } else if (i == fLine) {
                start = 0;
                end = fChar;
            } else {
                start = 0;
                end = lines.get(i).length();
            }
            start = Math.max(0, Math.min(start, lines.get(i).length()));
            end = Math.max(0, Math.min(end, lines.get(i).length()));
            if (start >= end) {
                continue;
            }
            String line = lines.get(i);
            float x0 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, start));
            float x1 = textX + SkiaFontRenderer.getStringWidth(font, line.substring(0, end));
            float y = blockTop + i * lineHeight;
            SkiaDraw.drawRoundedRect(canvas, x0, y, Math.max(1.0F, x1 - x0), lineHeight, s(1), 0xE02D6FD6);
        }
    }

    private String copySelectedText() {
        if (!hasTextSelection()) {
            return "";
        }
        List<String> lines = selectionMessageLines;
        if (lines.isEmpty()) {
            return "";
        }
        int aLine = Math.max(0, Math.min(selectionAnchorLine, lines.size() - 1));
        int aChar = selectionAnchorChar;
        int fLine = Math.max(0, Math.min(selectionFocusLine, lines.size() - 1));
        int fChar = selectionFocusChar;
        if (aLine > fLine || (aLine == fLine && aChar > fChar)) {
            int tmpLine = aLine;
            aLine = fLine;
            fLine = tmpLine;
            int tmpChar = aChar;
            aChar = fChar;
            fChar = tmpChar;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = aLine; i <= fLine; i++) {
            String line = lines.get(i);
            int start = i == aLine ? aChar : 0;
            int end = i == fLine ? fChar : line.length();
            start = Math.max(0, Math.min(start, line.length()));
            end = Math.max(0, Math.min(end, line.length()));
            if (start < end) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line, start, end);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) {
            return true;
        }
        if (hasTextSelection() || selecting) {
            clearTextSelection();
        }
        // Vanilla suggestion layer gets first pick on clicks too (prevents click-through).
        if (chatInputSuggestor != null && chatInputSuggestor.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        float panelX = panelX();
        float panelY = panelY();
        UiLayout layout = layout();

        // The emoji toggle button is tested before the panel's own "click outside
        // dismisses" rule, otherwise closing and reopening in the same click nets
        // back to open and the button can never toggle the panel off.
        if (button == 0 && layout.emojiBtn.contains((float) mx, (float) my)) {
            pressButton(1);
            inputFocused = true;
            emojiOpen = !emojiOpen;
            return true;
        }

        // Emoji panel click. Cells must come from EMOJIS — the array that is
        // actually drawn — not a shorter local copy, or the lower rows are dead.
        if (emojiOpen) {
            if (overEmojiPanel((float) mx, (float) my)) {
                int col = (int) (((float) mx - emojiPanelX() - s(12)) / UiTokens.EMOJI_CELL);
                int row = (int) (((float) my - emojiPanelY() - s(16)) / UiTokens.EMOJI_CELL);
                if (col >= 0 && col < UiTokens.EMOJI_COLS && row >= 0) {
                    int idx = row * UiTokens.EMOJI_COLS + col;
                    if (idx < EMOJIS.length) {
                        inputAppend(EMOJIS[idx]);
                        return true;
                    }
                }
                return true; // inside the panel but between cells: swallow, don't fall through
            }
            emojiOpen = false;
        }

        // Context menu click. Remember the target before dismissing so a
        // right-click on the same bubble toggles instead of reopening.
        ChatMessage menuBefore = contextMessage;
        if (contextMessage != null) {
            float menuW = UiTokens.MENU_W;
            float menuH = UiTokens.MENU_H;
            float menuX = Math.min(contextX, panelX + panelWidth() - menuW - s(8));
            float menuY = Math.min(contextY, panelY + panelHeight() - menuH - s(8));
            boolean inside = (float) mx >= menuX && (float) mx <= menuX + menuW
                    && (float) my >= menuY && (float) my <= menuY + menuH;
            if (inside && button == 0) {
                if ((float) my < menuY + menuH / 2.0F) {
                    copyToClipboard(contextMessage.getContentText());
                } else {
                    replyTarget = contextMessage;
                    inputFocused = true;
                    setFocused(chatField);
                    chatField.setFocused(true);
                }
                closeContextMenu();
                return true;
            }
            // Any other click — including a right-click aimed at another bubble —
            // dismisses first; the handlers below may then open a new menu.
            closeContextMenu();
        }

        // Button row: image / emoji / send share one row and one size,
        // geometry comes from UiLayout so hits can never drift from the drawing.
        if (button == 0 && layout.imageBtn.contains((float) mx, (float) my)) {
            pressButton(0);
            inputFocused = true;
            pickAndUploadImage();
            return true;
        }
        // Inserting an emoji must NOT close the panel: users often want to pick several
        // in a row. The panel still closes on any outside click or the toggle button.
        if (button == 0 && layout.sendBtn.contains((float) mx, (float) my)) {
            pressButton(2);
            sendMessage(inputGetText());
            return true;
        }

        // Input box focus
        inputFocused = false;
        dismissSuggestor();
        if (button == 0 && layout.inputBar.contains((float) mx, (float) my)) {
            inputFocused = true;
            setFocused(chatField);
            chatField.setFocused(true);
            return true;
        }
        setFocused(null);
        chatField.setFocused(false);

        // Scrollbar drag start
        if (button == 0 && overScrollbarTrack(layout, mx, my)) {
            draggingScrollbar = true;
            dragStartY = my;
            dragStartScroll = scrollY;
            return true;
        }

        // Message interactions. Right-click only opens the bubble menu when the
        // pointer is actually on the bubble, not on the name band or avatar.
        for (MessageHit hit : hits) {
            if (my < hit.y() || my > hit.bottom()) {
                continue;
            }
            if (button == 1 && !hit.message().isSystem()
                    && mx >= hit.bubbleX() && mx <= hit.bubbleX() + hit.bubbleWidth()
                    && my >= hit.bubbleY() && my <= hit.bubbleBottom()) {
                // Right-clicking the bubble the menu is already on closes it.
                if (menuBefore == hit.message()) {
                    return true;
                }
                contextMessage = hit.message();
                contextX = mx;
                contextY = my;
                return true;
            }
            if (button == 0) {
                List<MessageTextLine> textLines = textLinesForHit(hit);
                for (MessageTextLine line : textLines) {
                    float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                            FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                            line.text());
                    if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                        selectionMessage = hit.message();
                        selectionMessageLines = textLines.stream().map(MessageTextLine::text).toList();
                        selectionAnchorLine = selectionFocusLine = line.line();
                        selectionAnchorChar = selectionFocusChar = charAtLine(line, mx);
                        selecting = true;
                        selectionMoved = false;
                        return true;
                    }
                }
            }
            if (button == 0 && mx >= hit.avatarX() && mx <= hit.avatarX() + hit.avatarSize()
                    && my >= hit.avatarY() && my <= hit.avatarY() + hit.avatarSize()) {
                long now = System.currentTimeMillis();
                if (now - lastAvatarClickTime < 350 && lastAvatarClickIndex == hit.index()) {
                    pokeIndex = hit.index();
                    pokeStartTime = now;
                    lastAvatarClickTime = 0;
                } else {
                    lastAvatarClickTime = now;
                    lastAvatarClickIndex = hit.index();
                    inputAppend("@" + (hit.message().isOwn() ? ownName() : "玩家") + " ");
                    inputFocused = true;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selecting && button == 0 && selectionMessage != null) {
            float mx = toVirtualX(mouseX);
            float my = toVirtualY(mouseY);
            for (MessageHit hit : hits) {
                if (my < hit.y() || my > hit.bottom() || hit.message() != selectionMessage) {
                    continue;
                }
                for (MessageTextLine line : textLinesForHit(hit)) {
                    float lineRight = line.x() + SkiaFontRenderer.getStringWidth(
                            FontManager.font(line.message().isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY),
                            line.text());
                    if (mx >= line.x() && mx <= lineRight && my >= line.y() && my <= line.y() + line.height()) {
                        int ch = charAtLine(line, mx);
                        if (ch != selectionFocusChar || line.line() != selectionFocusLine) {
                            selectionFocusLine = line.line();
                            selectionFocusChar = ch;
                            selectionMoved = true;
                        }
                        return true;
                    }
                }
            }
            return true; // drag outside text keeps current selection active
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selecting && button == 0) {
            selecting = false;
            if (!selectionMoved) {
                clearTextSelection();
            }
            return true;
        }
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Esc: animated close
            dismissSuggestor();
            requestClose();
            return true;
        }
        if (closing) {
            return true;
        }
        // Copy selected message text before the vanilla field/suggestion layer
        // consumes Ctrl+C.
        if (keyCode == 67 && (modifiers & 2) != 0 && hasTextSelection()) {
            String copied = copySelectedText();
            if (!copied.isEmpty()) {
                this.client.keyboard.setClipboard(copied);
            }
            return true;
        }
        // Vanilla suggestion layer gets first pick (Tab/arrows over the popup).
        if (chatInputSuggestor != null && chatInputSuggestor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            if (inputFocused) {
                sendMessage(inputGetText());
            }
            return true;
        }
        // Up/Down become caret navigation as soon as the text wraps onto a second
        // line (>= INPUT_MAX_LINES). Once multiline, Up/Down never fall back to
        // vanilla chat history — that remains a single-line behaviour.
        if (inputFocused && chatField != null && (keyCode == 265 || keyCode == 264)) {
            List<String> lines = wrappedInput(layout().inputTextMaxWidth());
            if (lines.size() >= UiTokens.INPUT_MAX_LINES) {
                int caret = caretIndex();
                int row = caretLine(lines, caret);
                int target = (keyCode == 265) ? row - 1 : row + 1;
                if (target >= 0 && target < lines.size()) {
                    int rowStart = 0;
                    for (int i = 0; i < row; i++) {
                        rowStart += lines.get(i).length();
                    }
                    int targetStart = 0;
                    for (int i = 0; i < target; i++) {
                        targetStart += lines.get(i).length();
                    }
                    // Move straight up/down at the same visual column, clamped to
                    // the target line's length (standard text-editor behaviour).
                    int col = MathHelper.clamp(caret - rowStart, 0, lines.get(row).length());
                    int pos = targetStart + Math.min(col, lines.get(target).length());
                    chatField.setCursor(pos, false);
                }
                return true;
            }
        }
        if (inputFocused && AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info("keyPressed: {} (sc {}) mod {}", keyCode, scanCode, modifiers);
        }
        // Falls through to super (ChatScreen): the focused chatField consumes
        // backspace/ctrl+v/arrows/IME input; up/down drive vanilla chat history.
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (closing) {
            return true;
        }
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info("charTyped: '{}' (U+{}) focused={} field={}",
                    chr, Integer.toHexString(chr), inputFocused, chatField != null && chatField.isFocused());
        }
        return super.charTyped(chr, modifiers);
    }

    private void pickAndUploadImage() {
        Thread worker = new Thread(() -> {
            Path file = FilePicker.pickImage();
            if (file == null) {
                return;
            }
            imageUploader.upload(file, url -> {
                String code = "[[CICode,url=" + url + ",name=" + file.getFileName().toString() + "]]";
                inputAppend(inputGetText().isEmpty() ? code : " " + code);
            }, error -> {
                AtomChat.LOGGER.warn("Image upload failed: {}", error);
            });
        }, "AtomChat-ImagePicker");
        worker.setDaemon(true);
        worker.start();
    }

    private void sendMessage(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return;
        }
        if (this.client.player != null) {
            String quoteName = null;
            String quoteText = null;
            if (replyTarget != null) {
                quoteName = replyTarget.isOwn() ? ownName() : "玩家";
                quoteText = abbreviate(replyTarget.getContentText(), 30);
                // Quote travels with the message so other players can see it too.
                normalized = "「引用 @" + quoteName + ": " + quoteText + "」" + normalized;
            }
            if (normalized.startsWith("/")) {
                this.client.player.networkHandler.sendChatCommand(normalized.substring(1));
            } else {
                if (!normalized.startsWith("「引用")
                        && (normalized.startsWith("http://") || normalized.startsWith("https://"))
                        && !normalized.contains("CICode")) {
                    normalized = "[[CICode,url=" + normalized + ",name=图片]]";
                }
                this.client.player.networkHandler.sendChatMessage(normalized);
            }
            this.client.inGameHud.getChatHud().addToMessageHistory(normalized);
            ChatStore.get().add(new ChatMessage(Text.literal(normalized), true, quoteName, quoteText));
            inputSetText("");
            replyTarget = null;
            inputFocused = true;
            scrollToBottom = true;
        }
    }

    private static String normalizeInput(String text) {
        return StringHelper.truncateChat(StringUtils.normalizeSpace(text.trim()));
    }

    private static String extractImageUrl(String text) {
        int start = text.indexOf("[[CICode,url=");
        if (start < 0) {
            start = text.indexOf("[CICode,url=");
        }
        if (start < 0) {
            return null;
        }
        int urlStart = text.indexOf("url=", start) + 4;
        int end = text.indexOf(',', urlStart);
        if (end < 0) {
            end = text.indexOf(']', urlStart);
        }
        if (end < 0 || end <= urlStart) {
            return null;
        }
        return text.substring(urlStart, end);
    }

    private String ownName() {
        return this.client.player != null ? this.client.player.getName().getString() : "我";
    }

    private static String abbreviate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    private int accent() {
        return AtomChatConfig.get().accentColor;
    }

    private int ownBubble() {
        return AtomChatConfig.get().ownBubbleColor;
    }

    private int otherBubble() {
        return AtomChatConfig.get().otherBubbleColor;
    }

    private int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    private int textSecondary() {
        return AtomChatConfig.get().textSecondaryColor;
    }

    private int panelBg() {
        return AtomChatConfig.get().panelBgColor;
    }

    private float panelWidth() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelWidth), vw() - 32.0F);
    }

    private float panelHeight() {
        return Math.min(UiTokens.s(AtomChatConfig.get().panelHeight), vh() - 32.0F);
    }

    // Virtual UI space: independent of vanilla GUI scale, anchored at 1080p.
    private float uiDensity() {
        var window = this.client.getWindow();
        return Math.max(1.0F, window.getFramebufferHeight() / 1080.0F);
    }

    private float vw() {
        return this.client.getWindow().getFramebufferWidth() / uiDensity();
    }

    private float vh() {
        return this.client.getWindow().getFramebufferHeight() / uiDensity();
    }

    private float panelX() {
        return UiTokens.PANEL_ANCHOR_X;
    }

    private float panelY() {
        return (vh() - panelHeight()) / 2.0F;
    }

    /**
     * The one and only way to build the layout. Rendering and every hit test go
     * through it so the input bar's animated height can never desync a click
     * from what was drawn.
     */
    private UiLayout layout() {
        float replyH = replyTarget != null ? s(34) : 0.0F;
        return UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight(), inputExtraH, replyH);
    }

    // ---------------------------------------------------------------- input box

    /**
     * Wraps the draft text, then eases the bar's extra height toward what that
     * wrap needs (0 for one line, one line height for two — never more).
     *
     * <p>Must run before the message list is measured: the bar is bottom-anchored,
     * so whatever it gains the list gives up. When the list shrinks under a view
     * that was pinned to the bottom, re-stick it, otherwise the newest message
     * would slide out of sight.</p>
     *
     * @return a layout rebuilt with the updated height.
     */
    private UiLayout updateInputLayout(UiLayout current) {
        float lineH = inputLineHeight();
        List<String> lines = wrappedInput(current.inputTextMaxWidth());
        int targetLines = Math.min(UiTokens.INPUT_MAX_LINES, Math.max(1, lines.size()));
        float targetExtra = (targetLines - 1) * lineH;
        if (Math.abs(targetExtra - inputExtraH) > 0.5F && scrollTarget >= maxScroll - 3.0F) {
            scrollToBottom = true;
        }
        inputHeightAnim.animateTo(UiMotion.INPUT_GROW_MS, targetExtra);
        inputHeightAnim.update(frameDt);
        inputExtraH = inputHeightAnim.getValue();
        return layout();
    }

    private float inputLineHeight() {
        return SkiaFontRenderer.getHeight(FontManager.font(UiTokens.FONT_INPUT));
    }

    /** Wrapped input text, cached until the text or the available width changes. */
    private List<String> wrappedInput(float maxWidth) {
        String current = inputGetText();
        if (inputWrapCache == null || inputWrapWidth != maxWidth || !current.equals(inputWrapText)) {
            inputWrapText = current;
            inputWrapWidth = maxWidth;
            inputWrapCache = SkiaFontRenderer.wrap(FontManager.font(UiTokens.FONT_INPUT), current, maxWidth);
        }
        return inputWrapCache;
    }

    /**
     * Absolute index of the line holding the caret. wrap() drops the whitespace
     * at a break point, so line lengths can sum to slightly less than the full
     * text — the mapping is exact everywhere except right at a break.
     */
    private static int caretLine(List<String> lines, int caret) {
        int pos = 0;
        for (int i = 0; i < lines.size(); i++) {
            pos += lines.get(i).length();
            if (caret <= pos) {
                return i;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int caretIndex() {
        return chatField == null ? 0 : MathHelper.clamp(chatField.getCursor(), 0, inputGetText().length());
    }

    /** Keeps the caret's line inside the visible window, clamping to the ends. */
    private void scrollInputToCaret(int caretLine, int totalLines) {
        int max = UiTokens.INPUT_MAX_LINES;
        if (totalLines <= max) {
            inputScrollLine = 0;
            return;
        }
        if (caretLine < inputScrollLine) {
            inputScrollLine = caretLine;
        } else if (caretLine > inputScrollLine + max - 1) {
            inputScrollLine = caretLine - max + 1;
        }
        inputScrollLine = Math.max(0, Math.min(inputScrollLine, totalLines - max));
    }

    /** Virtual-space top edge of the line the caret sits on, for IME anchoring. */
    private float caretLineTopY() {
        UiLayout l = layout();
        Font font = FontManager.font(UiTokens.FONT_INPUT);
        float lineH = SkiaFontRenderer.getHeight(font);
        List<String> lines = wrappedInput(l.inputTextMaxWidth());
        if (lines.isEmpty()) {
            return l.inputTextCenterY - lineH / 2.0F;
        }
        int line = caretLine(lines, caretIndex());
        int shown = Math.min(UiTokens.INPUT_MAX_LINES, lines.size());
        int from = Math.min(inputScrollLine, lines.size() - shown);
        int row = Math.max(0, Math.min(line - from, shown - 1));
        return l.inputTextCenterY + row * lineH - lineH / 2.0F;
    }

    private float toVirtualX(double guiX) {
        return (float) (guiX * this.client.getWindow().getScaleFactor() / uiDensity());
    }

    private float toVirtualY(double guiY) {
        return (float) (guiY * this.client.getWindow().getScaleFactor() / uiDensity());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record MessageTextLine(ChatMessage message, int line, String text, float x, float y, float height) {
    }

    private record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                              float avatarX, float avatarY, float avatarSize, float bubbleY, float bubbleX,
                              float bubbleWidth, float bubbleBottom) {
    }
}
