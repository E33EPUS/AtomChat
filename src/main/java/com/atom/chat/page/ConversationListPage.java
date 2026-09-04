package com.atom.chat.page;

import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.font.FontManager;
import com.atom.chat.image.PlayerAvatar;
import com.atom.chat.render.SkiaDraw;
import com.atom.chat.render.SkiaFontRenderer;
import com.atom.chat.ui.AppIcons;
import com.atom.chat.ui.UiLayout;
import com.atom.chat.ui.UiMotion;
import com.atom.chat.ui.UiTokens;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.ColorFilter;
import io.github.humbleui.skija.ColorMatrix;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * QQ-style conversation list root page: Public is always first, then every
 * online player (server contact book), then recently-chatted offline players.
 */
public final class ConversationListPage {
    private static final float ROW_H = UiTokens.s(64);
    private static final float ROW_GAP = UiTokens.s(8);
    private static final float AVATAR = UiTokens.s(44);
    private static final float AVATAR_RADIUS = UiTokens.s(12);
    private static final float ICON_INSET = UiTokens.s(10);

    private final PageHost host;
    private float rowHover;
    private int hoveredIndex = -1;
    private long lastFrameMs = System.currentTimeMillis();

    public enum RowKind { PUBLIC, PLAYER }

    public record Row(RowKind kind, PlayerRef player, ChatMessage latest,
                      int unread, boolean online, boolean blocked) {
        public String title() {
            if (kind == RowKind.PUBLIC) {
                return tr("atomchat.conversation.world");
            }
            return player != null ? player.realName() : "";
        }
    }

    public record RowHit(Row row, int index, float x, float y, float w, float h) {
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public ConversationListPage(PageHost host) {
        this.host = host;
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    private static float s(float v) {
        return UiTokens.s(v);
    }

    /** Builds the row list in display order. */
    public List<Row> rows() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Row> all = new ArrayList<>();
        all.add(new Row(RowKind.PUBLIC, null, latestPublic(), ChatStore.publicUnread(), true, false));

        List<PlayerRef> online = onlinePlayers();
        List<PlayerRef> known = PrivateChatStore.knownPartners();
        for (PlayerRef p : online) {
            if (p.equals(ownRef(client))) {
                continue;
            }
            ChatMessage latest = PrivateChatStore.latest(p);
            boolean blocked = BlockList.isBlocked(p);
            all.add(new Row(RowKind.PLAYER, p, latest, PrivateChatStore.unread(p), true, blocked));
        }
        for (PlayerRef p : known) {
            if (isOnline(p)) {
                continue;
            }
            ChatMessage latest = PrivateChatStore.latest(p);
            boolean blocked = BlockList.isBlocked(p);
            all.add(new Row(RowKind.PLAYER, p, latest, PrivateChatStore.unread(p), false, blocked));
        }

        // Sort only player rows after Public by the agreed rules: dynamic chats
        // first (latest activity descending), static/no-history by name; offline
        // section after online. Public is always row 0.
        List<Row> sortedPlayers = new ArrayList<>(all.subList(1, all.size()));
        sortedPlayers.sort(Comparator
                .comparing((Row r) -> r.online() ? 0 : 1)
                .thenComparing(r -> r.online() ? 0 : (r.unread() > 0 ? 0 : 1))
                .thenComparing(r -> r.online() && r.latest() == null ? 1 : 0)
                .thenComparing(r -> r.latest() != null ? -r.latest().getTimestamp() : 0L)
                .thenComparing(r -> r.latest() == null ? r.title().toLowerCase(java.util.Locale.ROOT) : ""));
        List<Row> result = new ArrayList<>();
        result.add(new Row(RowKind.PUBLIC, null, latestPublic(), ChatStore.publicUnread(), true, false));
        result.addAll(sortedPlayers);
        return result;
    }

    private static List<PlayerRef> onlinePlayers() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return List.of();
        }
        List<PlayerRef> out = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            out.add(PlayerRef.of(entry.getProfile().getId(), name));
        }
        return out;
    }

    private static boolean isOnline(PlayerRef player) {
        if (player == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return false;
        }
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (player.uuid() != null && player.uuid().equals(entry.getProfile().getId())) {
                return true;
            }
            if (player.realName() != null && player.realName().equalsIgnoreCase(entry.getProfile().getName())) {
                return true;
            }
        }
        return false;
    }

    private static PlayerRef ownRef(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }
        return PlayerRef.of(client.player.getUuid(), client.player.getName().getString());
    }

    public float measureContent(UiLayout layout) {
        List<Row> rows = rows();
        if (rows.isEmpty()) {
            return UiTokens.ROOT_CONTENT_GAP;
        }
        return UiTokens.ROOT_CONTENT_GAP + rows.size() * ROW_H + (rows.size() - 1) * ROW_GAP;
    }

    public void render(Canvas canvas, UiLayout layout, float vmx, float vmy, float scrollY) {
        long now = System.currentTimeMillis();
        float dt = Math.min(50.0F, Math.max(1.0F, now - lastFrameMs));
        lastFrameMs = now;
        List<Row> rows = rows();
        float listTop = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        int hovered = -1;
        canvas.save();
        try {
            SkiaDraw.clip(canvas, layout.list.x(), layout.list.y(), layout.list.w(), layout.list.h(), 0.0F);
            for (int i = 0; i < rows.size(); i++) {
                float y = listTop + i * (ROW_H + ROW_GAP) - scrollY;
                if (y + ROW_H < layout.list.y() || y > layout.list.bottom()) {
                    continue;
                }
                boolean over = vmx >= layout.list.x() && vmx <= layout.list.right()
                        && vmy >= y && vmy <= y + ROW_H;
                if (over) {
                    hovered = i;
                }
                drawRow(canvas, rows.get(i), layout.list.x(), y, layout.list.w(), over, i);
            }
        } finally {
            canvas.restore();
        }
        hoveredIndex = hovered;
        rowHover = UiMotion.approach(rowHover, hovered >= 0 ? 1.0F : 0.0F, dt, UiMotion.HOVER_MS);
    }

    /** Hit-tests rows using the same geometry as render. */
    public RowHit hit(float vmx, float vmy, UiLayout layout, float scrollY) {
        List<Row> rows = rows();
        float listTop = layout.list.y() + UiTokens.ROOT_CONTENT_GAP;
        for (int i = 0; i < rows.size(); i++) {
            float y = listTop + i * (ROW_H + ROW_GAP) - scrollY;
            RowHit hit = new RowHit(rows.get(i), i, layout.list.x(), y, layout.list.w(), ROW_H);
            if (hit.contains(vmx, vmy)) {
                return hit;
            }
        }
        return null;
    }

    private void drawRow(Canvas canvas, Row row, float x, float y, float w, boolean hover, int index) {
        float hoverAlpha = (hover && index == hoveredIndex) ? 1.0F : 0.0F;
        if (hover) {
            hoverAlpha = Math.max(hoverAlpha, rowHover);
        }
        if (row.blocked()) {
            drawBlockedRow(canvas, row, x, y, w, hover, hoverAlpha);
        } else {
            drawNormalRow(canvas, row, x, y, w, hover, hoverAlpha);
        }
    }

    private void drawNormalRow(Canvas canvas, Row row, float x, float y, float w, boolean hover, float hoverAlpha) {
        SkiaDraw.drawRoundedRect(canvas, x, y, w, ROW_H, s(12), Color.makeARGB(60, 255, 255, 255));
        if (hoverAlpha > 0.01F) {
            SkiaDraw.drawRoundedRect(canvas, x, y, w, ROW_H, s(12),
                    Color.makeARGB((int) (45.0F * hoverAlpha), 255, 255, 255));
        }
        drawRowContent(canvas, row, x, y, w);
    }

    private void drawBlockedRow(Canvas canvas, Row row, float x, float y, float w, boolean hover, float hoverAlpha) {
        float[] matrix = {
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0.2126F, 0.7152F, 0.0722F, 0, 0,
                0, 0, 0, 1, 0
        };
        canvas.save();
        try (Paint layer = new Paint().setColorFilter(ColorFilter.makeMatrix(new ColorMatrix(matrix)))) {
            canvas.saveLayer(Rect.makeXYWH(x - 1, y - 1, w + 2, ROW_H + 2), layer);
            SkiaDraw.drawRoundedRect(canvas, x, y, w, ROW_H, s(12), Color.makeARGB(60, 255, 255, 255));
            if (hoverAlpha > 0.01F) {
                SkiaDraw.drawRoundedRect(canvas, x, y, w, ROW_H, s(12),
                        Color.makeARGB((int) (45.0F * hoverAlpha), 255, 255, 255));
            }
            drawRowContent(canvas, row, x, y, w);
            canvas.restore();
        } finally {
            canvas.restore();
        }
    }

    private void drawRowContent(Canvas canvas, Row row, float x, float y, float w) {
        float iconSize = AVATAR;
        float iconRadius = AVATAR_RADIUS;
        float iconInset = ICON_INSET;
        float iconX = x + iconInset;
        float iconY = y + (ROW_H - iconSize) / 2.0F;

        if (row.kind == RowKind.PUBLIC) {
            SkiaDraw.drawRoundedRect(canvas, iconX, iconY, iconSize, iconSize, iconRadius,
                    Color.makeARGB(60, 255, 255, 255));
            drawIconCentered(canvas, AppIcons.ICON_GLOBE_PATH,
                    iconX + iconSize / 2.0F, iconY + iconSize / 2.0F, s(26),
                    Color.makeARGB(255, 255, 255, 255));
        } else if (row.player() != null) {
            drawPlayerAvatar(canvas, row.player(), iconX, iconY, iconSize);
        }

        Font nameFont = FontManager.font(UiTokens.FONT_NAME);
        Font subFont = FontManager.font(UiTokens.FONT_QUOTE);
        Font timeFont = FontManager.font(UiTokens.FONT_QUOTE);
        float textX = iconX + iconSize + s(12);
        float nameCenterY = y + ROW_H / 2.0F - s(9);
        float previewCenterY = y + ROW_H / 2.0F + s(12);

        String time = row.kind == RowKind.PUBLIC ? formatMessageTime(latestPublic()) : "";
        if (row.kind == RowKind.PLAYER && row.latest() != null) {
            time = formatMessageTime(row.latest());
        }
        float timeW = time.isEmpty() ? 0.0F : SkiaFontRenderer.getStringWidth(timeFont, time);
        float timeX = x + w - s(10) - timeW;

        float badgeW = 0.0F;
        String badgeText = "";
        if (row.unread() > 0) {
            badgeText = row.unread() > 99 ? "99+" : String.valueOf(row.unread());
            badgeW = Math.max(s(18), SkiaFontRenderer.getStringWidth(timeFont, badgeText) + s(9));
        }
        float nameMaxW = Math.max(0.0F, timeX - textX - s(8));
        String name = truncateToWidth(nameFont, row.title(), nameMaxW);
        SkiaFontRenderer.drawText(canvas, nameFont, name, textX,
                SkiaFontRenderer.centerBaselineY(nameFont, nameCenterY),
                Color.makeARGB(255, 255, 255, 255));
        if (!time.isEmpty()) {
            SkiaFontRenderer.drawText(canvas, timeFont, time, timeX,
                    SkiaFontRenderer.centerBaselineY(timeFont, nameCenterY),
                    Color.makeARGB(190, 170, 170, 186));
        }

        float maxPreviewW = Math.max(0.0F, x + w - textX - s(8) - (badgeW > 0 ? badgeW + s(8) : 0.0F));
        String preview = previewText(row);
        if (preview == null || preview.isBlank()) {
            preview = tr("atomchat.conversation.start");
        }
        preview = truncateToWidth(subFont, preview, maxPreviewW);
        SkiaFontRenderer.drawText(canvas, subFont, preview, textX,
                SkiaFontRenderer.centerBaselineY(subFont, previewCenterY),
                Color.makeARGB(220, 170, 170, 186));

        if (badgeW > 0) {
            Font badgeFont = FontManager.font(UiTokens.FONT_QUOTE);
            float bh = s(18);
            float bx = x + w - s(10) - badgeW;
            float by = y + ROW_H / 2.0F + s(12) - bh / 2.0F;
            SkiaDraw.drawRoundedRect(canvas, bx, by, badgeW, bh, bh / 2.0F, Color.makeARGB(255, 244, 67, 54));
            SkiaFontRenderer.drawTextCentered(canvas, badgeFont, badgeText,
                    bx + badgeW / 2.0F, by + bh / 2.0F, Color.makeARGB(255, 255, 255, 255));
        }
    }

    private static void drawPlayerAvatar(Canvas canvas, PlayerRef player, float x, float y, float size) {
        Image face = PlayerAvatar.face(player.uuid(), player.realName());
        if (face != null) {
            SkiaDraw.drawRoundedImage(canvas, face, x, y, size, size, size / 2.0F, SamplingMode.LINEAR);
        } else {
            SkiaDraw.drawRoundedRect(canvas, x, y, size, size, size / 2.0F, Color.makeARGB(255, 120, 130, 145));
        }
    }

    private static String previewText(Row row) {
        if (row.kind == RowKind.PUBLIC) {
            ChatMessage latest = latestPublic();
            if (latest == null) {
                return tr("atomchat.conversation.empty");
            }
            return previewForPublic(latest);
        }
        ChatMessage latest = row.latest();
        if (latest == null) {
            return tr("atomchat.conversation.start");
        }
        if (latest.isOwn()) {
            return tr("atomchat.conversation.me") + ": " + friendlyContent(latest);
        }
        return friendlyContent(latest);
    }

    private static ChatMessage latestPublic() {
        List<ChatMessage> messages = ChatStore.get().snapshot();
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private static String previewForPublic(ChatMessage msg) {
        if (msg.isSystem()) {
            String content = friendlyContent(msg);
            return content.isEmpty() ? tr("atomchat.conversation.empty") : content;
        }
        String name = msg.getSenderName();
        if (name == null || name.isBlank()) {
            name = tr("atomchat.sender.player");
        }
        String content = friendlyContent(msg);
        return name + ": " + (content.isEmpty() ? tr("atomchat.conversation.empty") : content);
    }

    private static String friendlyContent(ChatMessage msg) {
        String raw = msg.getRawText();
        if (hasImageCode(raw)) {
            return tr("atomchat.hud.image");
        }
        String text = msg.getContentText();
        return text == null ? "" : text.trim();
    }

    private static boolean hasImageCode(String text) {
        return text != null
                && (text.contains("[[CICode,url=") || text.contains("[CICode,url="));
    }

    private static String formatMessageTime(ChatMessage msg) {
        if (msg == null) {
            return "";
        }
        ZonedDateTime dt = Instant.ofEpochMilli(msg.getTimestamp()).atZone(ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (date.equals(today.minusDays(1))) {
            return tr("atomchat.time.yesterday");
        }
        if (date.equals(today.minusDays(2))) {
            return tr("atomchat.time.beforeYesterday");
        }
        return tr("atomchat.time.date", dt.getMonthValue(), dt.getDayOfMonth());
    }

    private static String truncateToWidth(Font font, String text, float maxW) {
        if (text.isEmpty() || maxW <= 0.0F || SkiaFontRenderer.getStringWidth(font, text) <= maxW) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && SkiaFontRenderer.getStringWidth(font, t + "…") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    private static void drawIconCentered(Canvas canvas, Path icon, float cx, float cy,
                                         float size, int color) {
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
                    .setStrokeWidth(UiTokens.iconStroke(size) / scale)
                    .setStrokeCap(PaintStrokeCap.ROUND)
                    .setStrokeJoin(PaintStrokeJoin.ROUND)) {
                canvas.drawPath(icon, paint);
            }
        } finally {
            canvas.restore();
        }
    }
}
