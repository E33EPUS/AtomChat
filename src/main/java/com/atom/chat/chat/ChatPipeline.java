package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Guard 1 orchestration for system/disguised channels: text-level fallback
 * that identifies a player line from the online/known name list.
 *
 * <p>Trimmed port of e33chat's ChatPipeline (MIT, same author). Plain-string
 * parsing is used to locate style-slice boundaries; rich Text slicing then
 * preserves the original run styles when available.
 */
public final class ChatPipeline {
    private ChatPipeline() {
    }

    /** All online names (profile + tab display + §-stripped variants). */
    public static List<String> onlineNameCandidates() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        player.networkHandler.getPlayerList().forEach(info -> {
            for (String cand : ChatClassifier.nameCandidates(info)) {
                names.add(cand);
            }
        });
        return new ArrayList<>(names);
    }

    /**
     * Tries to parse a decorated system-channel line as a player message.
     * Returns null when the line is not a player line.
     */
    public static SenderMeta tryParsePlayerLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<String> names = onlineNameCandidates();
        if (names.isEmpty()) {
            return null;
        }
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, names);
        if (parsed.isEmpty()) {
            return null;
        }
        var pl = parsed.orElseThrow();
        // Whitespace-only gap = broadcast sentence ("Steve joined the game").
        if (MessagePresentation.isWhitespaceOnlyGap(text, pl.nameEnd(), pl.contentStart())) {
            return null;
        }

        PlayerListEntry info = ChatClassifier.resolveOnlinePlayer(pl.playerName());
        String profile = info != null ? info.getProfile().getName() : pl.playerName();
        UUID uuid = info != null ? info.getProfile().getId() : ChatClassifier.resolveUuid(pl.playerName());
        return new SenderMeta(uuid, pl.displayLabel(), profile, pl.content(), false);
    }

    /**
     * Best-effort extraction of the decorated display label ("[VIP]Steve")
     * from the final HUD line when the channel layer only supplied the raw
     * profile name. Returns null when the line does not contain the sender.
     */
    public static String decoratedDisplayName(String fullText, SenderMeta meta) {
        if (meta == null || fullText == null) {
            return null;
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (meta.senderName() != null) {
            candidates.add(meta.senderName());
        }
        if (meta.profileName() != null) {
            candidates.add(meta.profileName());
        }
        if (candidates.isEmpty()) {
            return null;
        }
        var parsed = MessagePresentation.parseDecoratedPlayerLine(fullText, candidates);
        if (parsed.isPresent()) {
            String label = parsed.get().displayLabel();
            if (label != null && !label.isBlank()) {
                return label;
            }
        }
        return null;
    }

    /**
     * Best-effort content extraction from the final decorated line when the
     * channel layer only supplied identity. Falls back to the full line.
     */
    public static String extractContent(String fullText, SenderMeta meta) {
        if (meta == null) {
            return fullText;
        }
        if (meta.contentText() != null) {
            return meta.contentText();
        }
        if (fullText == null) {
            return null;
        }
        String needle = meta.senderName() != null ? meta.senderName() : meta.profileName();
        if (needle == null) {
            return fullText;
        }
        int idx = fullText.indexOf(needle);
        if (idx < 0 && meta.profileName() != null) {
            idx = fullText.indexOf(meta.profileName());
            needle = meta.profileName();
        }
        if (idx < 0) {
            return fullText;
        }
        int sep = MessagePresentation.skipSeparators(fullText, idx + needle.length());
        if (sep <= idx + needle.length() || sep >= fullText.length()) {
            return fullText;
        }
        return fullText.substring(sep).trim();
    }

    /**
     * Slices the final decorated line into styled sender and content parts.
     * Only returns a result when the line parses as a player line for the
     * sender/profile names carried by {@code meta}; otherwise returns empty so
     * callers keep their system-safe fallback.
     */
    public static Optional<RichChatParts> sliceRichText(Text fullLine, SenderMeta meta) {
        if (fullLine == null || meta == null) {
            return Optional.empty();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (meta.senderName() != null) {
            candidates.add(meta.senderName());
        }
        if (meta.profileName() != null) {
            candidates.add(meta.profileName());
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String text = fullLine.getString();
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, candidates);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var pl = parsed.orElseThrow();
        // Whitespace-only gap = broadcast sentence ("Steve joined the game").
        if (MessagePresentation.isWhitespaceOnlyGap(text, pl.nameEnd(), pl.contentStart())) {
            return Optional.empty();
        }

        RichText full = RichText.of(fullLine);
        RichText sender = full.slice(0, pl.labelEnd());
        RichText content = full.slice(pl.contentStart(), text.length()).linkifyUrls();
        return Optional.of(new RichChatParts(sender, content));
    }
}
