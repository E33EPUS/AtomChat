package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Guard 0 and identity helpers: deterministic routing by vanilla translation
 * key, plus player-name resolution shared by the capture pipeline.
 *
 * <p>Trimmed port of e33chat's ChatClassifier (MIT, same author).
 */
public final class ChatClassifier {
    public enum Route {
        PLAYER,
        SYSTEM,
        PRIVATE,
        UNKNOWN
    }

    private ChatClassifier() {
    }

    public static String[] nameCandidates(PlayerListEntry info) {
        Set<String> out = new LinkedHashSet<>();
        String profile = info.getProfile().getName();
        addNameVariants(out, profile);
        Text tab = info.getDisplayName();
        if (tab != null) {
            addNameVariants(out, tab.getString().trim());
        }
        return out.toArray(new String[0]);
    }

    public static void addNameVariants(Set<String> out, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        out.add(name);
        String stripped = name.replaceAll("§.", "");
        if (!stripped.isEmpty()) {
            out.add(stripped);
        }
    }

    /**
     * Classifies a vanilla chat message by its translation key.
     *
     * <p>PLAYER covers signed/team player chat; PRIVATE is reserved for future
     * private-message UI; SYSTEM covers vanilla broadcasts that must not be
     * claimed as player chat; anything else is UNKNOWN and may be inspected by
     * the tolerant text fallback.
     */
    public static Route classifyByKey(Text message) {
        if (message.getContent() instanceof net.minecraft.text.TranslatableTextContent tc) {
            String key = tc.getKey();
            if (isPlayerKey(key)) {
                return Route.PLAYER;
            }
            if (isPrivateKey(key)) {
                return Route.PRIVATE;
            }
            if (isSystemKey(key)) {
                return Route.SYSTEM;
            }
        }
        return Route.UNKNOWN;
    }

    /**
     * @return true for vanilla system/broadcast lines that must never be claimed
     *         as player chat (advancements/deaths/joins/admin/emote/commands).
     */
    public static boolean isVanillaBroadcast(Text message) {
        return classifyByKey(message) == Route.SYSTEM;
    }

    /**
     * Xaero-compatible mods share waypoint data as chat with machine prefixes
     * ({@code xaero-waypoint:}, {@code xaero_waypoint:},
     * {@code xaero_waypoint_add:}). These are not human player chat: e33chat
     * routes them to the system channel so they are never claimed as a player
     * bubble and never swallowed as an own echo.
     */
    public static boolean isXaeroWaypointData(String line) {
        if (line == null) {
            return false;
        }
        String text = line;
        if (text.startsWith("<")) {
            int end = text.indexOf("> ");
            if (end >= 0) {
                text = text.substring(end + 2);
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("xaero-waypoint:")
                || lower.startsWith("xaero_waypoint:")
                || lower.startsWith("xaero_waypoint_add:");
    }

    private static boolean isPlayerKey(String key) {
        return key.equals("chat.type.text")
                || key.equals("chat.type.team.text")
                || key.equals("chat.type.team.sent");
    }

    private static boolean isPrivateKey(String key) {
        return key.equals("commands.message.display.incoming")
                || key.equals("commands.message.display.outgoing");
    }

    private static boolean isSystemKey(String key) {
        return key.startsWith("chat.type.advancement.")
                || key.startsWith("death.")
                || key.startsWith("multiplayer.player.")
                || key.startsWith("commands.")
                || key.equals("chat.type.admin")
                || key.equals("chat.type.announcement")
                || key.equals("chat.type.emote");
    }

    /**
     * Resolves a display name to an online PlayerListEntry. Exact match over all
     * name variants first, then longest contained profile/name for decorated
     * names like "[Title]Steve".
     */
    public static PlayerListEntry resolveOnlinePlayer(String displayName) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null || displayName == null || displayName.isEmpty()) {
            return null;
        }
        var online = player.networkHandler.getPlayerList();
        for (var info : online) {
            for (String cand : nameCandidates(info)) {
                if (cand.equals(displayName)) {
                    return info;
                }
            }
        }
        PlayerListEntry best = null;
        int bestLen = 0;
        for (var info : online) {
            for (String cand : nameCandidates(info)) {
                if (cand.length() >= 3 && cand.length() > bestLen && displayName.contains(cand)) {
                    best = info;
                    bestLen = cand.length();
                }
            }
        }
        return best;
    }

    public static UUID resolveUuid(String displayName) {
        PlayerListEntry info = resolveOnlinePlayer(displayName);
        return info != null ? info.getProfile().getId() : null;
    }
}
