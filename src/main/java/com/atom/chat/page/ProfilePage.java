package com.atom.chat.page;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.Animations;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Profile root page: a hero identity card (large circular avatar with a
 * persistent edit badge) above a card of copyable info rows — name, UUID,
 * latency and current server (grilled 2026-09-05: identity showcase only, no
 * social actions; rows copy their value).
 *
 * <p>Avatar interactions follow the grilled decision: the edit badge always
 * opens the file picker; tapping the avatar itself opens a local
 * change/clear menu when a custom avatar is set, and the picker otherwise.
 * With no custom avatar the skin is displayed.
 */
public final class ProfilePage {
    /** Actions the shell performs on behalf of this page. */
    public interface Handler {
        void openAvatarPicker();

        void clearAvatar();

        void copyText(String text);
    }

    /**
     * Operator lookup for the role row. Vanilla 1.21.1 only syncs the local
     * player's own permission level (via entity status 24-28 on join/op), so
     * the default resolver answers for self only and returns {@code null}
     * (unknown) for everyone else — the row is hidden rather than guessed.
     * A future server companion plugs in here to resolve every player.
     */
    public interface RoleResolver {
        Boolean isOperator(UUID uuid, String name);
    }

    private final Handler handler;
    private final AvatarStore avatarStore;
    /** Session start (client JOIN); 0 = unknown. */
    private static volatile long sessionStartMs;
    /** Cached stat totals; the statMap sum runs at most once a second. */
    private long statsCacheMs;
    private StatTotals statsCache;
    /**
     * Whose profile is shown; {@code null} means the local player. Reserved
     * for the planned "tap a player avatar to open their profile" navigation —
     * the data layer is already subject-parameterised.
     */
    private PlayerRef subject;
    private RoleResolver roleResolver;

    private long lastFrameMs = System.currentTimeMillis();
    private float avatarHover;
    private float badgeHover;
    private float rowHover;
    /** Row the current {@link #rowHover} alpha belongs to; fades out on exit. */
    private int hoverRowIndex = -1;
    private final float[] menuItemHover = new float[2];
    private final float[] tileHover = new float[3];
    private boolean avatarMenuOpen;
    /** Fade/scale progress of the avatar menu; keeps drawing while fading out. */
    private float menuAnim;

    public ProfilePage(Handler handler, AvatarStore avatarStore) {
        this.handler = handler;
        this.avatarStore = avatarStore;
    }

    /** Subject injection point for the future avatar-click profile navigation. */
    public void setSubject(PlayerRef newSubject) {
        this.subject = newSubject;
    }

    /** Clears the injected subject; the page falls back to the local player. */
    public void resetSubject() {
        this.subject = null;
    }

    public PlayerRef subject() {
        return subject;
    }

    public void setRoleResolver(RoleResolver newResolver) {
        this.roleResolver = newResolver;
    }

    /** Records the session start; called from the client JOIN event. */
    public static void noteJoin() {
        sessionStartMs = System.currentTimeMillis();
    }

    private UUID subjectUuid() {
        if (subject != null) {
            return subject.uuid();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getUuid() : null;
    }

    private String subjectName() {
        if (subject != null) {
            return subject.realName();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getName().getString() : "-";
    }

    private boolean subjectIsSelf() {
        UUID own = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getUuid() : null;
        return subject == null || (subjectUuid() != null && subjectUuid().equals(own));
    }

    private static String sessionValue() {
        long start = sessionStartMs;
        if (start <= 0) {
            return null;
        }
        long secs = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        long h = secs / 3600L;
        long m = (secs % 3600L) / 60L;
        long s = secs % 60L;
        if (h > 0) {
            return tr("atomchat.profile.session.h", h, m);
        }
        if (m > 0) {
            return tr("atomchat.profile.session.m", m, s);
        }
        return tr("atomchat.profile.session.s", s);
    }

    /**
     * Totals for mined blocks and kills need the whole synced stat map, so the
     * combined value is cached for a second; the walk distance is a single
     * custom-stat lookup anyway. Null until the player exists.
     */
    private String statsValue() {
        StatTotals t = statTotals();
        return t == null ? null : tr("atomchat.profile.stats.value", t.mined(), t.killed(), distance(t.walkCm()));
    }

    private record StatTotals(long mined, long killed, long walkCm) {
    }

    private StatTotals statTotals() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (statsCache != null && now - statsCacheMs < 1000L) {
            return statsCache;
        }
        StatHandler stats = client.player.getStatHandler();
        long mined = 0L;
        long killed = 0L;
        for (Object2IntMap.Entry<Stat<?>> entry : stats.statMap.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            if (stat.getType() == Stats.MINED) {
                mined += entry.getIntValue();
            } else if (stat.getType() == Stats.KILLED) {
                killed += entry.getIntValue();
            }
        }
        long walkCm = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM));
        statsCache = new StatTotals(mined, killed, walkCm);
        statsCacheMs = now;
        return statsCache;
    }

    private static String distance(long cm) {
        if (cm >= 100_000L) {
            return String.format(java.util.Locale.ROOT, "%.1f km", cm / 100_000.0);
        }
        return (cm / 100L) + " m";
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    /** One copyable info row. */
    public record InfoRow(String label, String value) {
    }

    /** One dashboard tile under the hero card: big value + small label. */
    public record StatTile(String label, String value, String copy) {
    }

    public float measureContent(UiLayout layout) {
        return UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H
                + UiTokens.SETTINGS_ROW_GAP
                + UiTokens.PROFILE_TILE_H
                + UiTokens.SETTINGS_ROW_GAP
                + infoRows().size() * UiTokens.PROFILE_ROW_H
                + (infoRows().size() - 1) * UiTokens.SETTINGS_ROW_GAP;
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /**
     * Dashboard tiles (0.1.11 redesign): ping / session / stats as one row of
     * three under the hero card, identity rows stay as grouped rows below.
     */
    private List<StatTile> statTiles() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<StatTile> tiles = new ArrayList<>();
        UUID uuid = subjectUuid();
        PlayerListEntry entry = uuid != null && client.player != null && client.player.networkHandler != null
                ? client.player.networkHandler.getPlayerListEntry(uuid)
                : null;
        String ping = tr("atomchat.profile.ping.value", entry != null ? entry.getLatency() : -1);
        tiles.add(new StatTile(tr("atomchat.profile.ping"), ping, ping));
        String session = sessionValue();
        tiles.add(new StatTile(tr("atomchat.profile.session"),
                session != null ? session : "-", session != null ? session : "-"));
        StatTotals totals = statTotals();
        if (totals != null) {
            tiles.add(new StatTile(tr("atomchat.profile.stats"),
                    tr("atomchat.profile.stats.mined", totals.mined()), statsValue()));
        } else {
            tiles.add(new StatTile(tr("atomchat.profile.stats"), "-", "-"));
        }
        return tiles;
    }

    private List<InfoRow> infoRows() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<InfoRow> rows = new ArrayList<>();
        UUID uuid = subjectUuid();
        String name = subjectName();
        rows.add(new InfoRow(tr("atomchat.profile.name"), name));
        rows.add(new InfoRow(tr("atomchat.profile.uuid"), uuid != null ? uuid.toString() : "-"));
        Boolean operator = roleResolver != null ? roleResolver.isOperator(uuid, name) : null;
        if (operator != null) {
            // Unknown (null) hides the row instead of guessing a role.
            rows.add(new InfoRow(tr("atomchat.profile.role"),
                    tr(operator ? "atomchat.profile.role.op" : "atomchat.profile.role.member")));
        }
        String server;
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null
                && !client.getCurrentServerEntry().address.isBlank()) {
            server = client.getCurrentServerEntry().address;
        } else if (client.getServer() != null) {
            server = tr("atomchat.profile.server.singleplayer");
        } else {
            server = "-";
        }
        rows.add(new InfoRow(tr("atomchat.profile.server"), server));
        return rows;
    }

    // ------------------------------------------------------------------
    // Geometry (single source for render / hit / menu)
    // ------------------------------------------------------------------

    private UiLayout.Rect heroRect(UiLayout layout, float scrollY) {
        return new UiLayout.Rect(
                layout.list.x(),
                layout.list.y() + UiTokens.ROOT_CONTENT_GAP - scrollY,
                layout.list.w(),
                UiTokens.PROFILE_AVATAR_HERO_H);
    }

    private UiLayout.Rect avatarRect(UiLayout layout, float scrollY) {
        UiLayout.Rect hero = heroRect(layout, scrollY);
        float size = UiTokens.PROFILE_AVATAR;
        return new UiLayout.Rect(
                hero.x() + (hero.w() - size) / 2.0F,
                hero.y() + s(18),
                size, size);
    }

    private UiLayout.Rect badgeRect(UiLayout layout, float scrollY) {
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        float badge = UiTokens.PROFILE_EDIT_BADGE;
        return new UiLayout.Rect(
                avatar.right() - badge + s(2),
                avatar.bottom() - badge + s(2),
                badge, badge);
    }

    private UiLayout.Rect tileRect(UiLayout layout, int index, float scrollY) {
        float top = layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H + UiTokens.SETTINGS_ROW_GAP - scrollY;
        float w = (layout.list.w() - UiTokens.PROFILE_TILE_GAP * 2.0F) / 3.0F;
        return new UiLayout.Rect(
                layout.list.x() + index * (w + UiTokens.PROFILE_TILE_GAP),
                top, w, UiTokens.PROFILE_TILE_H);
    }

    private UiLayout.Rect rowRect(UiLayout layout, int index, float scrollY) {
        float top = layout.list.y() + UiTokens.ROOT_CONTENT_GAP
                + UiTokens.PROFILE_AVATAR_HERO_H + UiTokens.SETTINGS_ROW_GAP
                + UiTokens.PROFILE_TILE_H + UiTokens.SETTINGS_ROW_GAP - scrollY;
        return new UiLayout.Rect(
                layout.list.x(),
                top + index * (UiTokens.PROFILE_ROW_H + UiTokens.SETTINGS_ROW_GAP),
                layout.list.w(),
                UiTokens.PROFILE_ROW_H);
    }

    /**
     * The local avatar menu (change/clear); anchored under the avatar itself
     * and wide enough for its longest label — the fixed conversation-menu
     * width truncates "Change avatar".
     */
    private UiLayout.Rect menuRect(UiLayout layout, float scrollY) {
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        float w = menuWidth();
        float h = UiTokens.MENU_H;
        float x = Math.max(layout.list.x() + s(8),
                Math.min(avatar.x() + avatar.w() / 2.0F - w / 2.0F,
                        layout.list.right() - w - s(8)));
        return new UiLayout.Rect(x, avatar.bottom() + s(6), w, h);
    }

    private float menuWidth() {
        Font font = FontManager.font(UiTokens.FONT_BUTTON);
        float w = UiTokens.MENU_W;
        w = Math.max(w, s(36) + SkiaFontRenderer.getStringWidth(font,
                tr("atomchat.profile.avatar.change")) + s(14));
        w = Math.max(w, s(36) + SkiaFontRenderer.getStringWidth(font,
                tr("atomchat.profile.avatar.clear")) + s(14));
        return w;
    }

    private UiLayout.Rect menuItemRect(UiLayout layout, float scrollY, int index) {
        UiLayout.Rect menu = menuRect(layout, scrollY);
        float rowH = UiTokens.MENU_H / 2.0F;
        return new UiLayout.Rect(menu.x(), menu.y() + index * rowH, menu.w(), rowH);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    /**
     * Handles a left click. Returns true when the click was consumed (menu
     * action, avatar, badge or info row); false lets the shell keep it.
     */
    public boolean onClick(float vmx, float vmy, UiLayout layout, float scrollY) {
        if (avatarMenuOpen) {
            for (int i = 0; i < 2; i++) {
                if (menuItemRect(layout, scrollY, i).contains(vmx, vmy)) {
                    avatarMenuOpen = false;
                    if (i == 0) {
                        handler.openAvatarPicker();
                    } else {
                        avatarMenuOpen = false;
                        handler.clearAvatar();
                    }
                    return true;
                }
            }
            // Any click outside the menu dismisses it; the click is consumed
            // so it cannot fall through onto the rows underneath.
            avatarMenuOpen = false;
            return true;
        }
        if (subjectIsSelf() && badgeRect(layout, scrollY).contains(vmx, vmy)) {
            handler.openAvatarPicker();
            return true;
        }
        if (subjectIsSelf() && avatarRect(layout, scrollY).contains(vmx, vmy)) {
            if (avatarStore.isSet()) {
                avatarMenuOpen = true;
            } else {
                handler.openAvatarPicker();
            }
            return true;
        }
        List<StatTile> tiles = statTiles();
        for (int i = 0; i < tiles.size(); i++) {
            if (tileRect(layout, i, scrollY).contains(vmx, vmy)) {
                handler.copyText(tiles.get(i).copy());
                return true;
            }
        }
        List<InfoRow> rows = infoRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rowRect(layout, i, scrollY).contains(vmx, vmy)) {
                handler.copyText(rows.get(i).value());
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;

        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            drawHeroCard(canvas, layout, vmx, vmy, scrollY);
            drawStatTiles(canvas, layout, vmx, vmy, scrollY);
            drawInfoRows(canvas, layout, vmx, vmy, scrollY);
            drawAvatarMenu(canvas, layout, vmx, vmy, scrollY);
        } finally {
            canvas.restore();
        }

        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        boolean overAvatar = avatar.contains(vmx, vmy);
        UiLayout.Rect badge = badgeRect(layout, scrollY);
        boolean overBadge = badge.contains(vmx, vmy);
        avatarHover = UiMotion.approach(avatarHover, overAvatar ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        badgeHover = UiMotion.approach(badgeHover, overBadge ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        int hovered = -1;
        List<InfoRow> rows = infoRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rowRect(layout, i, scrollY).contains(vmx, vmy)) {
                hovered = i;
                break;
            }
        }
        if (hovered != hoverRowIndex) {
            hoverRowIndex = hovered;
        }
        rowHover = UiMotion.approach(rowHover, hovered >= 0 ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        for (int i = 0; i < tileHover.length; i++) {
            boolean over = tileRect(layout, i, scrollY).contains(vmx, vmy);
            tileHover[i] = UiMotion.approach(tileHover[i], over ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        }
        for (int i = 0; i < menuItemHover.length; i++) {
            boolean over = avatarMenuOpen
                    && menuItemRect(layout, scrollY, i).contains(vmx, vmy);
            menuItemHover[i] = UiMotion.approach(menuItemHover[i], over ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
        }
        // Popup fade respects the decorative-motion switch like every other
        // overlay: when animations are off the duration is 0 and the menu
        // simply appears/disappears.
        menuAnim = UiMotion.approach(menuAnim, avatarMenuOpen ? 1.0F : 0.0F, dt,
                Animations.ms(UiMotion.POPUP_MS));
    }

    private void drawHeroCard(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        UiLayout.Rect hero = heroRect(layout, scrollY);
        if (hero.bottom() < layout.list.y() || hero.y() > layout.list.bottom()) {
            return;
        }
        SkiaDraw.drawRoundedRect(canvas, hero.x(), hero.y(), hero.w(), hero.h(),
                UiTokens.SETTINGS_TILE_RADIUS, Color.makeARGB(60, 255, 255, 255));

        // Avatar: the local custom avatar when the subject is self and one is
        // set (decoded off-thread; the skin shows while the decode is in
        // flight), the real skin otherwise — PlayerAvatar owns that chain.
        UiLayout.Rect avatar = avatarRect(layout, scrollY);
        Image face = PlayerAvatar.face(subjectUuid(), subjectName());
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                    avatar.w() / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                    avatar.w() / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
        if (avatarHover > 0.01F && subjectIsSelf() && avatarStore.isSet()) {
            SkiaDraw.drawRoundedRect(canvas, avatar.x(), avatar.y(), avatar.w(), avatar.h(),
                    avatar.w() / 2.0F, Color.makeARGB((int) (40.0F * avatarHover), 255, 255, 255));
        }

        // Persistent edit badge at the avatar's bottom-right; only the local
        // player's avatar is editable.
        if (subjectIsSelf()) {
            UiLayout.Rect badge = badgeRect(layout, scrollY);
            SkiaDraw.drawRoundedRect(canvas, badge.x(), badge.y(), badge.w(), badge.h(),
                    badge.w() / 2.0F, Color.makeARGB(215, 20, 22, 30));
            if (badgeHover > 0.01F) {
                SkiaDraw.drawRoundedRect(canvas, badge.x(), badge.y(), badge.w(), badge.h(),
                        badge.w() / 2.0F, Color.makeARGB((int) (60.0F * badgeHover), 255, 255, 255));
            }
            drawIconCentered(canvas, AppIcons.ICON_EDIT_PATH,
                    badge.x() + badge.w() / 2.0F, badge.y() + badge.h() / 2.0F,
                    UiTokens.PROFILE_EDIT_BADGE * 0.55F, Color.makeARGB(255, 255, 255, 255));
        }

        // Name under the avatar.
        Font nameFont = FontManager.font(UiTokens.PROFILE_NAME_FONT);
        SkiaFontRenderer.drawTextCentered(canvas, nameFont, subjectName(),
                hero.x() + hero.w() / 2.0F,
                avatar.bottom() + s(26),
                Color.makeARGB(255, 255, 255, 255));
    }

    /** Dashboard tiles: big value centred, small label under it. */
    private void drawStatTiles(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<StatTile> tiles = statTiles();
        Font valueFont = FontManager.font(UiTokens.PROFILE_TILE_VALUE_FONT);
        Font labelFont = FontManager.font(UiTokens.PROFILE_TILE_LABEL_FONT);
        for (int i = 0; i < tiles.size(); i++) {
            UiLayout.Rect tile = tileRect(layout, i, scrollY);
            if (tile.bottom() < layout.list.y() || tile.y() > layout.list.bottom()) {
                continue;
            }
            SkiaDraw.drawRoundedRect(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                    UiTokens.PROFILE_ROW_RADIUS, Color.makeARGB(60, 255, 255, 255));
            if (tileHover[i] > 0.01F) {
                SkiaDraw.drawRoundedRect(canvas, tile.x(), tile.y(), tile.w(), tile.h(),
                        UiTokens.PROFILE_ROW_RADIUS, Color.makeARGB((int) (45.0F * tileHover[i]), 255, 255, 255));
            }
            float cx = tile.x() + tile.w() / 2.0F;
            String value = SkiaFontRenderer.truncate(valueFont, tiles.get(i).value(),
                    tile.w() - UiTokens.PROFILE_ROW_PAD);
            SkiaFontRenderer.drawTextCentered(canvas, valueFont, value,
                    cx, tile.y() + tile.h() / 2.0F - s(4), Color.makeARGB(255, 255, 255, 255));
            SkiaFontRenderer.drawTextCentered(canvas, labelFont, tiles.get(i).label(),
                    cx, tile.y() + tile.h() / 2.0F + s(15), Color.makeARGB(200, 170, 170, 186));
        }
    }

    private void drawInfoRows(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        List<InfoRow> rows = infoRows();
        Font labelFont = FontManager.font(UiTokens.PROFILE_ROW_FONT);
        Font valueFont = FontManager.font(UiTokens.PROFILE_ROW_VALUE_FONT);
        for (int i = 0; i < rows.size(); i++) {
            UiLayout.Rect row = rowRect(layout, i, scrollY);
            if (row.bottom() < layout.list.y() || row.y() > layout.list.bottom()) {
                continue;
            }
            SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                    UiTokens.PROFILE_ROW_RADIUS, Color.makeARGB(60, 255, 255, 255));
            if (rowHover > 0.01F && i == hoverRowIndex) {
                SkiaDraw.drawRoundedRect(canvas, row.x(), row.y(), row.w(), row.h(),
                        UiTokens.PROFILE_ROW_RADIUS, Color.makeARGB((int) (45.0F * rowHover), 255, 255, 255));
            }
            float cy = row.y() + row.h() / 2.0F;
            SkiaFontRenderer.drawText(canvas, labelFont, rows.get(i).label(),
                    row.x() + UiTokens.PROFILE_ROW_PAD,
                    SkiaFontRenderer.centerBaselineY(labelFont, cy),
                    Color.makeARGB(255, 255, 255, 255));
            String value = rows.get(i).value();
            float valueMaxW = row.w() - UiTokens.PROFILE_ROW_PAD * 2
                    - SkiaFontRenderer.getStringWidth(labelFont, rows.get(i).label());
            // drawTextRight already centres on cy internally — passing a
            // pre-converted baseline double-converts and draws the text high.
            SkiaFontRenderer.drawTextRight(canvas, valueFont,
                    SkiaFontRenderer.truncate(valueFont, value, Math.max(s(24), valueMaxW)),
                    row.right() - UiTokens.PROFILE_ROW_PAD,
                    cy,
                    Color.makeARGB(255, 255, 255, 255));
        }
    }

    private void drawAvatarMenu(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        if (menuAnim < 0.01F) {
            return;
        }
        UiLayout.Rect menu = menuRect(layout, scrollY);
        float rowH = UiTokens.MENU_H / 2.0F;
        String[] labels = {
                tr("atomchat.profile.avatar.change"),
                tr("atomchat.profile.avatar.clear")
        };
        Path[] icons = {AppIcons.ICON_EDIT_PATH, AppIcons.ICON_TAB_PROFILE_PATH};
        Font font = FontManager.font(UiTokens.FONT_BUTTON);
        canvas.save();
        try (Paint layer = new Paint()) {
            // Same entrance language as the bubble context menu: layer alpha
            // fade plus a subtle scale from the menu's top centre.
            layer.setColor(Color.makeARGB((int) (255.0F * menuAnim), 0, 0, 0));
            canvas.saveLayer(Rect.makeXYWH(menu.x() - s(20), menu.y() - s(20),
                    menu.w() + s(40), menu.h() + s(40)), layer);
            float sc = 0.94F + 0.06F * menuAnim;
            canvas.translate(menu.x() + menu.w() / 2.0F, menu.y());
            canvas.scale(sc, sc);
            canvas.translate(-(menu.x() + menu.w() / 2.0F), -menu.y());
            SkiaDraw.drawRoundedShadow(canvas, menu.x(), menu.y(), menu.w(), menu.h(),
                    s(10), s(8), Color.makeARGB(100, 0, 0, 0));
            SkiaDraw.drawRoundedRect(canvas, menu.x(), menu.y(), menu.w(), menu.h(),
                    s(10), Color.makeARGB(245, 35, 39, 47));
            for (int i = 0; i < labels.length; i++) {
                float rowY = menu.y() + i * rowH;
                if (menuItemHover[i] > 0.01F) {
                    // Uniform s(4) inset on every side of the row capsule, the
                    // exact hover language of the bubble context menu.
                    SkiaDraw.drawRoundedRect(canvas, menu.x() + s(4), rowY + s(4),
                            menu.w() - s(8), rowH - s(8),
                            s(6), Color.makeARGB((int) (55.0F * menuItemHover[i]), 255, 255, 255));
                }
                float cy = rowY + rowH / 2.0F;
                drawIconCentered(canvas, icons[i], menu.x() + s(18), cy,
                        UiTokens.CONTEXT_ICON_SIZE, Color.makeARGB(255, 255, 255, 255));
                SkiaFontRenderer.drawText(canvas, font, labels[i], menu.x() + s(36),
                        SkiaFontRenderer.centerBaselineY(font, cy),
                        Color.makeARGB(255, 255, 255, 255));
            }
            // Close the saveLayer; the finally below closes the outer save().
            // A saveLayer per frame with no matching restore leaks one matrix
            // level per frame and drags the whole UI off-screen.
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy, float size, int color) {
        Rect b = icon.getBounds();
        if (b == null || b.isEmpty()) {
            return;
        }
        float scale = size / Math.max(b.getWidth(), b.getHeight());
        canvas.save();
        try {
            canvas.translate(cx - (b.getLeft() + b.getRight()) / 2.0F * scale,
                    cy - (b.getTop() + b.getBottom()) / 2.0F * scale);
            canvas.scale(scale, scale);
            try (Paint paint = new Paint().setColor(color).setAntiAlias(true)
                    .setMode(PaintMode.STROKE)
                    .setStrokeWidth(1.5F / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
