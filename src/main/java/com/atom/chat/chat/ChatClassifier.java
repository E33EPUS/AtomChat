package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Guard 0 and identity helpers: deterministic routing by vanilla translation
 * key, plus player-name resolution shared by the capture pipeline.
 *
 * <p>Trimmed port of e33chat's ChatClassifier (MIT, same author).
 */
public final class ChatClassifier {
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
     * @return true for vanilla system/broadcast lines that must never be claimed
     *         as player chat (advancements/deaths/joins/admin/emote/team).
     */
    public static boolean isVanillaBroadcast(Text message) {
        if (message.getContent() instanceof net.minecraft.text.TranslatableTextContent tc) {
            String key = tc.getKey();
            return key.startsWith("chat.type.advancement.")
                    || key.startsWith("death.")
                    || key.startsWith("multiplayer.player.")
                    || key.startsWith("commands.")
                    || key.equals("chat.type.admin")
                    || key.equals("chat.type.announcement")
                    || key.equals("chat.type.emote")
                    || key.startsWith("chat.type.team.");
        }
        return false;
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
