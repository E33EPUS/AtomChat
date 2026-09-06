package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of e33chat's EasyBotParser: built-in recognition for EasyBot QQ group
 * messages relayed into the game as system broadcasts.
 *
 * <p>EasyBot's Minecraft-side piece is only a renderer — the "[群名]"/"&lt;昵称&gt;"
 * part is assembled bot-side, so the exact shape depends on the server's
 * template. Shapes seen in the wild (all battle-tested on e33chat):
 * <pre>
 *   [群名] &lt;昵称(QQ号)&gt; 内容      (default template)
 *   [群名] &lt;昵称&gt; 内容
 *   &lt;昵称&gt; 内容                   (group label removed)
 *   &lt;昵称（群名片）&gt; 内容
 * </pre>
 * The leading [label] is optional — angle-bracket name + content is the
 * structural signal. Custom server templates remain the job of the user's
 * chat templates.
 *
 * <p>Stepping-aside rules carried over from the母本: a line whose display name
 * is a locally known player keeps the player path (and its real UUID/skin);
 * without a QQ number, generic broadcast labels ("[公告] &lt;Server&gt; ...",
 * "&lt;系统&gt; ...") stay system messages.
 */
public final class EasyBotParser {
    private EasyBotParser() {
    }

    // (?:[label])? <name> content — (?s) lets content span newlines.
    private static final Pattern RELAY_FORMAT = Pattern.compile(
            "^(?:\\[([^\\]]*)\\]\\s*)?<([^>]*)>\\s*(?s:(.*))$");

    // QQ numbers are 5-12 digits, optionally wrapped in parentheses (half- or
    // full-width) at the end of the angle-bracket name area: "昵称(123456)".
    private static final Pattern QQ_AT_END = Pattern.compile("[（(]?(\\d{5,12})[)）]?$");

    private static final int MAX_NAME = 32;

    private static final Set<String> BROADCAST_LABELS = Set.of(
            "系统", "公告", "服务器", "广播", "提示", "通知",
            "system", "server", "notice", "broadcast", "announcement", "alert");

    public static SenderMeta tryParse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher m = RELAY_FORMAT.matcher(text);
        if (!m.matches()) {
            return null;
        }

        String groupName = m.group(1) == null ? "" : m.group(1).trim();
        String nameArea = m.group(2) == null ? "" : m.group(2).trim();
        String content = m.group(3);
        if (nameArea.isEmpty() || content == null || content.isBlank()) {
            return null;
        }
        if (nameArea.length() > MAX_NAME || nameArea.indexOf('\n') >= 0) {
            return null;
        }

        String nick = null;
        String qq = null;
        Matcher qm = QQ_AT_END.matcher(nameArea);
        if (qm.find()) {
            qq = qm.group(1);
            String before = nameArea.substring(0, qm.start()).trim();
            int paren = before.lastIndexOf('(');
            if (paren < 0) {
                paren = before.lastIndexOf('（');
            }
            if (paren >= 0) {
                before = before.substring(0, paren).trim();
            }
            if (!before.isEmpty()) {
                nick = before;
            }
        } else if (nameArea.matches("\\d{5,12}")) {
            qq = nameArea;
        } else {
            nick = nameArea;
        }

        String displayName = nick != null && !nick.isEmpty() ? nick : qq;
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }

        // Without a QQ number there is no strong EasyBot signal: generic
        // broadcast labels stay system messages.
        if (qq == null && (isBroadcastLabel(groupName) || isBroadcastLabel(displayName))) {
            return null;
        }

        // A locally known player relayed through a system packet keeps its
        // profile UUID (and therefore its skin) only on the player path —
        // step aside so the guard can claim the line instead.
        if (isKnownPlayer(displayName)) {
            return null;
        }

        String rawPlayerName = qq != null ? qq : displayName;
        return new SenderMeta(null, displayName, rawPlayerName, content.strip(),
                false, false, null, null, null);
    }

    /**
     * Exact-match only: {@link ChatClassifier#resolveOnlinePlayer} also does a
     * substring fallback, which would hand every QQ nickname containing a
     * player name back to the player path (and then drop it entirely).
     */
    private static boolean isKnownPlayer(String displayName) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null || mc.player.networkHandler == null) {
                return false;
            }
            for (PlayerListEntry info : mc.player.networkHandler.getPlayerList()) {
                for (String cand : ChatClassifier.nameCandidates(info)) {
                    if (cand.equalsIgnoreCase(displayName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            return false;
        }
        return false;
    }

    private static boolean isBroadcastLabel(String s) {
        String zone = s.trim();
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                    || (open == '<' && close == '>')
                    || (open == '(' && close == ')')
                    || (open == '（' && close == '）')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        return !zone.isEmpty() && BROADCAST_LABELS.contains(zone.toLowerCase(Locale.ROOT));
    }
}
