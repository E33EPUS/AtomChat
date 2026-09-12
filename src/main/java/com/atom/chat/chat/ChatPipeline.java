package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

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
 * parsing is used to locate style-slice boundaries; rich Component slicing then
 * preserves the original run styles when available.
 */
public final class ChatPipeline {
    private ChatPipeline() {
    }

    /** All online names (profile + tab display + §-stripped variants), then
     *  players once seen in chat — the offline-player tail that lets relayed
     *  lines (bot bridges, delayed echoes) still parse as player chat. */
    public static List<String> onlineNameCandidates() {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        player.connection.getOnlinePlayers().forEach(info -> {
            for (String cand : ChatClassifier.nameCandidates(info)) {
                names.add(cand);
            }
        });
        names.addAll(SeenPlayers.profileNames());
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

        PlayerInfo info = ChatClassifier.resolveOnlinePlayer(pl.playerName());
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
     * Slices the reply body out of a quote-prefixed rich content line.
     *
     * <p>The wire carries quotes as the plain-text prefix
     * {@code 「引用 @Name: quoted」body}; rebuilding {@code body} with
     * {@link RichText#literal} kept the visible text but dropped every run
     * style inside the reply (server rich content, coloured mentions). The rich
     * line already carries the same text, so slice after the closing bracket
     * instead. Falls back to a literal when the rich line does not contain the
     * parsed body verbatim (translated prefixes, whitespace drift, plain-only
     * sources).
     */
    public static RichText quoteBodyRich(RichText content, String body) {
        String plainBody = body == null ? "" : body;
        if (content == null || content.isEmpty()) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        String full = content.getString();
        int open = full.indexOf("「引用");
        int close = open < 0 ? -1 : full.indexOf('」', open + 2);
        if (close < 0) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        int start = close + 1;
        while (start < full.length() && Character.isWhitespace(full.charAt(start))) {
            start++;
        }
        int end = full.length();
        while (end > start && Character.isWhitespace(full.charAt(end - 1))) {
            end--;
        }
        if (!full.substring(start, end).equals(plainBody)) {
            return RichText.literal(plainBody).linkifyUrls();
        }
        return content.slice(start, end).linkifyUrls();
    }

    /**
     * Slices the final decorated line into styled sender and content parts.
     * Only returns a result when the line parses as a player line for the
     * sender/profile names carried by {@code meta}; otherwise returns empty so
     * callers keep their system-safe fallback.
     */
    public static Optional<RichChatParts> sliceRichText(Component fullLine, SenderMeta meta) {
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

        RichText full = RichText.of(fullLine);
        String text = full.getString(); // visible text, no § control pairs
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, candidates);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        var pl = parsed.orElseThrow();
        // Whitespace-only gap = broadcast sentence ("Steve joined the game").
        if (MessagePresentation.isWhitespaceOnlyGap(text, pl.nameEnd(), pl.contentStart())) {
            return Optional.empty();
        }

        RichText sender;
        if (pl.nameStart() > 0 && text.charAt(pl.nameStart() - 1) == '<') {
            // Vanilla angle decoration is not part of the displayed sender:
            // "<Steve> hi" and "[VIP]<Steve> hi" must render as "Steve" and
            // "[VIP]Steve". Slice around the opening '<' (prefix decoration +
            // bare name) instead of synthesising a literal — the literal branch
            // dropped the line's run colours, rendering every sender in the
            // plain text colour (0.2.5 hunt).
            sender = RichText.concat(full.slice(0, pl.nameStart() - 1),
                    full.slice(pl.nameStart(), pl.labelEnd()));
        } else if (text.charAt(0) == '<' && pl.nameEnd() < text.length()
                && text.charAt(pl.nameEnd()) == '>') {
            // "<[Team]Steve> hi": slice out the wrapping brackets but keep the
            // inner runs' styles so team colours survive — e33chat's
            // cleanNameArea sliceStyled(1, len-1) parity (0.2.5 hunt).
            sender = full.slice(1, pl.labelEnd());
        } else {
            sender = full.slice(0, pl.labelEnd());
        }
        RichText content = full.slice(pl.contentStart(), text.length()).linkifyUrls();
        return Optional.of(new RichChatParts(sender, content));
    }
}
