package com.atom.chat.page;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.Cicodes;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.ClickableSpan;
import com.atom.chat.render.Easing;
import com.atom.chat.render.RichTextRenderer;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.text.RichText;
import com.atom.chat.text.RichTextLayout.RichLine;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.ScrollController;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.text.Text;

/**
 * Message list presentation, split out of AtomChatScreen: rendering, entrance
 * animation, text selection and hit geometry for one conversation. The screen
 * owns the navigation-level scroll controllers and all input-side interaction
 * state; anything the view needs from the screen arrives through {@link Host}.
 */
public final class MessageListView {

    /** Screen-provided answers the view needs while drawing. */
    public interface Host {
        /** Local player uuid: own messages draw the local player's avatar face. */
        UUID ownUuid();

        /** Local player name: own messages fall back to it when the profile name is blank. */
        String ownName();

        /** Display name for a message row (own vs. other resolution stays on the screen). */
        String senderName(ChatMessage message);

        /** Screen-open timestamp: the entrance animation baseline. */
        long openStart();
    }

    public record MessageTextLine(ChatMessage message, int line, String text, float x, float y, float height) {
    }

    public record MessageHit(ChatMessage message, int index, float x, float y, float maxWidth, float bottom,
                             float avatarX, float avatarY, float avatarSize, float bubbleY, float bubbleX,
                             float bubbleWidth, float bubbleBottom) {
    }

    private static final long MESSAGE_ANIM_MS = UiMotion.MESSAGE_MS;
    private static final long ENTRANCE_SETTLE_GUARD_MS = 5000L;

    private final Host host;

    private final List<MessageHit> hits = new ArrayList<>();
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    private ChatMessage selectionMessage;
    private int selectionAnchorLine = -1;
    private int selectionAnchorChar = -1;
    private int selectionFocusLine = -1;
    private int selectionFocusChar = -1;
    private boolean selecting;
    private boolean selectionMoved;
    private List<String> selectionMessageLines = List.of();

    private final Map<ChatMessage, Long> messageEnterStart = new HashMap<>();
    private final Set<ChatMessage> messageEnterSettled = new HashSet<>();
    private long lastEntrancePrune;

    private int pokeIndex = -1;
    private long pokeStartTime;

    public MessageListView(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ public api

    public void draw(Canvas canvas, float x, float y, float width, float height,
                     List<ChatMessage> messages, ScrollController scroll) {
        hits.clear();
        clickableSpans.clear();
        // Snapshot "was at bottom" before maxScroll grows: after new messages
        // arrive the old target is no longer near the new max, so comparing after
        // recompute would make us miss the follow and leave a growing gap.
        boolean wasAtBottom = scroll.isAtBottom();
        boolean viewportChanged = scroll.viewportChanged(height);
        scroll.setContent(measureContentHeight(messages, width), height);
        if (wasAtBottom) {
            if (viewportChanged) {
                // The list is shrinking/growing in lockstep with the animated
                // input bar. Keep the bottom pinned directly: chasing the moving
                // maxScroll with an eased scroll restarts every frame and visibly
                // lags behind the bar, which is why growing felt desynced while
                // shrinking (a plain clamp) felt fine.
                scroll.scrollToBottom(false);
            } else {
                scroll.stickToBottom();
            }
        }
        scroll.updateAnimation(System.currentTimeMillis());
        canvas.save();
        try {
            SkiaDraw.clip(canvas, x, y, width, height, 0.0F);
            canvas.translate(0.0F, -scroll.getScrollY());
            long now = System.currentTimeMillis();
            pruneEntranceSettled(now);
            float cursorY = y;
            for (int mi = 0; mi < messages.size(); mi++) {
                ChatMessage msg = messages.get(mi);
                if (dividerBefore(messages, mi)) {
                    // Clock pill between messages. Drawn only when its own
                    // message is in the extended viewport, so occluded
                    // dividers cost nothing.
                    float divOffset = cursorY - y;
                    if (divOffset <= scroll.getScrollY() + height + 80.0F
                            && divOffset + TIME_DIVIDER_H >= scroll.getScrollY() - 80.0F) {
                        drawTimeDivider(canvas, msg.getTimestamp(), x, width, cursorY);
                    }
                    cursorY += TIME_DIVIDER_H + UiTokens.LIST_GAP;
                }
                float h = messageHeight(msg, width);
                float offset = cursorY - y;
                if (offset > scroll.getScrollY() + height + 80.0F) {
                    break;
                }
                if (offset + h >= scroll.getScrollY() - 80.0F) {
                    float t = entranceProgress(msg, now);
                    boolean layered = t < 1.0F;
                    if (!layered) {
                        messageEnterSettled.add(msg);
                    }
                    canvas.save();
                    if (layered) {
                        // QQ-style entrance: own bubbles come in from the right
                        // (toward the left), other bubbles from the left. The
                        // layer rectangle must cover the full travel so a sliding
                        // bubble is never clipped by its own offscreen layer.
                        float travel = UiTokens.MESSAGE_SLIDE;
                        // Two curves, one timeline: the slide decelerates hard
                        // (cubic) while the fade ramps gently across the whole
                        // entrance (quad), so the opacity change is still
                        // happening while the bubble is still moving.
                        float fade = Easing.easeOutQuad(t);
                        float move = Easing.easeOutCubic(t);
                        // System capsules are centered and have no sender side;
                        // they fade in place rather than pretending to be someone's
                        // bubble.
                        float dx = msg.isSystem() ? 0.0F
                                : msg.isOwn() ? (1.0F - move) * travel
                                : -(1.0F - move) * travel;
                        try (Paint layer = new Paint()) {
                            layer.setColor(Color.makeARGB((int) (255.0F * fade), 0, 0, 0));
                            canvas.saveLayer(Rect.makeXYWH(x - travel - 4.0F, cursorY - 4.0F,
                                    width + travel * 2.0F + 8.0F, h + 28.0F), layer);
                            canvas.translate(dx, 0.0F);
                        }
                    }
                    int spanStart = clickableSpans.size();
                    MessageHit hit = drawMessage(canvas, msg, x, cursorY, width, hits.size());
                    // Clickable spans are recorded in content space (like hits
                    // before conversion); convert them to screen space so later
                    // hit-testing can compare them directly against the mouse.
                    for (int i = spanStart; i < clickableSpans.size(); i++) {
                        ClickableSpan s = clickableSpans.get(i);
                        clickableSpans.set(i, new ClickableSpan(s.x(), s.y() - scroll.getScrollY(), s.w(), s.h(), s.style()));
                    }
                    if (layered) {
                        canvas.restore();
                    }
                    canvas.restore();
                    // Hits are hit-tested in screen space; drawing happens in content space.
                    hits.add(new MessageHit(hit.message(), hit.index(), hit.x(), hit.y() - scroll.getScrollY(), hit.maxWidth(),
                            hit.bottom() - scroll.getScrollY(), hit.avatarX(), hit.avatarY() - scroll.getScrollY(), hit.avatarSize(),
                            hit.bubbleY() - scroll.getScrollY(), hit.bubbleX(), hit.bubbleWidth(), hit.bubbleBottom() - scroll.getScrollY()));
                } else {
                    // Left the viewport: drop the start timestamp only. The
                    // settled marker is deliberately kept so scrolling back up
                    // through history never replays an entrance; the set is
                    // bounded by pruneEntranceSettled's time guard.
                    messageEnterStart.remove(msg);
                }
                cursorY += h + UiTokens.LIST_GAP;
            }
        } finally {
            canvas.restore();
        }
    }

    /** Hit geometry from the most recent {@link #draw}; valid for the same frame. */
    public List<MessageHit> hits() {
        return hits;
    }

    public boolean hasSelection() {
        if (selectionMessage == null || selectionAnchorLine < 0 || selectionFocusLine < 0) {
            return false;
        }
        return selectionAnchorLine != selectionFocusLine || selectionAnchorChar != selectionFocusChar;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public void clearSelection() {
        selectionMessage = null;
        selectionAnchorLine = -1;
        selectionAnchorChar = -1;
        selectionFocusLine = -1;
        selectionFocusChar = -1;
        selecting = false;
        selectionMoved = false;
        selectionMessageLines = List.of();
    }

    /**
     * Arms a text selection on the message line under the pointer. The screen
     * keeps its own pending-click bookkeeping (span + moved flag) and calls
     * this after it; the line list is recomputed so the copied lines always
     * match what was hit-tested.
     */
    public void beginSelection(MessageHit hit, MessageTextLine line, float mx) {
        List<MessageTextLine> textLines = textLinesForHit(hit);
        selectionMessage = hit.message();
        selectionMessageLines = textLines.stream().map(MessageTextLine::text).toList();
        selectionAnchorLine = selectionFocusLine = line.line();
        selectionAnchorChar = selectionFocusChar = charAtLine(line, mx);
        selecting = true;
        selectionMoved = false;
    }

    /**
     * Extends the active selection to the pointer. Returns whether the drag was
     * consumed; the screen translates consumption into its own pending-click
     * suppression rules.
     */
    public boolean dragSelection(float mx, float my) {
        if (!selecting || selectionMessage == null) {
            return false;
        }
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
        // A drag that leaves the text is still a drag, so it must suppress
        // any click captured on mouse press even when no selection changed.
        return true; // drag outside text keeps current selection active
    }

    /**
     * Finishes a selection drag: the selection survives when it actually moved,
     * otherwise it collapses (a clean click on text never leaves a highlight).
     */
    public void endSelection() {
        selecting = false;
        if (!selectionMoved) {
            clearSelection();
        }
    }

    public String copySelection() {
        if (!hasSelection()) {
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

    /** Arms the avatar poke wobble for one message (double-click side effect). */
    public void poke(int index, long nowMs) {
        pokeIndex = index;
        pokeStartTime = nowMs;
    }

    public Optional<ClickableSpan> clickableSpanAt(float mx, float my) {
        return Optional.ofNullable(findClickableSpan(mx, my));
    }

    /** Drops entrance-animation bookkeeping; called when the screen is removed. */
    public void dispose() {
        messageEnterStart.clear();
        messageEnterSettled.clear();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the message avatar with the poke wobble armed when this message was
     * just double-clicked: QQ-style rocking around the avatar centre (damped
     * ±14° over two and a half oscillations in ~600ms) rather than a side-to-side
     * slide. Decorative, so with motion off the poke never arms and this reduces
     * to a plain draw.
     */
    private void drawAvatarWithPoke(Canvas canvas, ChatMessage msg, int index, float avatarX, float avatarY) {
        if (pokeIndex == index && pokeStartTime > 0 && Animations.enabled()) {
            long elapsed = System.currentTimeMillis() - pokeStartTime;
            if (elapsed < 600) {
                float t = elapsed / 600.0F;
                float angle = (float) Math.sin(t * Math.PI * 5.0) * 14.0F * (1.0F - t);
                float cx = avatarX + UiTokens.AVATAR_SIZE / 2.0F;
                float cy = avatarY + UiTokens.AVATAR_SIZE / 2.0F;
                canvas.save();
                canvas.translate(cx, cy);
                canvas.rotate(angle);
                canvas.translate(-cx, -cy);
                try {
                    drawAvatar(canvas, msg, avatarX, avatarY);
                } finally {
                    canvas.restore();
                }
                return;
            }
            pokeIndex = -1;
        }
        drawAvatar(canvas, msg, avatarX, avatarY);
    }

    /**
     * Circular avatar from the player's real skin face (face + hat layer sampled
     * from the 64x64 skin). The face image is an opaque square; the circle is
     * produced by drawRoundedImage's clip only, so there is exactly one rounded
     * edge (no CPU mask + clip double edge, and no placeholder bleeding through
     * the avatar). Falls back to a flat gray circle while the skin is missing.
     */
    private void drawAvatar(Canvas canvas, ChatMessage msg, float avatarX, float avatarY) {
        UUID uuid = msg.isOwn() ? host.ownUuid() : msg.getSenderUuid();
        String name = msg.isOwn() ? host.ownName() : msg.getProfileName();
        if (name == null || name.isBlank()) {
            name = host.senderName(msg);
        }
        Image face = PlayerAvatar.face(uuid, name);
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, avatarX, avatarY, UiTokens.AVATAR_SIZE, UiTokens.AVATAR_SIZE,
                    UiTokens.AVATAR_SIZE / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
    }

    /** Clock pill height between messages (see {@link #dividerBefore}). */
    private static final float TIME_DIVIDER_H = UiTokens.s(24);

    /**
     * A time divider is drawn above a message when {@code
     * timestampIntervalMinutes} have passed since the previous one. The first
     * message of the list always carries one (e33chat behaviour); 0 disables
     * timestamps entirely.
     */
    private static boolean dividerBefore(List<ChatMessage> messages, int index) {
        int minutes = AtomChatConfig.get().timestampIntervalMinutes;
        if (minutes <= 0) {
            return false;
        }
        if (index <= 0) {
            return true;
        }
        return messages.get(index).getTimestamp() - messages.get(index - 1).getTimestamp()
                >= minutes * 60_000L;
    }

    /** The clock pill itself: same capsule family as system messages. */
    private void drawTimeDivider(Canvas canvas, long timestamp, float x, float width, float y) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        String time = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(timestamp));
        float textW = SkiaFontRenderer.getStringWidth(font, time);
        float pillW = textW + s(20);
        float pillH = TIME_DIVIDER_H - s(6);
        float pillX = x + (width - pillW) / 2.0F;
        float pillY = y + s(3);
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, pillH, UiTokens.radius(10),
                secondaryCapsuleBg());
        SkiaFontRenderer.drawTextCentered(canvas, font, time, x + width / 2.0F, pillY + pillH / 2.0F,
                secondaryCapsuleText());
    }

    /**
     * Raw 0..1 progress of a message's entrance. Messages that existed before
     * this screen was opened are already settled; messages arriving while the
     * screen is open start their animation on the first frame they are actually
     * drawn inside the viewport.
     *
     * <p>Linear on purpose: the caller picks the curve. The fade and the slide
     * must not share one — easeOutCubic covers ~88% of its distance in the
     * first half of the duration, which feels right for a slide but spends the
     * opacity ramp in ~70ms, far too fast to read as a fade.
     */
    private float entranceProgress(ChatMessage msg, long now) {
        if (!Animations.messageEntry()
                || msg.getTimestamp() < host.openStart()
                || messageEnterSettled.contains(msg)
                || now - msg.getTimestamp() > ENTRANCE_SETTLE_GUARD_MS) {
            return 1.0F;
        }
        Long start = messageEnterStart.get(msg);
        if (start == null) {
            start = now;
            messageEnterStart.put(msg, start);
        }
        return Math.min(1.0F, (now - start) / (float) MESSAGE_ANIM_MS);
    }

    /**
     * Bounded housekeeping for the never-replay guarantee: once a message is
     * older than the guard window it is settled by time alone, so its entry can
     * leave the set. Runs at most once a second.
     */
    private void pruneEntranceSettled(long now) {
        if (messageEnterSettled.isEmpty() || now - lastEntrancePrune < 1000L) {
            return;
        }
        lastEntrancePrune = now;
        long cutoff = now - ENTRANCE_SETTLE_GUARD_MS;
        messageEnterSettled.removeIf(m -> m.getTimestamp() < cutoff);
    }

    private MessageHit drawMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        if (msg.isSystem()) {
            return drawSystemMessage(canvas, msg, x, y, maxWidth, index);
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float bubbleMaxWidth = maxWidth - UiTokens.BUBBLE_RETRACT;
        String raw = msg.getRawText();
        String imageUrl = Cicodes.extractImageUrl(raw);
        if (imageUrl != null) {
            if (!AtomChatConfig.get().imageMessagesEnabled) {
                return drawImagePlaceholderMessage(canvas, msg, x, y, maxWidth, index);
            }
            return drawImageMessage(canvas, msg, raw, imageUrl, x, y, maxWidth, index);
        }
        RichText content = msg.getContentRich();
        float textMaxWidth = bubbleMaxWidth - UiTokens.BUBBLE_PAD * 2.0F;
        List<RichLine> richLines = RichTextRenderer.wrapFor(content, font, textMaxWidth);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        // The bubble must hug the longest visible line. Using the single-line
        // width of the whole message collapsed multi-line messages (e.g. a hard
        // newline between two short lines) to a pill only as wide as the bubble
        // padding, because the old expression forced a 0 width whenever there
        // was more than one line.
        float maxLineWidth = 0.0F;
        for (RichLine line : richLines) {
            maxLineWidth = Math.max(maxLineWidth, RichTextRenderer.width(font, line));
        }
        float bubbleWidth;
        if (maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F <= bubbleMaxWidth) {
            bubbleWidth = Math.max(UiTokens.BUBBLE_MIN_W, maxLineWidth + UiTokens.BUBBLE_PAD * 2.0F);
        } else {
            bubbleWidth = bubbleMaxWidth;
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
        drawMessageName(canvas, msg, y, bubbleX, bubbleX + bubbleWidth);

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);

        // Poke animation: QQ-style wobble — the avatar rocks around its centre
        // (damped ±14° over two and a half oscillations in ~600ms) instead of
        // sliding side to side. The wobble itself is decorative, so with motion
        // off the poke is already suppressed at the click site and this block
        // never arms.
        drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);

        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.BUBBLE_RADIUS, msg.isOwn() ? ownBubble() : otherBubble());
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, bubbleText(msg), clickableSpans, true);

        float bottom = bubbleTop + bubbleHeight;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, bubbleWidth, bottom);
    }

    /**
     * System lines (death, command feedback, join...) render as a compact
     * centered gray capsule: no avatar, no name, smaller text.
     */
    private MessageHit drawSystemMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        List<RichLine> richLines = RichTextRenderer.wrapFor(msg.getContentRich(), font,
                maxWidth - UiTokens.BUBBLE_PAD * 2.0F);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float textHeight = Math.max(lineHeight, lines.size() * lineHeight);
        float bubbleHeight = textHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        float lineMax = 0.0F;
        for (RichLine line : richLines) {
            lineMax = Math.max(lineMax, RichTextRenderer.width(font, line));
        }
        float bubbleWidth = Math.min(maxWidth, Math.max(s(40), lineMax + UiTokens.BUBBLE_PAD * 2.0F));
        float bubbleX = x + (maxWidth - bubbleWidth) / 2.0F;
        float bubbleTop = y + s(2);
        // System capsules share the secondary capsule family (configurable).
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, bubbleWidth, bubbleHeight, UiTokens.radius(10),
                secondaryCapsuleBg());
        drawMessageSelection(canvas, msg, lines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F, lineHeight, font);
        RichTextRenderer.drawLines(canvas, font, richLines, bubbleX + UiTokens.BUBBLE_PAD, bubbleTop + bubbleHeight / 2.0F,
                lineHeight, secondaryCapsuleText(), clickableSpans, true);
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
        String display = Cicodes.truncateToWidth(quoteFont, quote, textMaxW);
        float pillW = Math.min(capW, SkiaFontRenderer.getStringWidth(quoteFont, display) + UiTokens.QUOTE_PAD_X * 2.0F + barW + s(4));
        // Align the quote's outer edge with the bubble's outer edge, not with
        // the avatar. The bubble uses AVATAR_GAP as the horizontal gap to the
        // avatar, so the quote must use the same token.
        float pillX = own ? x + maxWidth - UiTokens.AVATAR_SIZE - UiTokens.AVATAR_GAP - pillW
                : x + UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        // Quote pill shares the secondary capsule family (configurable), so it
        // reads as the same family as system messages and time dividers.
        SkiaDraw.drawRoundedRect(canvas, pillX, pillY, pillW, UiTokens.QUOTE_HEIGHT, s(6), secondaryCapsuleBg());
        SkiaDraw.drawRoundedRect(canvas, pillX + UiTokens.QUOTE_PAD_X, pillY + s(3), barW, UiTokens.QUOTE_HEIGHT - s(6), barW / 2.0F, accent());
        float textStartX = pillX + UiTokens.QUOTE_PAD_X + barW + s(4);
        float centerBaselineY = SkiaFontRenderer.centerBaselineY(quoteFont, pillY + UiTokens.QUOTE_HEIGHT / 2.0F);
        boolean imageQuote = msg.getQuoteText() != null
                && Cicodes.isImagePlaceholder(msg.getQuoteText());
        if (imageQuote) {
            // Only the [图片]/[Image] placeholder is green; the quoted player's
            // name and the colon stay in the normal primary colour.
            String fullNamePart = name + ": ";
            float placeholderW = SkiaFontRenderer.getStringWidth(quoteFont, msg.getQuoteText());
            String namePart = Cicodes.truncateToWidth(quoteFont, fullNamePart, Math.max(0.0F, textMaxW - placeholderW));
            SkiaFontRenderer.drawText(canvas, quoteFont, namePart, textStartX, centerBaselineY, bubbleText(msg));
            float namePartW = SkiaFontRenderer.getStringWidth(quoteFont, namePart);
            SkiaFontRenderer.drawText(canvas, quoteFont, msg.getQuoteText(), textStartX + namePartW,
                    centerBaselineY, Color.makeARGB(255, 85, 255, 85));
        } else {
            SkiaFontRenderer.drawText(canvas, quoteFont, display, textStartX, centerBaselineY, bubbleText(msg));
        }
    }

    /**
     * Draws a message's name hugging its bubble's outer edge. One helper for
     * text and image bubbles so their spacing can never drift apart: the image
     * path centred the name in the band while the text path drew it from the raw
     * baseline, which put the two a cap-height apart.
     */
    private void drawMessageName(Canvas canvas, ChatMessage msg, float rowY, float leftX, float rightX) {
        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        RichText sender = msg.getSenderRich();
        if (sender.isEmpty()) {
            sender = RichText.literal(host.senderName(msg));
        }
        List<RichLine> lines = RichTextRenderer.wrapFor(sender, nameFont, Float.MAX_VALUE);
        if (lines.isEmpty()) {
            return;
        }
        float lineHeight = SkiaFontRenderer.getHeight(nameFont);
        float nameWidth = 0.0F;
        for (RichLine line : lines) {
            nameWidth = Math.max(nameWidth, RichTextRenderer.width(nameFont, line));
        }
        float x = msg.isOwn() ? rightX - nameWidth : leftX;
        // RichTextRenderer.drawLines takes a centerY and internally converts
        // it to the cap-height baseline, matching the old drawText helper.
        float centerY = rowY + UiTokens.NAME_BAND / 2.0F;
        RichTextRenderer.drawLines(canvas, nameFont, lines, x, centerY, lineHeight, textPrimary(),
                clickableSpans, true);
    }

    private MessageHit drawImageMessage(Canvas canvas, ChatMessage msg, String raw, String imageUrl, float x, float y, float maxWidth, int index) {
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        float bubbleTop = y + UiTokens.NAME_BAND + quoteH;
        float[] size = Cicodes.imageBubbleSize(Cicodes.parseImageMeta(raw), maxWidth);
        float imageW = size[0];
        float imageH = size[1];
        float bubbleX = msg.isOwn() ? x + maxWidth - imageW - nameOffset : x + nameOffset;

        // Name hugs the bubble's outer edge, exactly like a text bubble.
        // Anchoring it to the row instead (the old behaviour) left the name
        // drifting away from the bubble as soon as the bubble width changed —
        // image bubbles are always wider than a short text bubble.
        drawMessageName(canvas, msg, y, bubbleX, bubbleX + imageW);

        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);
        drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS, otherBubble());

        Image image = ImageLoader.get().get(imageUrl, true);
        if (image != null) {
            // No aspect fix-up here: the CICode carries the intrinsic size, so
            // the bubble already has the image's proportions and the bitmap is
            // simply fitted to it. Stretching only happened because the box used
            // to be a fixed 275x175 with the height clamped rather than scaled.
            SkiaDraw.drawRoundedImage(canvas, image, bubbleX, bubbleTop, imageW, imageH, UiTokens.BUBBLE_RADIUS);
        } else {
            Font loadingFont = FontManager.font(UiTokens.FONT_QUOTE);
            SkiaFontRenderer.drawTextCentered(canvas, loadingFont, tr("atomchat.image.loading"),
                    bubbleX + imageW / 2.0F, bubbleTop + imageH / 2.0F, textSecondary());
        }

        float bottom = bubbleTop + imageH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, bubbleTop, bubbleX, imageW, bottom);
    }

    /**
     * Image messages with the receiving toggle off: a compact capsule with the
     * green [图片] placeholder — the same mark as the vanilla HUD, and nothing
     * is fetched or decoded. Identity (name + avatar) stays so the sender is
     * still readable.
     */
    private MessageHit drawImagePlaceholderMessage(Canvas canvas, ChatMessage msg, float x, float y, float maxWidth, int index) {
        boolean hasQuote = msg.getQuoteName() != null;
        float quoteH = hasQuote ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        Font font = FontManager.font(UiTokens.FONT_QUOTE);
        String placeholder = tr("atomchat.hud.image");
        float textW = SkiaFontRenderer.getStringWidth(font, placeholder);
        float pillW = Math.min(maxWidth - UiTokens.BUBBLE_RETRACT, textW + UiTokens.BUBBLE_PAD * 2.0F);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float pillH = lineHeight + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        float nameOffset = UiTokens.AVATAR_SIZE + UiTokens.AVATAR_GAP;
        float pillX = msg.isOwn() ? x + maxWidth - pillW - nameOffset : x + nameOffset;
        float pillTop = y + UiTokens.NAME_BAND + quoteH;
        drawMessageName(canvas, msg, y, pillX, pillX + pillW);
        float avatarX = msg.isOwn() ? x + maxWidth - UiTokens.AVATAR_SIZE : x;
        float avatarY = y + s(4);
        drawAvatarWithPoke(canvas, msg, index, avatarX, avatarY);
        if (hasQuote) {
            drawQuotePill(canvas, msg, x, maxWidth, y + UiTokens.NAME_BAND, msg.isOwn());
        }
        SkiaDraw.drawRoundedRect(canvas, pillX, pillTop, pillW, pillH, UiTokens.radius(10),
                secondaryCapsuleBg());
        SkiaFontRenderer.drawTextCentered(canvas, font, placeholder,
                pillX + pillW / 2.0F, pillTop + pillH / 2.0F, Color.makeARGB(255, 85, 255, 85));
        float bottom = pillTop + pillH;
        return new MessageHit(msg, index, x, y, maxWidth, bottom, avatarX, avatarY, UiTokens.AVATAR_SIZE, pillTop, pillX, pillW, bottom);
    }

    private float measureContentHeight(List<ChatMessage> messages, float width) {
        float contentHeight = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (dividerBefore(messages, i)) {
                contentHeight += TIME_DIVIDER_H + UiTokens.LIST_GAP;
            }
            contentHeight += messageHeight(messages.get(i), width) + UiTokens.LIST_GAP;
        }
        return contentHeight;
    }

    /**
     * Must match what drawMessage/drawImageMessage actually lay out:
     * name band + (quote pill + gap) + bubble; image bubbles are s(140) tall.
     */
    private float messageHeight(ChatMessage msg, float maxWidth) {
        if (msg.isSystem()) {
            Font font = FontManager.font(UiTokens.FONT_QUOTE);
            float lineHeight = SkiaFontRenderer.getHeight(font);
            int lines = RichTextRenderer.wrapFor(msg.getContentRich(), font,
                    maxWidth - UiTokens.BUBBLE_PAD * 2.0F).size();
            return s(2) + Math.max(lineHeight, lines * lineHeight) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
        }
        float quoteH = msg.getQuoteName() != null ? UiTokens.QUOTE_HEIGHT + UiTokens.QUOTE_GAP : 0.0F;
        Cicodes.ImageMeta imageMeta = Cicodes.parseImageMeta(msg.getRawText());
        if (imageMeta != null) {
            if (!AtomChatConfig.get().imageMessagesEnabled) {
                Font font = FontManager.font(UiTokens.FONT_QUOTE);
                return UiTokens.NAME_BAND + quoteH
                        + SkiaFontRenderer.getHeight(font) + UiTokens.SYSTEM_BUBBLE_PAD_Y;
            }
            return UiTokens.NAME_BAND + quoteH + Cicodes.imageBubbleSize(imageMeta, maxWidth)[1];
        }
        Font font = FontManager.font(UiTokens.FONT_BODY);
        float lineHeight = SkiaFontRenderer.getHeight(font);
        float wrapW = Math.max(s(20), maxWidth - UiTokens.BUBBLE_RETRACT - UiTokens.BUBBLE_PAD * 2.0F);
        int lines = RichTextRenderer.wrapFor(msg.getContentRich(), font, wrapW).size();
        return UiTokens.NAME_BAND + quoteH + UiTokens.BUBBLE_PAD_Y + Math.max(lineHeight, lines * lineHeight);
    }

    // ------------------------------------------------------------------ text selection

    public List<MessageTextLine> textLinesForHit(MessageHit hit) {
        List<MessageTextLine> out = new ArrayList<>();
        ChatMessage msg = hit.message();
        if (Cicodes.extractImageUrl(msg.getRawText()) != null) {
            return out;
        }
        Font font = FontManager.font(msg.isSystem() ? UiTokens.FONT_QUOTE : UiTokens.FONT_BODY);
        float textMax = Math.max(s(20), hit.bubbleWidth() - UiTokens.BUBBLE_PAD * 2.0F);
        List<RichLine> richLines = RichTextRenderer.wrapFor(msg.getContentRich(), font, textMax);
        List<String> lines = new ArrayList<>();
        for (RichLine line : richLines) {
            lines.add(line.getPlainText());
        }
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
        if (selectionMessage != msg || !hasSelection()) {
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

    private ClickableSpan findClickableSpan(float mx, float my) {
        for (ClickableSpan s : clickableSpans) {
            if (mx >= s.x() && mx <= s.x() + s.w() && my >= s.y() && my <= s.y() + s.h()) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tokens

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Minecraft language lookup for all AtomChat UI copy. */
    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
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

    /** Shared capsule background for system messages, time dividers, quote pills. */
    private int secondaryCapsuleBg() {
        return AtomChatConfig.get().secondaryCapsuleBg;
    }

    private int secondaryCapsuleText() {
        return AtomChatConfig.get().secondaryCapsuleText;
    }

    /** Text inside a chat bubble (body rich text and quoted text). */
    private int bubbleText(ChatMessage msg) {
        return msg != null && msg.isOwn()
                ? AtomChatConfig.get().bubbleTextColor
                : AtomChatConfig.get().otherBubbleTextColor;
    }
}
