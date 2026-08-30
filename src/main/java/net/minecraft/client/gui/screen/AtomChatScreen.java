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
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.ui.UiLayout;
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


    // Animation state
    private static final long OPEN_ANIM_MS = 220;
    private static final long MESSAGE_ANIM_MS = 250;
    private static final long SCROLL_ANIM_MS = 150;
    private static final long WHEEL_ANIM_MS = 400;
    private final long openStart = System.currentTimeMillis();
    private boolean closing;
    private long closeStart;
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
    private static final long SCROLLBAR_FADE_MS = 300;
    private static final long SCROLLBAR_IDLE_MS = 2000;
    private float scrollBarAlpha;
    private long lastScrollActivity = Long.MIN_VALUE / 2;
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

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    private int pokeIndex = -1;
    private long pokeStartTime;

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
        // No super.render: ChatScreen/Screen would draw the vanilla input box and
        // widget chrome; our UI is fully Skia-drawn, the suggestor renders explicitly.
        graphics.checkFrameBufferId();
        graphics.draw(canvas -> drawPhone(canvas, mouseX, mouseY, delta));
        // The hidden EditBox stays positioned so the IME floating window anchors
        // correctly; its text/cursor are drawn by Skia above. The suggestion popup
        // still renders through the vanilla pipeline on top.
        if (!closing && chatField != null) {
            positionInputField(UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight()));
            if (chatInputSuggestor != null) {
                chatInputSuggestor.render(context, mouseX, mouseY);
            }
        }
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
        return (int) Math.round((UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight()).inputTextCenterY - s(9)) * density / scaleFactor);
    }

    private int anchorInputLeftX() {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        return (int) Math.round((UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight()).inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor);
    }

    private void positionInputField(UiLayout layout) {
        double density = uiDensity();
        double scaleFactor = this.client.getWindow().getScaleFactor();
        chatField.setX((int) Math.round((layout.inputBar.x() + UiTokens.INPUT_TEXT_X) * density / scaleFactor));
        chatField.setY((int) Math.round((layout.inputTextCenterY - s(9)) * density / scaleFactor));
        chatField.setWidth((int) Math.max(10.0F, Math.round((layout.inputBar.w() - UiTokens.INPUT_TEXT_X * 2.0F) * density / scaleFactor)));
        chatField.setHeight((int) Math.round(s(18) * density / scaleFactor));
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

    private void drawPhone(Canvas canvas, int mouseX, int mouseY, float delta) {
        float x = panelX();
        float y = panelY();
        long now = System.currentTimeMillis();
        float progress;
        if (closing) {
            progress = 1.0F - Easing.easeOutCubic(Math.min(1.0F, (now - closeStart) / (float) OPEN_ANIM_MS));
        } else {
            progress = Easing.easeOutCubic(Math.min(1.0F, (now - openStart) / (float) OPEN_ANIM_MS));
        }
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * progress), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(x - 32.0F, y - 32.0F, panelWidth() + 64.0F, panelHeight() + 64.0F), layer);
            canvas.translate((progress - 1.0F) * 36.0F, 0.0F);
            drawPanel(canvas, x, y, mouseX, mouseY, delta);
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

    private void drawPanel(Canvas canvas, float x, float y, int mouseX, int mouseY, float delta) {
        inputFocused = chatField != null && chatField.isFocused();
        long nowMs = System.currentTimeMillis();
        frameDt = Math.min(50L, Math.max(1L, nowMs - lastFrameMs));
        lastFrameMs = nowMs;
        float emojiTarget = emojiOpen ? 1.0F : 0.0F;
        emojiAnim += (emojiTarget - emojiAnim) * Math.min(1.0F, frameDt / 140.0F);
        UiLayout layout = UiLayout.of(x, y, panelWidth(), panelHeight());
        UiLayout.Rect panel = layout.rect();
        // Phone bezel: background is inset by the full stroke width so nothing can
        // bleed outside; the white ring itself is drawn LAST (see end of method)
        // so every component sits beneath a clean edge.
        float strokeWidth = s(3);
        try (Paint bg = new Paint().setColor(panelBg())) {
            canvas.drawRRect(RRect.makeXYWH(panel.x() + strokeWidth, panel.y() + strokeWidth,
                    panel.w() - strokeWidth * 2.0F, panel.h() - strokeWidth * 2.0F, UiTokens.PANEL_RADIUS - strokeWidth), bg);
        }

        // Header: inset card, same style as the input bar.
        try (Paint header = new Paint().setColor(Color.makeARGB(60, 255, 255, 255))) {
            canvas.drawRRect(RRect.makeXYWH(layout.header.x(), layout.header.y(), layout.header.w(), layout.header.h(), s(18)), header);
        }
        Font titleFont = FontManager.font(UiTokens.FONT_TITLE);
        SkiaFontRenderer.drawText(canvas, titleFont, "世界频道", layout.header.x() + UiTokens.HEADER_PAD_X,
                SkiaFontRenderer.centerBaselineY(titleFont, layout.header.y() + layout.header.h() / 2.0F), textPrimary());
        LocalTime now = LocalTime.now();
        String time = String.format("%02d:%02d", now.getHour(), now.getMinute());
        Font timeFont = FontManager.font(UiTokens.FONT_TIME);
        SkiaFontRenderer.drawTextRight(canvas, timeFont, time, layout.header.right() - UiTokens.HEADER_PAD_X,
                layout.header.y() + layout.header.h() / 2.0F, textSecondary());

        drawMessages(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h());

        // Reply bar above input
        if (replyTarget != null) {
            float replyY = layout.inputBar.y() - s(34);
            SkiaDraw.drawRoundedRect(canvas, layout.list.x(), replyY, layout.list.w(), s(26), s(8), Color.makeARGB(90, 74, 144, 226));
            Font replyFont = FontManager.font(UiTokens.FONT_NAME);
            String replyLabel = "回复 " + (replyTarget.isOwn() ? ownName() : "玩家") + ": " + abbreviate(replyTarget.getContentText(), 26);
            SkiaFontRenderer.drawText(canvas, replyFont, replyLabel, layout.list.x() + UiTokens.QUOTE_PAD_X,
                    SkiaFontRenderer.centerBaselineY(replyFont, replyY + s(13)), textPrimary());
        }

        // Input bar: one button row (image / emoji … send), text row below
        UiLayout.Rect bar = layout.inputBar;
        SkiaDraw.drawRoundedRect(canvas, bar.x(), bar.y(), bar.w(), bar.h(), s(18), Color.makeARGB(60, 255, 255, 255));
        drawIconButton(canvas, "图片", layout.imageBtn.x(), layout.imageBtn.y(), 0, mouseX, mouseY);
        drawIconButton(canvas, "表情", layout.emojiBtn.x(), layout.emojiBtn.y(), 1, mouseX, mouseY);
        drawSendButton(canvas, layout.sendBtn.x(), layout.sendBtn.y(), mouseX, mouseY);

        // Input text: rendered by Skia at fixed density; the hidden EditBox is the
        // input backend (IME/keys) only. Cursor blinks at the vanilla cursor index.
        Font inputFont = FontManager.font(UiTokens.FONT_INPUT);
        float inputCenterY = layout.inputTextCenterY;
        String current = inputGetText();
        // Cap the visible text to the input bar width so it never overflows the bezel.
        float inputTextMaxW = bar.w() - UiTokens.INPUT_TEXT_X * 2.0F;
        String visible = current;
        if (SkiaFontRenderer.getStringWidth(inputFont, visible) > inputTextMaxW) {
            visible = truncateToWidth(inputFont, current, inputTextMaxW);
        }
        if (current.isEmpty() && !inputFocused) {
            SkiaFontRenderer.drawText(canvas, inputFont, "输入点什么，可以直接粘贴文件或图片哦~", bar.x() + UiTokens.INPUT_TEXT_X, SkiaFontRenderer.centerBaselineY(inputFont, inputCenterY), textSecondary());
        } else if (!current.isEmpty()) {
            SkiaFontRenderer.drawText(canvas, inputFont, visible, bar.x() + UiTokens.INPUT_TEXT_X, SkiaFontRenderer.centerBaselineY(inputFont, inputCenterY), textPrimary());
        }
        if (inputFocused && chatField != null && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursor = MathHelper.clamp(chatField.getCursor(), 0, current.length());
            float cursorX = bar.x() + UiTokens.INPUT_TEXT_X + SkiaFontRenderer.getStringWidth(inputFont, current.substring(0, Math.min(cursor, visible.length()))) + 2.0F;
            float cursorH = SkiaFontRenderer.textHeight(inputFont);
            SkiaDraw.drawRoundedRect(canvas, cursorX, inputCenterY - cursorH / 2.0F, 2.0F, cursorH, 1.0F, textPrimary());
        }

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
        buttonHover[id] += ((hover ? 1.0F : 0.0F) - buttonHover[id]) * Math.min(1.0F, frameDt / 120.0F);
        int fill = Math.min(255, (int) (70 + buttonHover[id] * 45.0F + (buttonPressed(id) ? 50 : 0)));
        SkiaDraw.drawRoundedRect(canvas, bx, by, UiTokens.BUTTON_W, UiTokens.BUTTON_H, UiTokens.BUTTON_RADIUS, Color.makeARGB(fill, 255, 255, 255));
        Font buttonFont = FontManager.font(UiTokens.FONT_BUTTON);
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, label, bx + UiTokens.BUTTON_W / 2.0F, by + UiTokens.BUTTON_H / 2.0F, textSecondary());
    }

    private void drawSendButton(Canvas canvas, float bx, float by, int mouseX, int mouseY) {
        float vmx = toVirtualX(mouseX);
        float vmy = toVirtualY(mouseY);
        boolean hover = vmx >= bx && vmx <= bx + UiTokens.BUTTON_W && vmy >= by && vmy <= by + UiTokens.BUTTON_H;
        buttonHover[2] += ((hover ? 1.0F : 0.0F) - buttonHover[2]) * Math.min(1.0F, frameDt / 120.0F);
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
     * Circular avatar from the player's real skin face (face + hat layer sampled
     * from the 64x64 skin). Falls back to the flat placeholder circle while the
     * skin texture is unavailable.
     */
    private void drawAvatarTexture(Canvas canvas, ChatMessage msg, float avatarX, float avatarY) {
        UUID uuid = msg.isOwn() && this.client.player != null ? this.client.player.getUuid() : null;
        String name = msg.isOwn() ? ownName() : "玩家";
        Image face = AvatarRenderer.face(SkinResolver.getSkin(uuid, name));
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, SamplingMode.DEFAULT);
            // Thin smooth ring on the rim: covers pixel stair-steps, reads as a bezel.
            try (Paint ring = new Paint().setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setColor(0xF2FFFFFF)) {
                float c = UiTokens.AVATAR_SIZE / 2.0F;
                canvas.drawCircle(avatarX + c, avatarY + c, c - s(1), ring);
            }
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
     * Rounded scrollbar: fades in while the pointer is on the message list,
     * while the view is animating, or while dragged; idles out after 2s.
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
        float fadeSpeed = Math.max(0.05F, (float) dt / (float) SCROLLBAR_FADE_MS);
        scrollBarAlpha += (target - scrollBarAlpha) * Math.min(1.0F, fadeSpeed);
        if (scrollBarAlpha < 0.01F) {
            scrollBarAlpha = 0.0F;
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
        boolean emphasized = draggingScrollbar || hover;
        scrollEmphasis += ((emphasized ? 1.0F : 0.0F) - scrollEmphasis) * Math.min(1.0F, frameDt / 150.0F);
        float w = trackW + scrollEmphasis * s(3);
        int ar = (accent() >> 16) & 0xFF;
        int ag = (accent() >> 8) & 0xFF;
        int ab = accent() & 0xFF;
        int r = (int) (255 + (ar - 255) * scrollEmphasis);
        int g = (int) (255 + (ag - 255) * scrollEmphasis);
        int bch = (int) (255 + (ab - 255) * scrollEmphasis);
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
        recomputeMaxScroll(messages, width, y, height);
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
                        try (Paint layer = new Paint()) {
                            layer.setColor(Color.makeARGB((int) (255.0F * ease), 0, 0, 0));
                            canvas.saveLayer(Rect.makeXYWH(x - 4.0F, cursorY - 4.0F, width + 8.0F, h + 28.0F), layer);
                            canvas.translate(0.0F, (1.0F - ease) * 10.0F);
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
                            hit.bubbleX(), hit.bubbleWidth(), hit.bubbleBottom() - scrollY));
                }
                cursorY += h + 10.0F;
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
            scrollY = scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * Easing.easeOutExpo(t);
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
        float bubbleHeight = textHeight + s(18);
        float nameOffset = UiTokens.AVATAR_SIZE + s(6);
        float bubbleX = msg.isOwn() ? x + maxWidth - bubbleWidth - nameOffset : x + nameOffset;

        // Name hugs the bubble's outer edge: right-aligned for own, left for others.
        String name = msg.isOwn() ? ownName() : "玩家";
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        float nameCenterY = y + UiTokens.NAME_BAND / 2.0F;
        if (msg.isOwn()) {
            SkiaFontRenderer.drawTextRight(canvas, nameFont, name, bubbleX + bubbleWidth, nameCenterY, textSecondary());
        } else {
            SkiaFontRenderer.drawText(canvas, nameFont, name, bubbleX, nameCenterY, textSecondary());
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
        SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        drawAvatarTexture(canvas, msg, avatarX, avatarY);

        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.BUBBLE_RADIUS, msg.isOwn() ? ownBubble() : otherBubble());
        SkiaFontRenderer.drawLines(canvas, font, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, textPrimary());

        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleX, bubbleWidth, bottom);
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
        float bubbleHeight = textHeight + s(10);
        float lineMax = 0.0F;
        for (String line : lines) {
            lineMax = Math.max(lineMax, SkiaFontRenderer.getStringWidth(font, line));
        }
        float bubbleWidth = Math.min(maxWidth, Math.max(s(40), lineMax + UiTokens.BUBBLE_PAD * 2.0F));
        float bubbleX = x + (maxWidth - bubbleWidth) / 2.0F;
        float bubbleTop = y + s(2);
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, s(10), Color.makeARGB(150, 35, 39, 47));
        SkiaFontRenderer.drawLines(canvas, font, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, textSecondary());
        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, 0.0F, 0.0F, 0.0F, bubbleX, bubbleWidth, bottom);
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
        String quote = msg.getQuoteName() + ": " + msg.getQuoteText();
        String display = truncateToWidth(quoteFont, quote, textMaxW);
        float pillW = Math.min(capW, SkiaFontRenderer.getStringWidth(quoteFont, display) + UiTokens.QUOTE_PAD_X * 2.0F + barW + s(4));
        float pillX = own ? x + maxWidth - UiTokens.AVATAR_SIZE - s(6) - pillW : x + UiTokens.AVATAR_SIZE + s(6);
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, UiTokens.QUOTE_HEIGHT, s(6), Color.makeARGB(70, 120, 130, 145));
        SkiaDraw.drawRoundedRect(canvas, pillX + UiTokens.QUOTE_PAD_X, pillY + s(3), barW, UiTokens.QUOTE_HEIGHT - s(6), barW / 2.0F, accent());
        SkiaFontRenderer.drawText(canvas, quoteFont, display, pillX + UiTokens.QUOTE_PAD_X + barW + s(4),
                SkiaFontRenderer.centerBaselineY(quoteFont, pillY + UiTokens.QUOTE_HEIGHT / 2.0F), textSecondary());
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
        float nameOffset = UiTokens.AVATAR_SIZE + s(6);
        String name = msg.isOwn() ? ownName() : "玩家";
        float nameX = msg.isOwn() ? x + maxWidth - UiTokens.BUBBLE_RETRACT : x + nameOffset;
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        SkiaFontRenderer.drawText(canvas, nameFont, name, nameX, SkiaFontRenderer.baselineY(nameFont, y + UiTokens.NAME_BAND / 2.0F), textSecondary());

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);
        SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        drawAvatarTexture(canvas, msg, avatarX, avatarY);

        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;

        float imageW = Math.min(s(220), maxWidth - UiTokens.BUBBLE_RETRACT - s(30));
        float imageH = s(140);
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
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleX, imageW, bottom);
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
            return s(2) + Math.max(lineHeight, lines * lineHeight) + s(10);
        }
        float quoteH = msg.getQuoteName() != null ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        if (extractImageUrl(msg.getRawText()) != null) {
            return UiTokens.NAME_BAND + quoteH + s(158);
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float wrapW = Math.max(s(20), maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.BUBBLE_PAD * 2.0F);
        int lines = SkiaFontRenderer.wrap(font, msg.getDisplayText(), wrapW).size();
        return UiTokens.NAME_BAND + quoteH + s(18) + Math.max(lineHeight, lines * lineHeight);
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

    private float emojiPanelY() {
        return panelY() + panelHeight() - UiTokens.INPUT_HEIGHT - UiTokens.PANEL_TOP_GAP - emojiPanelH() - s(6);
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
        contextAnim += (target - contextAnim) * Math.min(1.0F, frameDt / 140.0F);
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

    private void closeContextMenu() {
        if (contextMessage != null) {
            lastContextMessage = contextMessage;
        }
        closeContextMenu();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Suggestion popup scrolls first when open.
        if (chatInputSuggestor != null && chatInputSuggestor.mouseScrolled(verticalAmount)) {
            return true;
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        UiLayout.Rect list = UiLayout.of(panelX(), panelY(), panelWidth(), panelHeight()).list;
        if (list.contains((float) mx, (float) my)) {
            scrollToBottom = false;
            lastScrollActivity = System.currentTimeMillis();
            scrollTarget = Math.max(0, Math.min(scrollTarget - (float) verticalAmount * 45.0F, maxScroll));
            startScrollAnim(scrollTarget, WHEEL_ANIM_MS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) {
            return true;
        }
        // Vanilla suggestion layer gets first pick on clicks too (prevents click-through).
        if (chatInputSuggestor != null && chatInputSuggestor.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        float mx = toVirtualX(mouseX);
        float my = toVirtualY(mouseY);
        float panelX = panelX();
        float panelY = panelY();

        // Emoji panel click
        if (emojiOpen) {
            float panelX2 = emojiPanelX();
            float panelY2 = emojiPanelY();
            if (mx >= panelX2 && mx <= panelX2 + emojiPanelW() && my >= panelY2 && my <= panelY2 + emojiPanelH()) {
                String[] emojis = {"👍", "😂", "❤️", "🎉", "🔥", "😮", "😢", "👀", "✨", "💯", "🙏", "🤔"};
                int col = (int) ((mx - panelX2 - s(12)) / UiTokens.EMOJI_CELL);
                int row = (int) ((my - panelY2 - s(16)) / UiTokens.EMOJI_CELL);
                int idx = row * UiTokens.EMOJI_COLS + col;
                if (idx >= 0 && idx < emojis.length) {
                    inputAppend(emojis[idx]);
                    emojiOpen = false;
                    return true;
                }
            }
            emojiOpen = false;
        }

        // Context menu click
        if (contextMessage != null) {
            float menuW = UiTokens.MENU_W;
            float menuH = UiTokens.MENU_H;
            float menuX = Math.min(contextX, panelX + panelWidth() - menuW - s(8));
            float menuY = Math.min(contextY, panelY + panelHeight() - menuH - s(8));
            if (mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuH) {
                if (my < menuY + menuH / 2.0F) {
                    this.client.keyboard.setClipboard(contextMessage.getContentText());
                } else {
                    replyTarget = contextMessage;
                    inputFocused = true;
                    setFocused(chatField);
                    chatField.setFocused(true);
                }
                closeContextMenu();
                return true;
            }
            closeContextMenu();
        }

        // Button row: image / emoji / send share one row and one size,
        // geometry comes from UiLayout so hits can never drift from the drawing.
        UiLayout layout = UiLayout.of(panelX, panelY, panelWidth(), panelHeight());
        if (button == 0 && layout.imageBtn.contains((float) mx, (float) my)) {
            pressButton(0);
            inputFocused = true;
            pickAndUploadImage();
            return true;
        }
        if (button == 0 && layout.emojiBtn.contains((float) mx, (float) my)) {
            pressButton(1);
            inputFocused = true;
            emojiOpen = !emojiOpen;
            return true;
        }
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
            lastScrollActivity = System.currentTimeMillis();
            return true;
        }

        // Message interactions
        for (MessageHit hit : hits) {
            if (my >= hit.y() && my <= hit.bottom()) {
                if (button == 1) {
                    contextMessage = hit.message();
                    contextX = mx;
                    contextY = my;
                    return true;
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            lastScrollActivity = System.currentTimeMillis();
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

    private record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                              float avatarX, float avatarY, float avatarSize, float bubbleX, float bubbleWidth, float bubbleBottom) {
    }
}
