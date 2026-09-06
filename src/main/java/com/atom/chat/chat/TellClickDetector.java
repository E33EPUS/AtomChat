package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Port of e33chat's layer-2 tell-click attribution (2.3.14 restructure).
 *
 * <p>Plugins attach "click to whisper" SUGGEST_COMMAND events to the sender's
 * displayed name — the command value carries the real profile name, giving
 * deterministic sender attribution even on nickname servers where text
 * matching against the online list fails or decorates the name beyond
 * recognition.
 *
 * <p>Guards, in order:
 * <ul>
 *   <li>vanilla broadcast translation keys never qualify;</li>
 *   <li>the clickable segment must sit near the line start (≤ max(32, len/3))
 *       — feedback like "杀死了E33EPUS" carries a whole-line /tell click whose
 *       first segment is not a name;</li>
 *   <li>the clicked text must actually contain the sender's name (matched
 *       against all name variants), not just any /tell-bearing decoration.</li>
 * </ul>
 */
public final class TellClickDetector {
    private TellClickDetector() {
    }

    private static final String[] TELL_PREFIXES = {"/tell ", "/msg ", "/w ", "/whisper "};

    public static SenderMeta detectByTellClick(Text message, String text) {
        if (ChatClassifier.isVanillaBroadcast(message)) {
            return null;
        }
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null || text == null || text.isEmpty()) {
            return null;
        }
        final int[] pos = {0};
        final int[] range = {-1, -1};
        final String[] tellName = {null};
        final String[] clickedText = {null};
        message.visit((style, str) -> {
            int s = pos[0];
            int e = s + str.length();
            pos[0] = e;
            var click = style.getClickEvent();
            if (tellName[0] == null && click != null
                    && click.getAction() == net.minecraft.text.ClickEvent.Action.SUGGEST_COMMAND
                    && click.getValue() != null) {
                String cmd = click.getValue();
                for (String prefix : TELL_PREFIXES) {
                    if (cmd.startsWith(prefix)) {
                        String n = cmd.substring(prefix.length()).trim();
                        int sp = n.indexOf(' ');
                        if (sp > 0) {
                            n = n.substring(0, sp);
                        }
                        if (!n.isEmpty()) {
                            tellName[0] = n;
                            range[0] = s;
                            range[1] = e;
                            clickedText[0] = str;
                        }
                        break;
                    }
                }
            }
            return java.util.Optional.<Object>empty();
        }, net.minecraft.text.Style.EMPTY);

        int nameRangeLimit = Math.max(32, text.length() / 3);
        if (tellName[0] == null || range[0] > nameRangeLimit) {
            return null;
        }

        PlayerListEntry sender = null;
        for (var info : player.networkHandler.getPlayerList()) {
            String profile = info.getProfile().getName();
            if (profile.equals(tellName[0]) || profile.replaceAll("§.", "").equals(tellName[0])) {
                sender = info;
                break;
            }
        }
        UUID cachedId = null;
        if (sender == null) {
            cachedId = SeenPlayers.findUuid(tellName[0]);
            if (cachedId == null) {
                return null;
            }
        }

        if (sender != null) {
            // The clicked segment must actually be the sender's displayed name.
            String clicked = clickedText[0].replaceAll("§.", "").trim();
            boolean clickedIsName = false;
            for (String cand : ChatClassifier.nameCandidates(sender)) {
                if (!cand.isEmpty() && clicked.contains(cand)) {
                    clickedIsName = true;
                    break;
                }
            }
            if (!clickedIsName) {
                return null;
            }
        }

        int b = range[1];
        if (b < text.length() && text.charAt(b) == '>') {
            b++;
        }
        int contentStart = MessagePresentation.skipSeparators(text, b);
        if (contentStart >= text.length()) {
            return null;
        }

        String profile = sender != null ? sender.getProfile().getName() : tellName[0];
        UUID id = sender != null ? sender.getProfile().getId() : cachedId;
        // The clicked segment is the decorated sender display ("[VIP]Steve"):
        // AtomChat's text-layer meta carries it as the display label.
        String display = clickedText[0].replaceAll("§.", "").trim();
        if (display.isEmpty()) {
            display = profile;
        }
        return new SenderMeta(id, display, profile, text.substring(contentStart).strip(),
                false, false, null, null, null);
    }
}
