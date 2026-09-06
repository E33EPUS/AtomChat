package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.emote.EmoteImageCache;
import com.atom.chat.emote.EmoteStore;
import com.atom.chat.font.FontManager;
import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * The emoji / kaomoji / emote-pack panel: state, geometry, hit-testing and
 * rendering in one self-contained class. The screen keeps only the composer
 * side effects — text insertion, sticker sending and the file picker — via
 * {@link Host}.
 *
 * <p>Pure presentation plus pure-Java storage; every method takes the world
 * chat {@link UiLayout} so geometry can never drift from the caller's frame.</p>
 */
public final class EmojiPanel {
    /** Composer side effects the panel cannot own itself. */
    public interface Host {
        /** Appends text at the caret (emoji / kaomoji insert). */
        void insert(String text);

        /** Sends a local image file as an image message (emote tap). */
        void sendSticker(Path file);

        /** Opens the image picker for the "+" add slot (screen-owned flow). */
        void pickEmoteFile();
    }

    private static final String[] TAB_KEYS = {
            "atomchat.emoji.tab.emoji",
            "atomchat.emoji.tab.kaomoji",
            "atomchat.emoji.tab.emote"
    };

    private static final String[] EMOJIS = {
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘",
            "😋", "😛", "😜", "🤪", "😎", "🤗", "🤔", "😐",
            "😢", "😭", "😤", "😡", "🥺", "😴", "😷", "🤒",
            "🐱", "🐶", "🐼", "🐨", "🐰", "🦊", "🐸", "🐵",
            "🐭", "🐹", "🐮", "🦁", "🐯", "🐻", "🐧", "🐤",
            "🐴", "🦄", "🐝", "🐞", "🦋", "🐙", "🦀", "🐠",
            "🐷", "🐖",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔",
            "💕", "💖", "💗", "💘", "💝", "💟", "❣️", "💌",
            "👍", "👎", "👏", "🙌", "💪", "🤝", "👋", "✌️",
            "🎮", "🎯", "🎨", "🎵", "🎶", "🎤", "🎧", "🎼",
            "⭐", "🌟", "🔥", "💧", "🌈", "❄️", "🎉", "🎊",
            "🍕", "🍔", "🌮", "🍩", "🍪", "🎂", "☕", "🍺",
            "⬆️", "⬇️", "✅", "❌", "❓", "❗", "💤", "💡",
            "💀", "🗿", "🤡", "👀", "💯", "💢", "💬", "💭",
    };

    private static final String[] KAOMOJI = {
            "(｡•̀ᴗ-)✧", "(๑˃̵ᴗ˂̵)و", "(๑•̀ㅂ•́)و✧", "(◍•ᴗ•◍)",
            "╰(*°▽°*)╯", "(≧∇≦)ﾉ", "(＾▽＾)", "✧٩(ˊωˋ*)و✧",
            "ฅ^•ﻌ•^ฅ", "(•ω•)", "(￣▽￣*)", "(⌒▽⌒)☆",
            "(o゜▽゜)o☆", "＼(￣▽￣)／", "(◔◡◔)", "／(=✪ x ✪=)＼",
            "¯\\_(ツ)_/¯", "(ー_ー゛)", "(￢_￢)", "(¬_¬)",
            "(⇀‸↼‶)", "(｡ŏ_ŏ)", "(・∀・)", "_(:з」∠)_",
            "(╯°□°）╯︵ ┻━┻", "(´;ω;｀)", "Σ(°△°|||)", "(◎ロ◎)",
            "(∪.∪ )...zzz",
    };

    private final Host host;
    /** Local emote pack; see {@link EmoteStore} for the persistence rules. */
    private final EmoteStore emoteStore;
    private final EmoteImageCache emoteImageCache = new EmoteImageCache();

    private boolean open;
    private int tab;
    private int scroll;
    /** Open/close fade + scale; advances in {@link #update(long)} every frame. */
    private float anim;
    // Per-cell hover fade shared by the emoji / kaomoji / emote grids.
    private final Map<Integer, Float> cellHover = new HashMap<>();
    /** Hover washes for the tab strip. */
    private final float[] tabHover = new float[3];
    // Tab transition: double-layer content slide + sliding indicator.
    private final Animator tabContentAnim = new Animator(Easing::easeInOutCubic);
    private final Animator tabIndicatorAnim = new Animator(Easing::easeInOutCubic);
    private int tabAnimFrom = -1;
    private int tabAnimTo = -1;

    public EmojiPanel(Host host) {
        this.host = host;
        this.emoteStore = new EmoteStore(
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("atomchat/emotes"));
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    /**
     * Toggle from the composer's emoji button. Opening rescans the emote dir so
     * files dropped in by hand appear, and clears stale cell highlights.
     */
    public void toggle() {
        open = !open;
        if (open) {
            emoteStore.refresh();
            cellHover.clear();
        }
    }

    /** Full transient reset (leaving the world page): close and rewind scroll. */
    public void resetTransient() {
        open = false;
        scroll = 0;
    }

    /** Advances the open/close fade; called once per frame from the screen. */
    public void update(long frameDt) {
        anim = UiMotion.approach(anim, open ? 1.0F : 0.0F, frameDt,
                com.atom.chat.ui.Animations.ms(UiMotion.POPUP_MS));
    }

    /** Wheel scroll while the pointer is over the panel. */
    public void scroll(UiLayout layout, double verticalAmount) {
        scroll = Math.max(0, Math.min(
                scroll - (int) (verticalAmount * s(18)), maxScroll()));
    }

    public boolean overPanel(UiLayout layout, float mx, float my) {
        float px = panelX(layout);
        float py = panelY(layout);
        return mx >= px && mx <= px + panelW() && my >= py && my <= py + panelH();
    }

    /**
     * Handles a click inside the panel. Returns the text to insert ("" = the
     * click was consumed without inserting; tabs, emote actions and gutters).
     */
    public String click(UiLayout layout, float mx, float my) {
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        if (!overPanel(layout, mx, my)) {
            return "";
        }
        // Tab bar. The strip is inset by EMOJI_PANEL_PAD so it aligns with the
        // content grid below; the active pill then keeps a uniform s(4) inside it.
        if (my < py + UiTokens.EMOJI_TAB_H) {
            String[] labels = tabLabels();
            float tabInset = UiTokens.EMOJI_PANEL_PAD;
            float tabStripX = px + tabInset;
            float tabStripW = pw - tabInset * 2.0F;
            float tabW = tabStripW / labels.length;
            int t = (int) ((mx - tabStripX) / tabW);
            if (t >= 0 && t < labels.length && t != tab) {
                int from = tab;
                tab = t;
                scroll = 0;
                cellHover.clear();
                if (t == 2) {
                    // Rescan so files dropped into the emote dir by hand show up.
                    emoteStore.refresh();
                }
                startTabTransition(from, t);
            }
            return "";
        }
        // The emote (sticker) tab has its own grid — images, an add slot and a
        // per-cell remove button. It does its work inline and never inserts text.
        if (tab == 2) {
            return emotePanelClick(layout, mx, my);
        }
        // Content grid.
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float contentH = contentH();
        // The padding strips and the strip below the last visible row are dead
        // space. Unclamped maths used to wrap them around: col -1 landed on the
        // previous row's last emoji and col == cols on the next row's first, so
        // a click in the gutter silently inserted a different emoji.
        if (mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return "";
        }
        int col = (int) ((mx - contentX) / (contentW / cols));
        int row = (int) ((my - contentY + scroll) / itemH);
        col = Math.max(0, Math.min(cols - 1, col));
        int idx = row * cols + col;
        if (idx >= 0 && idx < items.length) {
            return items[idx];
        }
        return "";
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, long frameDt) {
        if (anim < 0.01F) {
            return;
        }
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float ph = panelH();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        canvas.save();
        try (Paint layer = new Paint()) {
            layer.setColor(Color.makeARGB((int) (255.0F * anim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(px - s(24), py - s(24), pw + s(48), ph + s(48)), layer);
            float sc = 0.92F + 0.08F * anim;
            float cx = px + pw / 2.0F;
            float cy = py + ph / 2.0F;
            canvas.translate(cx, cy);
            canvas.scale(sc, sc);
            canvas.translate(-cx, -cy);
            canvas.translate(0.0F, (1.0F - anim) * s(10));
            SkiaDraw.drawRoundedRect(canvas, px, py, pw, ph, UiTokens.radius(14), Color.makeARGB(245, 35, 39, 47));
            SkiaDraw.drawRoundedShadow(canvas, px, py, pw, ph, UiTokens.radius(14), s(8), Color.makeARGB(100, 0, 0, 0));

            // Tabs: the active pill slides between slots when the tab changes.
            tabIndicatorAnim.update(frameDt);
            tabContentAnim.update(frameDt);
            if (tabContentAnim.isDone()) {
                tabAnimFrom = -1;
            }
            updateTabHover(layout, vmx, vmy, frameDt);
            Font tabFont = FontManager.font(UiTokens.FONT_BUTTON);
            String[] labels = tabLabels();
            float tabInset = UiTokens.EMOJI_PANEL_PAD;
            float tabStripX = px + tabInset;
            float tabStripW = pw - tabInset * 2.0F;
            float tabW = tabStripW / labels.length;
            float indicator = tabIndicatorAnim.getValue();
            // The active pill keeps a uniform s(4) inset on every side of its tab
            // slot, and the whole strip is inset so it never crowds the panel's
            // rounded border (Apple-style calculated spacing).
            // The active pill leaves s(6) above and only s(2) below: the extra
            // bottom length makes the label's visual centre line up with the
            // pill's centre (the text baseline is drawn slightly low).
            SkiaDraw.drawRoundedRect(canvas, tabStripX + indicator * tabW + s(4), py + s(6),
                    tabW - s(8), UiTokens.EMOJI_TAB_H - s(8), s(8), Color.makeARGB(90, 255, 255, 255));
            for (int t = 0; t < labels.length; t++) {
                float hov = tabHover[t];
                if (hov > 0.01F) {
                    float hx = tabStripX + t * tabW + s(4);
                    float hy = py + s(6);
                    float hw = tabW - s(8);
                    float hh = UiTokens.EMOJI_TAB_H - s(8);
                    SkiaDraw.drawRoundedRect(canvas, hx, hy, hw, hh, s(8),
                            Color.makeARGB((int) (45.0F * hov), 255, 255, 255));
                }
                float tx = tabStripX + t * tabW;
                SkiaFontRenderer.drawTextCentered(canvas, tabFont, labels[t],
                        tx + tabW / 2.0F, py + UiTokens.EMOJI_TAB_H / 2.0F + s(2), textPrimary());
            }

            // Content area (clipped, scrollable). Switching tabs plays an opaque
            // push, like moving from one screen to the next: the outgoing tab is
            // pushed out as the incoming one slides in from the same direction,
            // both fully opaque and covering the full content width. A short
            // faded slide reads as a jitter, not a screen change.
            float contentX = px + UiTokens.EMOJI_PANEL_PAD;
            float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
            float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
            float contentH = contentH();
            updateGridHover(layout, vmx, vmy, frameDt);
            canvas.save();
            SkiaDraw.clip(canvas, contentX, contentY, contentW, contentH, 0.0F);
            boolean transitioning = tabAnimFrom >= 0 && tabAnimFrom != tab && !tabContentAnim.isDone();
            float tp = transitioning ? tabContentAnim.getValue() : 1.0F;
            if (transitioning) {
                float travel = contentW;
                float inSign = tabAnimTo > tabAnimFrom ? 1.0F : -1.0F;
                drawTabContent(canvas, tabAnimFrom, contentX, contentY, contentW, contentH,
                        -inSign * travel * tp, 1.0F, false);
                drawTabContent(canvas, tab, contentX, contentY, contentW, contentH,
                        inSign * travel * (1.0F - tp), 1.0F, true);
            } else {
                drawTabContent(canvas, tab, contentX, contentY, contentW, contentH,
                        0.0F, 1.0F, true);
            }
            canvas.restore();
            canvas.restore();
        }
        canvas.restore();
    }

    /** Adding an emote may overwrite an existing file of the same name, so the decode cache clears wholesale — ten entries, so it is cheap. */
    public void addEmote(Path file) {
        if (emoteStore.add(file.toFile())) {
            emoteImageCache.clear();
            cellHover.clear();
        }
    }

    private void startTabTransition(int from, int to) {
        tabAnimFrom = from;
        tabAnimTo = to;
        tabContentAnim.setValue(0.0F);
        tabContentAnim.animateTo(UiMotion.TAB_MS, 1.0F);
        tabIndicatorAnim.animateTo(UiMotion.TAB_MS, to);
    }

    private static String[] tabLabels() {
        String[] labels = new String[TAB_KEYS.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Text.translatable(TAB_KEYS[i]).getString();
        }
        return labels;
    }

    private static float panelW() {
        return UiTokens.EMOJI_COLS * UiTokens.EMOJI_CELL + UiTokens.EMOJI_PANEL_PAD * 2.0F;
    }

    private static float contentH() {
        return UiTokens.EMOJI_VISIBLE_ROWS * UiTokens.EMOJI_CELL;
    }

    private static float panelH() {
        return UiTokens.EMOJI_TAB_H + contentH() + UiTokens.EMOJI_PANEL_PAD;
    }

    /** Left-aligned with the message list inside the panel. */
    private static float panelX(UiLayout layout) {
        return layout.rect().x() + UiTokens.LIST_PAD_X;
    }

    /** Sits directly above the input bar, so it follows the bar's grown height. */
    private static float panelY(UiLayout layout) {
        return layout.inputBar.y() - UiTokens.PANEL_TOP_GAP - panelH() - s(6);
    }

    private int maxScroll() {
        if (tab == 2) {
            // 10 emotes in six columns fill two rows and never exceed the fixed
            // content height, so the emote grid never scrolls.
            return 0;
        }
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int rows = (items.length + cols - 1) / cols;
        float totalH = rows * itemH;
        return Math.max(0, (int) Math.ceil(totalH - contentH()));
    }

    private int gridHoverKey(int tab, int index) {
        return tab * 1000 + index;
    }

    /**
     * Hovered cell index for the active text tab (emoji/kaomoji), or -1 when the
     * pointer is over the tab bar, a gutter or outside the content area. Matches
     * click's geometry so highlight and hit-test never drift.
     */
    private int textGridHoveredIndex(int tab, float mx, float my, UiLayout layout) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int col = (int) ((mx - contentX) / (contentW / cols));
        int row = (int) ((my - contentY + scroll) / itemH);
        if (col < 0 || col >= cols) {
            return -1;
        }
        int idx = row * cols + col;
        return idx >= 0 && idx < items.length ? idx : -1;
    }

    /**
     * Hovered cell index for the emote tab (0..count, count = the "+" add slot),
     * or -1 outside the content area / tab bar / gutters.
     */
    private int emoteGridHoveredIndex(float mx, float my, UiLayout layout) {
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        if (my < py + UiTokens.EMOJI_TAB_H
                || mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return -1;
        }
        float colW = contentW / UiTokens.EMOTE_COLS;
        int col = (int) ((mx - contentX) / colW);
        int row = (int) ((my - contentY) / UiTokens.EMOTE_CELL);
        if (col < 0 || col >= UiTokens.EMOTE_COLS) {
            return -1;
        }
        int idx = row * UiTokens.EMOTE_COLS + col;
        int total = emoteStore.count() + 1;
        return idx >= 0 && idx < total ? idx : -1;
    }

    /**
     * Fades the tab-strip hover washes. The capsule geometry is the same as the
     * active pill, so hover follows the exact slots users click.
     */
    private void updateTabHover(UiLayout layout, float vmx, float vmy, long frameDt) {
        int hovered = -1;
        if (open && overPanel(layout, vmx, vmy)
                && vmy < panelY(layout) + UiTokens.EMOJI_TAB_H) {
            float px = panelX(layout);
            float pw = panelW();
            float inset = UiTokens.EMOJI_PANEL_PAD;
            float stripX = px + inset;
            float stripW = pw - inset * 2.0F;
            if (vmx >= stripX && vmx <= stripX + stripW) {
                String[] labels = tabLabels();
                float tabW = stripW / labels.length;
                int t = (int) ((vmx - stripX) / tabW);
                if (t >= 0 && t < labels.length) {
                    hovered = t;
                }
            }
        }
        for (int i = 0; i < tabHover.length; i++) {
            tabHover[i] = UiMotion.approach(tabHover[i],
                    i == hovered ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
        }
    }

    /**
     * Fades every tracked cell highlight toward its target — the pointer can only
     * hover one cell, but every other cell must still decay when the mouse moves
     * away. Called once per frame before the content layers are drawn, so both the
     * outgoing and incoming tab layers read the same per-cell state.
     */
    private void updateGridHover(UiLayout layout, float vmx, float vmy, long frameDt) {
        int hovered = -1;
        if (open && overPanel(layout, vmx, vmy) && vmy >= panelY(layout) + UiTokens.EMOJI_TAB_H) {
            hovered = tab == 2 ? emoteGridHoveredIndex(vmx, vmy, layout) : textGridHoveredIndex(tab, vmx, vmy, layout);
        }
        int hoveredKey = hovered < 0 ? -1 : gridHoverKey(tab, hovered);
        Iterator<Map.Entry<Integer, Float>> it = cellHover.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Float> e = it.next();
            boolean isHovered = e.getKey() == hoveredKey;
            float next = UiMotion.approach(e.getValue(), isHovered ? 1.0F : 0.0F, frameDt, UiMotion.HOVER_MS);
            if (!isHovered && next <= 0.001F) {
                it.remove();
            } else {
                e.setValue(next);
            }
        }
        if (hoveredKey >= 0 && !cellHover.containsKey(hoveredKey)) {
            cellHover.put(hoveredKey, 0.0F);
        }
    }

    /**
     * Draws one tab's content layer, optionally slid by dx and faded by alpha
     * during a tab switch. The old layer is non-interactive (no hover highlights)
     * because the pointer already targets the new tab.
     */
    private void drawTabContent(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                                float dx, float alpha, boolean interactive) {
        if (alpha <= 0.005F) {
            return;
        }
        boolean layered = alpha < 0.995F;
        canvas.save();
        canvas.translate(dx, 0.0F);
        if (layered) {
            try (Paint layer = new Paint()) {
                layer.setColor(Color.makeARGB((int) (255.0F * alpha), 0, 0, 0));
                // Cover the slide travel as well, or content leaving the layer's
                // bounds would be cut out of the fade.
                canvas.saveLayer(Rect.makeXYWH(contentX - s(24), contentY - s(8),
                        contentW + s(48), contentH + s(16)), layer);
                drawTabGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
            }
        } else {
            drawTabGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
        }
        if (layered) {
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawTabGrid(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                             boolean interactive) {
        if (tab == 2) {
            drawEmoteGrid(canvas, contentX, contentY, contentW, contentH, interactive);
        } else {
            drawTextGrid(canvas, tab, contentX, contentY, contentW, contentH, interactive);
        }
    }

    /**
     * Emoji/kaomoji text grid with a per-cell hover highlight that fades in and
     * out (UiMotion.HOVER_MS), the same language as the buttons and emote cells.
     * The capsule leaves a uniform s(2) margin from the cell; emoji glyphs are
     * centred inside it while kaomoji rows keep a deliberate left padding so the
     * text never touches the capsule edge.
     */
    private void drawTextGrid(Canvas canvas, int tab, float contentX, float contentY, float contentW, float contentH,
                              boolean interactive) {
        String[] items = tab == 1 ? KAOMOJI : EMOJIS;
        Font itemFont = FontManager.font(tab == 1 ? UiTokens.FONT_KAOMOJI : UiTokens.FONT_EMOJI);
        float itemH = tab == 1 ? UiTokens.EMOJI_KAOMOJI_ROW_H : UiTokens.EMOJI_CELL;
        int cols = tab == 1 ? 2 : UiTokens.EMOJI_COLS;
        float cellW = contentW / cols;
        for (int i = 0; i < items.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float ex = contentX + col * cellW;
            float ey = contentY - scroll + row * itemH;
            if (ey + itemH < contentY || ey > contentY + contentH) {
                continue;
            }
            if (interactive) {
                float hov = cellHover.getOrDefault(gridHoverKey(tab, i), 0.0F);
                if (hov > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), cellW - s(4), itemH - s(4), s(6),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                }
            }
            if (tab == 1) {
                SkiaFontRenderer.drawText(canvas, itemFont, items[i], ex + s(8),
                        SkiaFontRenderer.centerBaselineY(itemFont, ey + itemH / 2.0F), textPrimary());
            } else {
                SkiaFontRenderer.drawTextCentered(canvas, itemFont, items[i],
                        ex + cellW / 2.0F, ey + itemH / 2.0F, textPrimary());
            }
        }
    }

    /**
     * Click handling for the emote (sticker) tab: tapping an emote sends it —
     * upload the local file, drop its CICode into the draft — and closes the
     * panel so the user cannot accidentally fire several uploads. The trailing
     * "+" slot opens the picker; the hovered × deletes. Always returns "" because
     * nothing here is inserted as plain text.
     */
    private String emotePanelClick(UiLayout layout, float mx, float my) {
        float px = panelX(layout);
        float py = panelY(layout);
        float pw = panelW();
        float contentX = px + UiTokens.EMOJI_PANEL_PAD;
        float contentY = py + UiTokens.EMOJI_TAB_H + s(2);
        float contentW = pw - UiTokens.EMOJI_PANEL_PAD * 2.0F;
        float contentH = contentH();
        float colW = contentW / UiTokens.EMOTE_COLS;
        if (mx < contentX || mx > contentX + contentW
                || my < contentY || my > contentY + contentH) {
            return "";
        }
        int col = Math.max(0, Math.min(UiTokens.EMOTE_COLS - 1, (int) ((mx - contentX) / colW)));
        int row = (int) ((my - contentY) / UiTokens.EMOTE_CELL);
        int idx = row * UiTokens.EMOTE_COLS + col;
        List<File> emotes = emoteStore.list();
        if (idx < emotes.size()) {
            File emote = emotes.get(idx);
            float ex = contentX + col * colW;
            float ey = contentY + row * UiTokens.EMOTE_CELL;
            float rs = UiTokens.EMOTE_REMOVE_SIZE;
            // Remove button (top-right corner of the cell), hit before send.
            if (mx >= ex + colW - rs - s(2) && mx <= ex + colW - s(2)
                    && my >= ey + s(2) && my <= ey + s(2) + rs) {
                emoteStore.remove(emote);
                emoteImageCache.invalidate(emote);
                cellHover.clear();
                return "";
            }
            // Send: upload and close the panel (one sticker per tap).
            open = false;
            host.sendSticker(emote.toPath());
            return "";
        }
        // The trailing "+" add slot, disabled once the pack is full.
        if (idx == emotes.size() && !emoteStore.isFull()) {
            host.pickEmoteFile();
        }
        return "";
    }

    /**
     * Emote (sticker) grid: six columns of {@code s(44)} cells. Each emote is
     * fitted (never upscaled) into its cell; hovering highlights the cell and
     * shows the × remove button; the last cell is the "+" add slot, grayed out
     * when the pack is full.
     */
    private void drawEmoteGrid(Canvas canvas, float contentX, float contentY, float contentW, float contentH,
                               boolean interactive) {
        List<File> emotes = emoteStore.list();
        float cell = UiTokens.EMOTE_CELL;
        float colW = contentW / UiTokens.EMOTE_COLS;
        float pad = s(4);
        int total = emotes.size() + 1; // trailing "+" add slot
        for (int i = 0; i < total; i++) {
            int col = i % UiTokens.EMOTE_COLS;
            int row = i / UiTokens.EMOTE_COLS;
            float ex = contentX + col * colW;
            float ey = contentY + row * cell;
            if (ey + cell < contentY || ey > contentY + contentH) {
                continue;
            }
            float hov = interactive ? cellHover.getOrDefault(gridHoverKey(2, i), 0.0F) : 0.0F;
            if (i < emotes.size()) {
                File emote = emotes.get(i);
                // Image first, then the hover wash and remove button on top, so
                // the × can never be buried under the picture.
                Image img = emoteImageCache.image(emote);
                float avail = cell - pad * 2.0F;
                if (img != null) {
                    // Fit into the cell, never upscale, centred.
                    float scale = Math.min(1.0F, Math.min(avail / img.getWidth(), avail / img.getHeight()));
                    float dw = Math.max(1.0F, img.getWidth() * scale);
                    float dh = Math.max(1.0F, img.getHeight() * scale);
                    SkiaDraw.drawRoundedImage(canvas, img,
                            ex + (colW - dw) / 2.0F, ey + (cell - dh) / 2.0F, dw, dh, s(6));
                } else {
                    Font qFont = FontManager.font(UiTokens.FONT_QUOTE);
                    SkiaFontRenderer.drawTextCentered(canvas, qFont, "?", ex + colW / 2.0F, ey + cell / 2.0F,
                            textSecondary());
                }
                if (hov > 0.01F) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4), s(8),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                    // Remove button.
                    float rs = UiTokens.EMOTE_REMOVE_SIZE;
                    SkiaDraw.drawRoundedRect(canvas, ex + colW - rs - s(2), ey + s(2), rs, rs, s(4),
                            Color.makeARGB((int) (200.0F * hov), 214, 48, 48));
                    Font xFont = FontManager.font(UiTokens.FONT_QUOTE);
                    SkiaFontRenderer.drawTextCentered(canvas, xFont, "×",
                            ex + colW - rs / 2.0F - s(2), ey + s(2) + rs / 2.0F,
                            Color.makeARGB((int) (255.0F * hov), 255, 255, 255));
                }
            } else {
                boolean disabled = emoteStore.isFull();
                if (hov > 0.01F && !disabled) {
                    SkiaDraw.drawRoundedRect(canvas, ex + s(2), ey + s(2), colW - s(4), cell - s(4), s(8),
                            Color.makeARGB((int) (60.0F * hov), 255, 255, 255));
                }
                Font addFont = FontManager.font(UiTokens.FONT_EMOJI);
                SkiaFontRenderer.drawTextCentered(canvas, addFont, "+", ex + colW / 2.0F, ey + cell / 2.0F,
                        disabled ? Color.makeARGB(90, 255, 255, 255) : textPrimary());
            }
        }
    }

    /**
     * The panel is an overlay with its own opaque dark surface, so its text
     * pins the overlay palette instead of following the interface text
     * colour settings (same rule as the context menus).
     */
    private static int textPrimary() {
        return 0xFFFFFFFF;
    }

    private static int textSecondary() {
        return 0xDCAAAABA;
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }
}
