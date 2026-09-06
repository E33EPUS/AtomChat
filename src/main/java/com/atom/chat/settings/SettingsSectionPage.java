package com.atom.chat.settings;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.ToggleSwitch;
import com.atom.chat.wallpaper.WallpaperStore;

import java.nio.file.Path;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.SamplingMode;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Body of one settings section: a vertical list of cards in the same visual
 * language as the conversation list — switch rows, slider rows, read-only info
 * rows (optionally linking out) and the blocked-player list.
 *
 * <p>Row geometry comes from {@link #rows(SettingsSection)} for order and
 * {@link #rowRect(List, int, float, UiLayout)} for position, so rendering,
 * hit-testing and measurement can never disagree.</p>
 */
public final class SettingsSectionPage {
    public enum RowKind { HERO, SWITCH, SLIDER, COLOR, INFO, BLOCKED, LABEL, ACTION }

    public record Row(RowKind kind, SettingsItem item, SettingsSlider slider,
                      SettingsColor color, SettingsCatalog.InfoRow info, PlayerRef player,
                      String labelKey, String actionId) {
        static Row ofSwitch(SettingsItem item) {
            return new Row(RowKind.SWITCH, item, null, null, null, null, null, null);
        }

        static Row ofSlider(SettingsSlider slider) {
            return new Row(RowKind.SLIDER, null, slider, null, null, null, null, null);
        }

        static Row ofColor(SettingsColor color) {
            return new Row(RowKind.COLOR, null, null, color, null, null, null, null);
        }

        static Row ofInfo(SettingsCatalog.InfoRow info) {
            return new Row(RowKind.INFO, null, null, null, info, null, null, null);
        }

        static Row ofBlocked(PlayerRef player) {
            return new Row(RowKind.BLOCKED, null, null, null, null, player, null, null);
        }

        static Row ofLabel(String key) {
            return new Row(RowKind.LABEL, null, null, null, null, null, key, null);
        }

        static Row ofHero() {
            return new Row(RowKind.HERO, null, null, null, null, null, null, null);
        }

        static Row ofAction(String actionId, SettingsItem item) {
            return new Row(RowKind.ACTION, item, null, null, null, null, null, actionId);
        }
    }

    /**
     * @param actionX left edge of the clickable action: the whole row for a
     *                switch or a link, the unblock button for a blocked player.
     */
    public record RowHit(Row row, int index, float x, float y, float w, float h, float actionX) {
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        public boolean onAction(float px, float py) {
            // LABEL rows pass too: foldable colour groups toggle on click,
            // plain labels no-op inside perform().
            return contains(px, py) && px >= actionX;
        }
    }

    /** A slider row plus its track rect, in the same geometry the renderer uses. */
    public record SliderHit(Row row, int index, UiLayout.Rect rowRect, UiLayout.Rect track) {
        public boolean contains(float px, float py) {
            return px >= rowRect.x() && px <= rowRect.right()
                    && py >= rowRect.y() && py <= rowRect.bottom();
        }

        /** Whether the press landed on (or near) the knob, i.e. a drag not a nudge. */
        public boolean onKnob(float px, float py, float normalized) {
            float knobX = track.x() + track.w() * normalized;
            float r = UiTokens.SLIDER_KNOB / 2.0F + UiTokens.s(12);
            return px >= knobX - r && px <= knobX + r
                    && py >= track.y() - r && py <= track.bottom() + r;
        }
    }

    private static final int LINK_COLOR = Color.makeARGB(255, 96, 165, 250);
    private static final String LABEL_BLOCKED = "atomchat.settings.privacy.list";
    private static final String LABEL_THIRD_PARTY = "atomchat.settings.about.thirdparty.group";
    private static final String LABEL_MOD_INFO = "atomchat.settings.about.modinfo";
    private static final String LABEL_ADVANCED = "atomchat.settings.group.advanced";
    private static final String LABEL_ADJUST = "atomchat.settings.group.adjust";
    private static final String LABEL_BUBBLE_COLORS = "atomchat.settings.group.bubblecolors";
    private static final String LABEL_UI_COLORS = "atomchat.settings.group.uicolors";
    private static final String ACTION_WALLPAPER_PICK = "wallpaper_pick";
    private static final String ACTION_WALLPAPER_CLEAR = "wallpaper_clear";
    private static final String ACTION_TELEPORT_MODE = "teleport_mode";
    private static final String ACTION_THEME = "theme_cycle";

    /** Per-section heading for the leading switch/action group. */
    private static String groupKey(SettingsSection section) {
        return switch (section) {
            case APPEARANCE, PRIVACY -> "atomchat.settings.group.display";
            case CHAT -> "atomchat.settings.group.behaviour";
            case ABOUT -> LABEL_MOD_INFO;
        };
    }

    /** "Custom wallpaper" card: picking an image copies it into the config dir. */
    private SettingsItem wallpaperPickItem() {
        return new SettingsItem("wallpaper_pick",
                "atomchat.settings.appearance.wallpaper",
                "atomchat.settings.appearance.wallpaper.desc",
                () -> true, v -> {
        });
    }

    /** "Clear wallpaper" card: only offered while a wallpaper is actually set. */
    private SettingsItem wallpaperClearItem() {
        return new SettingsItem("wallpaper_clear",
                "atomchat.settings.appearance.wallpaper.clear",
                "atomchat.settings.appearance.wallpaper.clear.desc",
                () -> true, v -> {
        });
    }

    /** Teleport command card: subtitle shows the current auto/tp/tpa mode. */
    private SettingsItem teleportModeItem() {
        return new SettingsItem("teleport_mode",
                "atomchat.settings.chat.teleport",
                "atomchat.settings.chat.teleport.desc",
                () -> true, v -> {
        });
    }

    /** Two-step confirm for destructive actions: first tap arms a red
     *  "确定清除？", second tap within the window really fires. Guards against
     *  accidental wipes; any click that is not the armed button disarms. */
    private static final long CONFIRM_ARM_MS = 3000L;
    private String armedActionId;
    private long armedActionAt;
    /** Collapsed colour groups; session-only, every session starts expanded. */
    private final java.util.Set<String> collapsedColorGroups = new java.util.HashSet<>();

    /** Whether the given destructive action is showing its red confirm state. */
    public boolean actionArmed(String actionId) {
        return actionId != null && actionId.equals(armedActionId)
                && System.currentTimeMillis() - armedActionAt <= CONFIRM_ARM_MS;
    }

    /** Cancels any armed confirmation (click-outside rule). */
    public void disarmAction() {
        armedActionId = null;
    }

    /** Destructive actions that must pass through the two-step confirm. */
    private static boolean needsConfirm(String actionId) {
        return ACTION_WALLPAPER_CLEAR.equals(actionId);
    }

    /** The colour group folded/unfolded by a label row, or null for plain labels. */
    private static String foldableColorGroup(String labelKey) {
        if (LABEL_BUBBLE_COLORS.equals(labelKey)) {
            return "bubble";
        }
        if (LABEL_UI_COLORS.equals(labelKey)) {
            return "ui";
        }
        return null;
    }

    /**
     * Theme card, parked: the preset system works but the look it should
     * encapsulate is still settling (card tint slider landed first), so the
     * card renders veiled with a "coming soon" subtitle and ignores clicks.
     */
    private SettingsItem themeItem() {
        return new SettingsItem("theme_cycle",
                "atomchat.settings.appearance.theme",
                "atomchat.settings.appearance.theme.desc",
                () -> false, v -> {
        }, () -> false);
    }

    private final Map<String, ToggleSwitch> switches = new HashMap<>();
    private final Map<Integer, Float> rowHover = new HashMap<>();
    private String draggingSliderId;
    private int activeSliderIndex = -1;
    private float dragValue;
    /** Section of the frame currently being rendered; drag release needs it. */
    private SettingsSection currentSectionForSettle;
    /** Release glide: from the continuous drag position to the snapped value. */
    private String settleId;
    private float settleFrom;
    private float settleTo;
    private long settleStart;
    private int hoveredIndex = -1;
    private long lastFrameMs = System.currentTimeMillis();

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Title/value/verb/icon colour — follows the interface text colour setting. */
    private static int textPrimary() {
        return AtomChatConfig.get().textPrimaryColor;
    }

    /** Subtitle/divider/muted colour at a given alpha over the secondary text colour. */
    private static int sec(int alpha) {
        int c = AtomChatConfig.get().textSecondaryColor;
        return Color.makeARGB(alpha, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    public static float rowHeight(RowKind kind) {
        return switch (kind) {
            case LABEL -> UiTokens.SETTINGS_LABEL_H;
            case SLIDER, COLOR -> UiTokens.SETTINGS_SLIDER_ROW_H;
            case HERO -> UiTokens.SETTINGS_HERO_H;
            default -> UiTokens.SETTINGS_ROW_H;
        };
    }

    /** Rows in display order for the given section. */
    public List<Row> rows(SettingsSection section) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.ofLabel(groupKey(section)));
        for (SettingsItem item : SettingsCatalog.items(section)) {
            rows.add(Row.ofSwitch(item));
        }
        if (section == SettingsSection.APPEARANCE) {
            // The theme card leads the look group: it writes whole appearance
            // values at once, and every knob below it stays editable after.
            rows.add(Row.ofAction(ACTION_THEME, themeItem()));
            // The wallpaper cards live with the background controls they
            // compete with, not in a group of their own.
            rows.add(Row.ofAction(ACTION_WALLPAPER_PICK, wallpaperPickItem()));
            if (WallpaperStore.isSet()) {
                rows.add(Row.ofAction(ACTION_WALLPAPER_CLEAR, wallpaperClearItem()));
            }
            // Corner-style card deliberately deferred (author call, 2026-09-06):
            // cornerStyle stays config-only until the knob ships.
        }
        if (section == SettingsSection.CHAT) {
            rows.add(Row.ofAction(ACTION_TELEPORT_MODE, teleportModeItem()));
        }
        if (section == SettingsSection.APPEARANCE) {
            rows.add(Row.ofLabel(LABEL_ADJUST));
        }
        for (SettingsSlider slider : SettingsCatalog.sliders(section)) {
            rows.add(Row.ofSlider(slider));
        }
        if (section == SettingsSection.APPEARANCE) {
            // Colours split into two labelled groups: message bubbles vs the
            // surrounding interface. Same rows, easier to scan.
            rows.add(Row.ofLabel(LABEL_BUBBLE_COLORS));
            if (!collapsedColorGroups.contains("bubble")) {
                for (SettingsColor color : SettingsCatalog.colors(section)) {
                    if ("bubble".equals(color.group())) {
                        rows.add(Row.ofColor(color));
                    }
                }
            }
            rows.add(Row.ofLabel(LABEL_UI_COLORS));
            if (!collapsedColorGroups.contains("ui")) {
                for (SettingsColor color : SettingsCatalog.colors(section)) {
                    if ("ui".equals(color.group())) {
                        rows.add(Row.ofColor(color));
                    }
                }
            }
        }
        if (section == SettingsSection.PRIVACY) {
            rows.add(Row.ofLabel(LABEL_BLOCKED));
            for (PlayerRef player : blockedPlayers()) {
                rows.add(Row.ofBlocked(player));
            }
        }
        if (section == SettingsSection.ABOUT) {
            List<Row> about = new ArrayList<>();
            about.add(Row.ofHero());
            about.add(Row.ofLabel(LABEL_MOD_INFO));
            for (SettingsCatalog.InfoRow info : SettingsCatalog.aboutCoreRows()) {
                about.add(Row.ofInfo(info));
            }
            about.add(Row.ofLabel(LABEL_THIRD_PARTY));
            for (SettingsCatalog.InfoRow info : SettingsCatalog.thirdPartyRows()) {
                about.add(Row.ofInfo(info));
            }
            about.add(Row.ofLabel(LABEL_ADVANCED));
            for (SettingsItem item : SettingsCatalog.items(section)) {
                about.add(Row.ofSwitch(item));
            }
            return about;
        }
        return rows;
    }

    public static List<PlayerRef> blockedPlayers() {
        List<String> names = AtomChatConfig.get().blockedPlayers;
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<PlayerRef> out = new ArrayList<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                out.add(PlayerRef.of(null, name));
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- layout

    public float measureContent(UiLayout layout, SettingsSection section) {
        List<Row> rows = rows(section);
        if (rows.isEmpty()) {
            return UiTokens.ROOT_CONTENT_GAP;
        }
        float total = UiTokens.ROOT_CONTENT_GAP;
        for (Row row : rows) {
            total += rowHeight(row.kind());
        }
        total += (rows.size() - 1) * UiTokens.SETTINGS_ROW_GAP;
        return total;
    }

    private static UiLayout.Rect rowRect(List<Row> rows, int index, float scrollY, UiLayout layout) {
        float y = layout.list.y() + UiTokens.ROOT_CONTENT_GAP - scrollY;
        for (int i = 0; i < index; i++) {
            y += rowHeight(rows.get(i).kind()) + UiTokens.SETTINGS_ROW_GAP;
        }
        return new UiLayout.Rect(layout.list.x(), y, layout.list.w(), rowHeight(rows.get(index).kind()));
    }

    private static UiLayout.Rect sliderTrackRect(UiLayout.Rect row) {
        return new UiLayout.Rect(row.x() + UiTokens.SETTINGS_ROW_PAD,
                row.y() + UiTokens.SETTINGS_SLIDER_TRACK_Y,
                row.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F,
                UiTokens.SLIDER_TRACK_H);
    }

    /** Left edge of the clickable action for a row rect. */
    private static float actionX(Row row, UiLayout.Rect rect, Font buttonFont) {
        if (row.kind() == RowKind.BLOCKED) {
            float textW = SkiaFontRenderer.getStringWidth(buttonFont, tr("atomchat.settings.privacy.unblock"));
            return rect.right() - UiTokens.SETTINGS_ROW_PAD - (textW + s(18));
        }
        return rect.x();
    }

    // ----------------------------------------------------------------- render

    public void render(Canvas canvas, UiLayout layout, SettingsSection section,
                       float vmx, float vmy, float scrollY, int accent) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        currentSectionForSettle = section;

        List<Row> rows = rows(section);
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        int hovered = -1;

        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
                if (rect.bottom() < layout.list.y() || rect.y() > layout.list.bottom()) {
                    continue;
                }
                boolean over = row.kind() != RowKind.LABEL
                        && vmx >= rect.x() && vmx <= rect.right()
                        && vmy >= rect.y() && vmy <= rect.bottom();
                if (over) {
                    hovered = i;
                }
                // Draw from the animated value only. Never force it to 1 while
                // hovered — that is what made the highlight snap in instead of
                // fading in over the same 90ms the toolbar buttons use.
                drawRow(canvas, row, rect, rowHover.getOrDefault(i, 0.0F),
                        buttonFont, accent, dt);
            }
        } finally {
            canvas.restore();
        }

        hoveredIndex = hovered;
        if (hovered >= 0) {
            rowHover.putIfAbsent(hovered, 0.0F);
        }
        for (Integer key : new ArrayList<>(rowHover.keySet())) {
            float target = key == hovered ? 1.0F : 0.0F;
            rowHover.put(key, UiMotion.approach(rowHover.get(key), target, dt, UiMotion.HOVER_MS));
        }

        if (section == SettingsSection.PRIVACY && blockedPlayers().isEmpty()) {
            drawEmptyBlocked(canvas, layout, rows, scrollY);
        }
        advanceSettle(now);
    }

    private void drawEmptyBlocked(Canvas canvas, UiLayout layout, List<Row> rows, float scrollY) {
        UiLayout.Rect last = rowRect(rows, rows.size() - 1, scrollY, layout);
        float top = last.bottom() + s(10);
        float height = Math.min(s(160), Math.max(s(90), layout.list.bottom() - top));
        if (height <= 0.0F) {
            return;
        }
        drawEmptyState(canvas, new UiLayout.Rect(layout.list.x(), top, layout.list.w(), height),
                "atomchat.settings.privacy.empty");
    }

    private void drawRow(Canvas canvas, Row row, UiLayout.Rect rect, float hover,
                         Font buttonFont, int accent, float dtMs) {
        if (row.kind() == RowKind.LABEL) {
            drawLabel(canvas, row, rect);
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), UiTokens.cardFill());
        SkiaDraw.drawEdgeHighlight(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
        if (hover > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), UiTokens.cardHover(hover));
        }
        switch (row.kind()) {
            case HERO -> drawHero(canvas, rect);
            case SWITCH -> drawSwitch(canvas, row, rect, accent, dtMs);
            case SLIDER -> drawSlider(canvas, row, rect, accent);
            case COLOR -> drawColor(canvas, row, rect);
            case INFO -> drawInfo(canvas, row, rect);
            case BLOCKED -> drawBlocked(canvas, row, rect, hover, buttonFont);
            case ACTION -> drawAction(canvas, row, rect, hover);
            default -> {
            }
        }
    }

    /** Swatch strip geometry: centre Y within the row and the X step. */
    private static float swatchCy(UiLayout.Rect rect) {
        return rect.y() + s(42);
    }

    private static float swatchX(UiLayout.Rect rect, int index) {
        return rect.x() + UiTokens.SETTINGS_ROW_PAD + UiTokens.s(9) + index * UiTokens.s(26);
    }

    private void drawColor(Canvas canvas, Row row, UiLayout.Rect rect) {
        SettingsColor color = row.color();
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(color.titleKey()), rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F),
                rect.x() + UiTokens.SETTINGS_ROW_PAD,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        String hex = String.format("#%06X", color.value() & 0xFFFFFF);
        float hexW = SkiaFontRenderer.getStringWidth(valueFont, hex);
        SkiaFontRenderer.drawTextRight(canvas, valueFont, hex,
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                textPrimary());
        // Live element preview: the current value as one small square, same
        // shape for every colour row.
        float prevW = s(11);
        float prevR = s(3);
        float prevRight = rect.right() - UiTokens.SETTINGS_ROW_PAD - hexW - s(8);
        SkiaDraw.drawRoundedRect(canvas, prevRight - prevW, rect.y() + s(18) - prevW / 2.0F,
                prevW, prevW, prevR, color.value());

        float r = UiTokens.s(9);
        float cy = swatchCy(rect);
        for (int i = 0; i < color.swatchCount(); i++) {
            float scx = swatchX(rect, i);
            int swatch = color.swatchColor(i);
            SkiaDraw.drawRoundedRect(canvas, scx - r, cy - r, 2.0F * r, 2.0F * r, r, swatch);
            if (swatch == color.value()) {
                // Selection ring: white outline with a breathing gap.
                try (Paint ring = new Paint().setColor(Color.makeARGB(255, 255, 255, 255))
                        .setMode(PaintMode.STROKE).setStrokeWidth(s(2)).setAntiAlias(true)) {
                    canvas.drawOval(io.github.humbleui.types.Rect.makeXYWH(
                            scx - r - s(3), cy - r - s(3), 2.0F * (r + s(3)), 2.0F * (r + s(3))), ring);
                }
            }
        }
        // "+" cell: opens the custom colour picker (emote-grid plus language).
        float px = swatchX(rect, color.swatchCount());
        SkiaDraw.drawRoundedRect(canvas, px - r, cy - r, 2.0F * r, 2.0F * r, r,
                Color.makeARGB(70, 255, 255, 255));
        drawIconCentered(canvas, AppIcons.ICON_PLUS_PATH, px, cy, s(12),
                textPrimary());
    }

    private static void drawIconCentered(Canvas canvas, io.github.humbleui.skija.Path icon,
                                         float cx, float cy, float size, int color) {
        io.github.humbleui.types.Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float sc = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * sc,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * sc);
            canvas.scale(sc, sc);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE).setStrokeWidth(UiTokens.iconStroke(size) / sc)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }

    /**
     * About-page hero: the logo on a white plate (the source PNG has no alpha,
     * so it needs a light ground) beside the wordmark. The plate/image split is
     * deliberate — a future art-text logo only has to replace
     * {@link #heroImage()}, nothing else in the card moves.
     */
    private void drawHero(Canvas canvas, UiLayout.Rect rect) {
        SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), UiTokens.cardFill());
        SkiaDraw.drawEdgeHighlight(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                UiTokens.settingsRowRadius(), s(1.2F), UiTokens.CARD_EDGE);
        Image hero = heroImage();
        float plate = UiTokens.SETTINGS_HERO_PLATE;
        float plateX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float plateY = rect.y() + (rect.h() - plate) / 2.0F;
        float plateR = s(12);
        SkiaDraw.drawRoundedRect(canvas, plateX, plateY, plate, plate, plateR, Color.makeARGB(255, 250, 250, 250));
        if (hero != null) {
            float inset = s(7);
            SkiaDraw.drawRoundedImage(canvas, hero, plateX + inset, plateY + inset,
                    plate - inset * 2.0F, plate - inset * 2.0F, plateR - inset, SamplingMode.LINEAR);
        }
        Font heroFont = FontManager.font(UiTokens.SETTINGS_HERO_FONT);
        SkiaFontRenderer.drawText(canvas, heroFont, tr("atomchat.screen.title"),
                plateX + plate + s(14),
                SkiaFontRenderer.centerBaselineY(heroFont, rect.y() + rect.h() / 2.0F),
                textPrimary());
    }

    private static Image heroImage;

    /** The bundled {@code logo.png}, decoded once and cached for the session. */
    private static Image heroImage() {
        if (heroImage != null) {
            return heroImage;
        }
        try (var stream = SettingsSectionPage.class.getResourceAsStream("/assets/atomchat/logo.png")) {
            if (stream != null) {
                heroImage = Image.makeFromEncoded(stream.readAllBytes());
            }
        } catch (Exception ignored) {
            // No logo: the plate draws empty and the card still reads fine.
        }
        return heroImage;
    }

    /** Plain action card: title + description + a right-aligned verb. */
    private void drawAction(Canvas canvas, Row row, UiLayout.Rect rect, float hover) {
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F;

        boolean available = row.item().available();
        boolean redConfirm = needsConfirm(row.actionId()) && actionArmed(row.actionId());
        String subtitle;
        String verb;
        if (!available) {
            subtitle = tr(row.item().subtitleKey() + ".unavailable");
            verb = "";
        } else if (redConfirm) {
            // Armed state: the right-hand verb itself becomes the red confirm
            // question; the subtitle keeps its normal content.
            subtitle = tr(row.item().subtitleKey());
            verb = tr("atomchat.settings.appearance.wallpaper.clear.confirm");
        } else if (ACTION_WALLPAPER_PICK.equals(row.actionId())) {
            Path wallpaper = WallpaperStore.current();
            subtitle = wallpaper != null && wallpaper.getFileName() != null
                    ? wallpaper.getFileName().toString()
                    : tr(row.item().subtitleKey());
            verb = tr("atomchat.settings.action.choose");
        } else if (ACTION_TELEPORT_MODE.equals(row.actionId())) {
            String mode = AtomChatConfig.get().teleportCommandMode;
            subtitle = tr("atomchat.settings.chat.teleport." + (mode == null ? "auto" : mode));
            verb = tr("atomchat.settings.action.cycle");
        } else if (ACTION_THEME.equals(row.actionId())) {
            String theme = AtomChatConfig.get().themeName;
            subtitle = tr("atomchat.settings.theme."
                    + (theme == null || theme.isBlank() ? "none" : theme));
            verb = tr("atomchat.settings.action.cycle");
        } else {
            subtitle = tr(row.item().subtitleKey());
            verb = tr("atomchat.settings.action.clear");
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(row.item().titleKey()), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        Font verbFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        SkiaFontRenderer.drawText(canvas, subFont,
                SkiaFontRenderer.truncate(subFont, subtitle,
                        maxW - SkiaFontRenderer.getStringWidth(verbFont, verb) - s(12)), textX,
                SkiaFontRenderer.centerBaselineY(subFont, rect.y() + s(37)),
                redConfirm ? Color.makeARGB(255, 235, 64, 52) : sec(available ? 200 : 130));
        // Same treatment as the link cards' "Open": full-weight, centred —
        // the card's call to action, not a footnote.
        if (available) {
            SkiaFontRenderer.drawTextRight(canvas, verbFont, verb,
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F,
                    redConfirm ? Color.makeARGB(255, 235, 64, 52) : textPrimary());
        }
        // Unavailable veil, same language as the wallpaper-gated blur switch.
        if (!available) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), Color.makeARGB(90, 10, 12, 16));
        }
    }

    /**
     * Group heading: title-weight text centred between two rules of the same
     * colour, so it reads as a section divider rather than a stray caption.
     */
    private void drawLabel(Canvas canvas, Row row, UiLayout.Rect rect) {
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        int lineColor = sec(190);
        String group = foldableColorGroup(row.labelKey());
        if (group == null) {
            String text = tr(row.labelKey());
            float textW = SkiaFontRenderer.getStringWidth(font, text);
            float cy = rect.y() + rect.h() / 2.0F;
            float gap = s(12);
            float inset = s(4);
            float leftEnd = rect.x() + inset + (rect.w() - inset * 2.0F - textW - gap * 2.0F) / 2.0F;
            float rightStart = leftEnd + textW + gap * 2.0F;
            float lineH = s(1.5F);
            if (leftEnd > rect.x() + inset) {
                SkiaDraw.drawRoundedRect(canvas, rect.x() + inset, cy - lineH / 2.0F,
                        leftEnd - (rect.x() + inset), lineH, lineH / 2.0F, lineColor);
            }
            if (rightStart < rect.right() - inset) {
                SkiaDraw.drawRoundedRect(canvas, rightStart, cy - lineH / 2.0F,
                        rect.right() - inset - rightStart, lineH, lineH / 2.0F, lineColor);
            }
            SkiaFontRenderer.drawTextCentered(canvas, font, text, rect.x() + rect.w() / 2.0F, cy, lineColor);
            return;
        }
        // Foldable colour group: chevron + centred caption between the same
        // divider rules as plain group headings.
        boolean collapsed = collapsedColorGroups.contains(group);
        String text = tr(row.labelKey());
        float textW = SkiaFontRenderer.getStringWidth(font, text);
        float chevron = s(7);
        float gap = s(10);
        float cy = rect.y() + rect.h() / 2.0F;
        float contentW = chevron + gap + textW;
        float inset = s(4);
        float leftEnd = rect.x() + inset + (rect.w() - inset * 2.0F - contentW - gap * 2.0F) / 2.0F;
        float rightStart = leftEnd + contentW + gap * 2.0F;
        float lineH = s(1.5F);
        if (leftEnd > rect.x() + inset) {
            SkiaDraw.drawRoundedRect(canvas, rect.x() + inset, cy - lineH / 2.0F,
                    leftEnd - (rect.x() + inset), lineH, lineH / 2.0F, lineColor);
        }
        if (rightStart < rect.right() - inset) {
            SkiaDraw.drawRoundedRect(canvas, rightStart, cy - lineH / 2.0F,
                    rect.right() - inset - rightStart, lineH, lineH / 2.0F, lineColor);
        }
        float startX = leftEnd + gap;
        drawChevron(canvas, startX, cy, chevron, collapsed, lineColor);
        SkiaFontRenderer.drawTextCentered(canvas, font, text,
                startX + chevron + gap + textW / 2.0F, cy, lineColor);
    }

    /** Small fold indicator: right-pointing when collapsed, down when open.
     *  No explicit contour close: fill mode auto-closes, and Path.close() is
     *  the resource-release method (contour close is closePath()) — calling it
     *  inside the block frees the native path before drawPath runs (crashed). */
    private static void drawChevron(Canvas canvas, float cx, float cy, float size,
                                    boolean collapsed, int color) {
        try (io.github.humbleui.skija.Path path = new io.github.humbleui.skija.Path();
             Paint paint = new Paint()) {
            if (collapsed) {
                path.moveTo(cx - size / 2.0F, cy - size);
                path.lineTo(cx - size / 2.0F, cy + size);
                path.lineTo(cx + size / 2.0F, cy);
            } else {
                path.moveTo(cx - size, cy - size / 2.0F);
                path.lineTo(cx + size, cy - size / 2.0F);
                path.lineTo(cx, cy + size / 2.0F);
            }
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }

    /** Centred "nothing here" glyph + caption, shared by every empty list. */
    public static void drawEmptyState(Canvas canvas, UiLayout.Rect area, String textKey) {
        Font font = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float icon = UiTokens.SETTINGS_EMPTY_ICON;
        float textH = SkiaFontRenderer.textHeight(font);
        float gap = s(12);
        float groupH = icon + gap + textH;
        float top = area.y() + Math.max(0.0F, (area.h() - groupH) / 2.0F);
        float cx = area.x() + area.w() / 2.0F;
        // Empty-state icon and caption read as secondary information: they
        // follow the secondary text colour, dimmed to keep the muted look.
        int muted = sec(150);
        drawIconCentered(canvas, AppIcons.ICON_NO_PLAYERS_PATH, cx, top + icon / 2.0F, icon, muted);
        SkiaFontRenderer.drawTextCentered(canvas, font, Text.translatable(textKey).getString(),
                cx, top + icon + gap + textH / 2.0F, muted);
    }

    private void drawSwitch(Canvas canvas, Row row, UiLayout.Rect rect, int accent, float dtMs) {
        SettingsItem item = row.item();
        ToggleSwitch control = switches.computeIfAbsent(item.id(), k -> new ToggleSwitch());
        control.update(dtMs, item.available() && item.value());

        float switchX = rect.right() - UiTokens.SETTINGS_ROW_PAD - UiTokens.SWITCH_W;
        float switchY = rect.y() + (rect.h() - UiTokens.SWITCH_H) / 2.0F;
        control.render(canvas, switchX, switchY, accent);

        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font subFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = Math.max(0.0F, switchX - textX - s(10));
        // An overridden option explains itself instead of silently doing nothing.
        String subtitleKey = item.available()
                ? item.subtitleKey() : item.subtitleKey() + ".unavailable";
        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(item.titleKey()), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());
        SkiaFontRenderer.drawText(canvas, subFont,
                SkiaFontRenderer.truncate(subFont, tr(subtitleKey), maxW), textX,
                SkiaFontRenderer.centerBaselineY(subFont, rect.y() + s(37)),
                sec(item.available() ? 200 : 130));
        if (!item.available()) {
            SkiaDraw.drawRoundedRect(canvas, rect.x(), rect.y(), rect.w(), rect.h(),
                    UiTokens.settingsRowRadius(), Color.makeARGB(90, 10, 12, 16));
        }
    }

    private void drawSlider(Canvas canvas, Row row, UiLayout.Rect rect, int accent) {
        SettingsSlider slider = row.slider();
        boolean dragging = slider.id().equals(draggingSliderId);
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float titleMaxW = Math.max(0.0F, rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F
                - SkiaFontRenderer.getStringWidth(valueFont, slider.displayValue()) - s(12));

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(slider.titleKey()), titleMaxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(18)),
                textPrimary());
        SkiaFontRenderer.drawTextRight(canvas, valueFont, slider.displayValue(),
                rect.right() - UiTokens.SETTINGS_ROW_PAD, rect.y() + s(18),
                dragging ? accent : textPrimary());

        UiLayout.Rect track = sliderTrackRect(rect);
        float t = knobPosition(slider, dragging);
        float radius = UiTokens.SLIDER_TRACK_H / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, track.x(), track.y(), track.w(), track.h(), radius,
                Color.makeARGB(70, 255, 255, 255));
        float fillW = Math.max(track.h(), track.w() * t);
        SkiaDraw.drawRoundedRect(canvas, track.x(), track.y(), fillW, track.h(), radius, accent);

        float knobX = track.x() + track.w() * t - UiTokens.SLIDER_KNOB / 2.0F;
        float knobY = track.y() + track.h() / 2.0F - UiTokens.SLIDER_KNOB / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SLIDER_KNOB, UiTokens.SLIDER_KNOB,
                UiTokens.SLIDER_KNOB / 2.0F, Color.makeARGB(255, 255, 255, 255)); // knob: mechanical white
    }

    private void drawInfo(Canvas canvas, Row row, UiLayout.Rect rect) {
        Font titleFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        Font valueFont = FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        float textX = rect.x() + UiTokens.SETTINGS_ROW_PAD;
        float maxW = rect.w() - UiTokens.SETTINGS_ROW_PAD * 2.0F;
        boolean link = row.info().isLink();
        // Reserve room for the right-aligned "opens a browser" hint so a long
        // value can never run under it.
        float valueMaxW = maxW;
        if (link) {
            valueMaxW -= SkiaFontRenderer.getStringWidth(titleFont, tr("atomchat.settings.about.open")) + s(12);
        }

        SkiaFontRenderer.drawText(canvas, titleFont,
                SkiaFontRenderer.truncate(titleFont, tr(row.info().titleKey()), maxW), textX,
                SkiaFontRenderer.centerBaselineY(titleFont, rect.y() + s(20)),
                textPrimary());

        String value = SkiaFontRenderer.truncate(valueFont, row.info().value(), valueMaxW);
        // Link affordance = colour plus underline, so it stays distinct from
        // the grey non-link values on the same page. The right-aligned hint
        // tells the user the whole card is clickable before they try it.
        SkiaFontRenderer.drawText(canvas, valueFont, value, textX,
                SkiaFontRenderer.centerBaselineY(valueFont, rect.y() + s(37)),
                link ? LINK_COLOR : sec(200));
        if (link) {
            float underlineY = rect.y() + s(37) + SkiaFontRenderer.textHeight(valueFont) / 2.0F + s(2);
            SkiaDraw.drawRoundedRect(canvas, textX, underlineY,
                    SkiaFontRenderer.getStringWidth(valueFont, value), s(1.5F), s(0.75F), LINK_COLOR);
            // Full-weight and vertically centred: it is the card's call to
            // action, not a footnote.
            String hint = tr("atomchat.settings.about.open");
            Font hintFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawTextRight(canvas, hintFont, hint,
                    rect.right() - UiTokens.SETTINGS_ROW_PAD,
                    rect.y() + rect.h() / 2.0F, textPrimary());
        }
    }

    private void drawBlocked(Canvas canvas, Row row, UiLayout.Rect rect, float hover, Font buttonFont) {
        float avatar = UiTokens.SETTINGS_ROW_AVATAR;
        float avatarY = rect.y() + (rect.h() - avatar) / 2.0F;
        Image face = row.player() != null
                ? PlayerAvatar.face(row.player().uuid(), row.player().realName()) : null;
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                    avatar, avatar, avatar / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, rect.x() + UiTokens.SETTINGS_ROW_PAD, avatarY,
                    avatar, avatar, avatar / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }

        String name = row.player() != null ? row.player().realName() : "";
        Font nameFont = FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
        float nameX = rect.x() + UiTokens.SETTINGS_ROW_PAD + avatar + s(10);
        String label = tr("atomchat.settings.privacy.unblock");
        float buttonW = SkiaFontRenderer.getStringWidth(buttonFont, label) + s(18);
        float buttonX = rect.right() - UiTokens.SETTINGS_ROW_PAD - buttonW;
        float maxNameW = Math.max(0.0F, buttonX - nameX - s(10));
        SkiaFontRenderer.drawText(canvas, nameFont,
                SkiaFontRenderer.truncate(nameFont, name, maxNameW), nameX,
                SkiaFontRenderer.centerBaselineY(nameFont, rect.y() + rect.h() / 2.0F),
                textPrimary());

        float buttonH = s(28);
        float buttonY = rect.y() + (rect.h() - buttonH) / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, buttonX, buttonY, buttonW, buttonH, s(8),
                Color.makeARGB((int) (70.0F + 45.0F * hover), 255, 255, 255));
        SkiaFontRenderer.drawTextCentered(canvas, buttonFont, label,
                buttonX + buttonW / 2.0F, buttonY + buttonH / 2.0F,
                textPrimary());
    }

    // ------------------------------------------------------------------ input

    public RowHit hit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        Font buttonFont = FontManager.font(UiTokens.FONT_QUOTE);
        for (int i = 0; i < rows.size(); i++) {
            UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
            RowHit hit = new RowHit(rows.get(i), i, rect.x(), rect.y(), rect.w(), rect.h(),
                    actionX(rows.get(i), rect, buttonFont));
            if (hit.contains(vmx, vmy)) {
                return hit;
            }
        }
        return null;
    }

    /** Slider row under the pointer, or null. Geometry matches the renderer. */
    public SliderHit sliderHit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind() != RowKind.SLIDER) {
                continue;
            }
            UiLayout.Rect rect = rowRect(rows, i, scrollY, layout);
            SliderHit hit = new SliderHit(row, i, rect, sliderTrackRect(rect));
            if (hit.contains(vmx, vmy)) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Starts dragging the slider in {@code hit}. The knob jumps to the pointer
     * immediately, so picking the handle up never feels like it slipped. Only
     * the row index is kept: the panel can resize under the pointer (the width
     * slider does exactly that), so the geometry must be recomputed every frame.
     */
    public void beginSliderDrag(int rowIndex, SettingsSlider slider, UiLayout.Rect rowRect, float vmx) {
        activeSliderIndex = rowIndex;
        draggingSliderId = slider.id();
        settleId = null;
        UiLayout.Rect track = sliderTrackRect(rowRect);
        float t = track.w() <= 0.0F ? 0.0F : (vmx - track.x()) / track.w();
        dragValue = slider.denormalizeContinuous(t);
        slider.apply(dragValue);
    }

    /**
     * Retargets the active drag. The knob follows the pointer continuously;
     * only the snapped value is applied to the config, so the label always
     * names a value the mod is actually using.
     */
    public void dragSlider(UiLayout layout, SettingsSection section, float scrollY, float vmx) {
        if (activeSliderIndex < 0 || draggingSliderId == null) {
            return;
        }
        List<Row> rows = rows(section);
        if (activeSliderIndex >= rows.size()) {
            return;
        }
        Row row = rows.get(activeSliderIndex);
        if (row.kind() != RowKind.SLIDER) {
            return;
        }
        UiLayout.Rect track = sliderTrackRect(rowRect(rows, activeSliderIndex, scrollY, layout));
        float t = track.w() <= 0.0F ? 0.0F : (vmx - track.x()) / track.w();
        dragValue = row.slider().denormalizeContinuous(t);
        row.slider().apply(dragValue);
    }

    /**
     * Releases the drag: persists once, then lets the knob glide from wherever
     * the finger left it to the snapped value instead of teleporting there.
     */
    public void endSliderDrag() {
        if (draggingSliderId != null && activeSliderIndex >= 0) {
            List<Row> rows = rows(currentSectionForSettle);
            if (activeSliderIndex < rows.size() && rows.get(activeSliderIndex).kind() == RowKind.SLIDER) {
                SettingsSlider slider = rows.get(activeSliderIndex).slider();
                slider.persist();
                settleId = slider.id();
                settleFrom = slider.normalize(dragValue);
                settleTo = slider.normalize(slider.value());
                settleStart = System.currentTimeMillis();
            }
        }
        activeSliderIndex = -1;
        draggingSliderId = null;
    }

    public boolean isDraggingSlider() {
        return draggingSliderId != null;
    }

    /**
     * Knob position for rendering: the pointer while dragging, the snapped
     * value otherwise, with a short glide in between so releasing never reads
     * as a jump. A drag itself is deliberately 1:1 — easing it would only make
     * the knob lag behind the finger.
     */
    private float knobPosition(SettingsSlider slider, boolean dragging) {
        if (dragging) {
            return slider.normalize(dragValue);
        }
        if (slider.id().equals(settleId)) {
            return settleFrom + (settleTo - settleFrom)
                    * Easing.easeOutCubic(Math.min(1.0F, (System.currentTimeMillis() - settleStart) / 120.0F));
        }
        return slider.normalize(slider.value());
    }

    private void advanceSettle(long now) {
        if (settleId != null && now - settleStart >= 120L) {
            settleId = null;
        }
    }

    /** One {@link SettingsSlider#step()} in {@code direction} (-1 / +1). */
    public void nudgeSlider(SliderHit hit, int direction) {
        hit.row().slider().nudge(direction);
    }

    /** A colour swatch (or the "+" custom cell) under the pointer. */
    public record ColorHit(SettingsColor color, int swatchIndex, int swatch, boolean plus) {
    }

    /**
     * Colour swatch or the trailing "+" cell under the pointer, or null.
     * Geometry mirrors {@link #drawColor}: the row's swatch strip is
     * hit-tested swatch by swatch, not per row, so a click between swatches
     * falls through.
     */
    public ColorHit colorHit(float vmx, float vmy, UiLayout layout, SettingsSection section, float scrollY) {
        List<Row> rows = rows(section);
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            if (row.kind() != RowKind.COLOR) {
                continue;
            }
            SettingsColor color = row.color();
            UiLayout.Rect rect = rowRect(rows, rowIdx, scrollY, layout);
            float r = UiTokens.s(9);
            float cy = swatchCy(rect);
            float dy = vmy - cy;
            if (Math.abs(dy) > r + UiTokens.s(4) || vmy < rect.y() || vmy > rect.bottom()) {
                continue;
            }
            for (int i = 0; i < color.swatchCount(); i++) {
                float dx = vmx - swatchX(rect, i);
                if (Math.abs(dx) <= r + UiTokens.s(4)) {
                    return new ColorHit(color, i, color.swatchColor(i), false);
                }
            }
            // The "+" cell right after the last swatch.
            float dx = vmx - swatchX(rect, color.swatchCount());
            if (Math.abs(dx) <= r + UiTokens.s(4)) {
                return new ColorHit(color, -1, 0, true);
            }
        }
        return null;
    }

    /** Applies a colour swatch; writes through to the config immediately. */
    public void applyColor(ColorHit hit) {
        hit.color().apply(hit.swatch());
    }

    /** Applies a hit: flips a switch, opens a link, fires an action, folds a
     *  colour group, or unblocks. Destructive actions go through the two-step
     *  confirm. */
    public void perform(RowHit hit) {
        if (hit == null) {
            return;
        }
        switch (hit.row().kind()) {
            case SWITCH -> {
                SettingsItem item = hit.row().item();
                if (!item.available()) {
                    return;
                }
                item.set(!item.value());
            }
            case INFO -> {
                if (hit.row().info().isLink()) {
                    openUri(hit.row().info().uri());
                }
            }
            case LABEL -> {
                String group = foldableColorGroup(hit.row().labelKey());
                if (group == null) {
                    return;
                }
                if (!collapsedColorGroups.remove(group)) {
                    collapsedColorGroups.add(group);
                }
            }
            case ACTION -> {
                if (!hit.row().item().available() || actionHandler == null) {
                    return;
                }
                String actionId = hit.row().actionId();
                if (needsConfirm(actionId)) {
                    // Two-step confirm: first tap arms, second tap fires.
                    long now = System.currentTimeMillis();
                    if (!actionArmed(actionId) || now - armedActionAt > CONFIRM_ARM_MS) {
                        armedActionId = actionId;
                        armedActionAt = now;
                        return;
                    }
                    armedActionId = null;
                }
                actionHandler.onAction(actionId);
            }
            case BLOCKED -> {
                if (hit.row().player() != null) {
                    BlockList.setBlocked(hit.row().player(), false);
                }
            }
            default -> {
            }
        }
    }

    /** Shell callback for action cards; the screen owns the file picker. */
    public interface ActionHandler {
        void onAction(String actionId);
    }

    private ActionHandler actionHandler;

    public void setActionHandler(ActionHandler handler) {
        this.actionHandler = handler;
    }

    private void openUri(String uri) {
        try {
            net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(uri));
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to open link {}", uri, e);
        }
    }

    /** Drops per-page transient state so reopening a section starts clean. */
    public void reset() {
        switches.clear();
        rowHover.clear();
        draggingSliderId = null;
        hoveredIndex = -1;
    }
}
