package com.atom.chat.mixin;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.ChatTemplates;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatParser;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.chat.QuoteParser;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.SeenPlayers;
import com.atom.chat.chat.SenderMeta;
import com.atom.chat.chat.WhisperTextParser;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.text.ChatTextRewriter;
import com.atom.chat.text.RichText;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatHud.class, priority = 500)
public class ChatHudMixin {
    @Unique
    private boolean atomchat$reposting;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void atomchat$hideVanillaChatHud(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof AtomChatScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void atomchat$captureMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        if (atomchat$reposting) {
            return;
        }

        // Store the original message first; the vanilla HUD copy is rewritten
        // below only after capture has already decided what belongs in ChatStore.
        atomchat$captureAndStore(message, signatureData, indicator);

        MinecraftClient client = MinecraftClient.getInstance();
        String ownName = client.player != null ? client.player.getName().getString() : null;
        Text rewritten = ChatTextRewriter.rewritePrivate(message, ownName);
        if (rewritten != null) {
            // Keep compacting image codes / quote prefixes inside the rewritten
            // private line (e.g. "<name>[Whisper] [Quote] body").
            Text compacted = ChatTextRewriter.rewrite(rewritten);
            if (compacted != null) {
                rewritten = compacted;
            }
        } else {
            rewritten = ChatTextRewriter.rewrite(message);
        }
        if (rewritten != null) {
            ci.cancel();
            atomchat$reposting = true;
            try {
                ((ChatHud) (Object) this).addMessage(rewritten, signatureData, indicator);
            } finally {
                atomchat$reposting = false;
            }
        }
    }

    @Unique
    private void atomchat$captureAndStore(Text message, MessageSignatureData signatureData, MessageIndicator indicator) {
        String raw = message.getString();
        MinecraftClient client = MinecraftClient.getInstance();

        SenderMeta meta = MessageCapture.consume();
        // Machine-to-machine chat protocols must never be claimed as player chat
        // or suppressed as an own echo. e33chat routes them to the system channel
        // ("宁可不杀不可错杀"): when in doubt, let the line through.
        if (ChatClassifier.isXaeroWaypointData(raw)
                || (meta != null && meta.contentText() != null
                && ChatClassifier.isXaeroWaypointData(meta.contentText()))) {
            addSystemMessage(message);
            return;
        }
        if (meta != null && meta.whisper()) {
            atomchat$routePrivate(message, meta, client);
            return;
        }
        if (meta == null) {
            // No channel-level identity. Private /msg lines are deterministic by
            // translation key even when the MessageHandler capture missed them.
            SenderMeta fallbackPrivate = PrivateChatParser.tryParse(message);
            if (fallbackPrivate != null) {
                atomchat$routePrivate(message, fallbackPrivate, client);
                return;
            }
            // No channel-level identity: translation-key system lines are
            // authoritative and must stay system even if their rendered text
            // happens to look like a player line. Other routes may only claim
            // a player when the text parser resolves the line to an online
            // player; otherwise they are system messages.
            if (ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM) {
                addSystemMessage(message);
                return;
            }
            // Text-layer whisper fallback (plugin-reformatted /msg, bot relays).
            // Must run BEFORE the player-line guard: its separator skipping
            // would mis-claim "[Steve -> me] hi" as a public bubble from Steve.
            if (atomchat$tryTextWhisper(raw, message, client)) {
                return;
            }
            SenderMeta parsed = ChatPipeline.tryParsePlayerLine(raw);
            if (parsed == null) {
                // Guard failed: user-configured templates get the last claim
                // before the line degrades to a gray system bubble.
                parsed = atomchat$tryChatTemplate(raw);
            }
            if (parsed == null) {
                atomchat$logParseMiss(raw);
                addSystemMessage(message);
                return;
            }
            // Deliberately no own-echo suppression here. This branch has no
            // channel-level identity, so parsing the line as "looks like me" is
            // not proof it is our own echo — mods (Xaero path analysis, server
            // waypoints, etc.) can emit own-name-shaped system lines. Suppressing
            // them makes real messages vanish from the panel; showing a possible
            // duplicate is the safer failure per e33chat's "宁可不杀" rule.
            String displayName = parsed.senderName() != null ? parsed.senderName() : parsed.profileName();
            if (AtomChatConfig.get().hideBlockedMessages
                    && displayName != null && BlockList.isBlocked(displayName)) {
                return;
            }
            String parsedContent = parsed.contentText() != null ? parsed.contentText() : raw;
            QuoteParser.Quote quote = atomchat$quoteOf(parsedContent);
            String body = quote != null ? quote.body() : parsedContent;
            var sliced = ChatPipeline.sliceRichText(message, parsed);
            if (sliced.isPresent()) {
                RichText richContent = quote != null
                        ? RichText.literal(body).linkifyUrls()
                        : sliced.get().content().linkifyUrls();
                ChatStore.get().add(new ChatMessage(message, false, parsed.system(),
                        quote != null ? quote.quoteName() : null,
                        quote != null ? quote.quoteText() : null,
                        parsed.senderUuid(), displayName, parsed.profileName(), body,
                        sliced.get().sender(), richContent));
            } else {
                ChatStore.get().add(new ChatMessage(message, false, parsed.system(),
                        quote != null ? quote.quoteName() : null,
                        quote != null ? quote.quoteText() : null,
                        parsed.senderUuid(), displayName, parsed.profileName(), body,
                        RichText.empty(), RichText.literal(body).linkifyUrls()));
            }
            SeenPlayers.remember(parsed.senderUuid(), parsed.profileName(), displayName);
            return;
        }

        boolean own = isOwn(meta, raw, client);
        if (own) {
            // Own message echo: already added locally by AtomChatScreen.
            return;
        }
        String blockName = meta.profileName() != null ? meta.profileName() : meta.senderName();
        if (AtomChatConfig.get().hideBlockedMessages
                && blockName != null && BlockList.isBlocked(blockName)) {
            return;
        }

        String content = ChatPipeline.extractContent(raw, meta);
        String displayName = ChatPipeline.decoratedDisplayName(raw, meta);
        if (displayName == null) {
            displayName = meta.senderName();
        }
        RichText senderRich;
        RichText contentRich;
        if (meta.senderComponent() == null && meta.contentComponent() == null) {
            var sliced = ChatPipeline.sliceRichText(message, meta);
            if (sliced.isPresent()) {
                senderRich = sliced.get().sender();
                contentRich = sliced.get().content().linkifyUrls();
            } else {
                senderRich = RichText.empty();
                contentRich = RichText.literal(content);
            }
            SeenPlayers.remember(meta.senderUuid(),
                    meta.profileName() != null ? meta.profileName() : displayName, displayName);
        } else {
            senderRich = meta.senderComponent() != null
                    ? RichText.of(meta.senderComponent())
                    : RichText.empty();
            contentRich = meta.contentComponent() != null
                    ? RichText.of(meta.contentComponent()).linkifyUrls()
                    : RichText.literal(content);
        }
        QuoteParser.Quote quote = atomchat$quoteOf(content);
        String body = quote != null ? quote.body() : content;
        if (quote != null) {
            contentRich = RichText.literal(body).linkifyUrls();
        }
        ChatStore.get().add(new ChatMessage(message, false, meta.system(),
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                meta.senderUuid(), displayName, meta.profileName(), body,
                senderRich, contentRich));
    }

    /**
     * G1 text-layer whisper fallback + user-configured whisper templates.
     * Returns true when the line was claimed (routed into the private panel).
     */
    @Unique
    private static boolean atomchat$tryTextWhisper(String raw, Text message, MinecraftClient client) {
        String ownName = client.player != null ? client.player.getName().getString() : null;
        WhisperTextParser.WhisperHit hit = WhisperTextParser.tryParse(raw, ownName);
        if (hit == null) {
            var cfg = AtomChatConfig.get();
            if (cfg.whisperTemplates.isEmpty()) {
                return false;
            }
            var match = ChatTemplates.matchWhisper(
                    raw, cfg.whisperTemplates, ChatPipeline.onlineNameCandidates());
            if (match.isEmpty()) {
                return false;
            }
            var tm = match.get();
            hit = new WhisperTextParser.WhisperHit(true, tm.displayLabel(), tm.content());
        }
        PlayerListEntry info = ChatClassifier.resolveOnlinePlayer(hit.partnerDisplay());
        String profile = info != null ? info.getProfile().getName() : hit.partnerDisplay();
        UUID uuid = info != null ? info.getProfile().getId()
                : ChatClassifier.resolveUuid(hit.partnerDisplay());
        SenderMeta whisperMeta;
        if (hit.incoming()) {
            whisperMeta = new SenderMeta(uuid, hit.partnerDisplay(), profile,
                    hit.content(), false, true, profile, null, null);
        } else {
            // Outgoing echo: for sends made from the panel the local bubble
            // already exists — drop only when the tracker confirms, otherwise
            // store the line as our outgoing message (same semantics as the
            // vanilla-key outgoing branch in PrivateChatParser).
            PlayerRef partner = PlayerRef.of(uuid, profile);
            if (PrivateEchoTracker.consumeIfMatch(partner)) {
                return true;
            }
            if (client.player == null) {
                return false;
            }
            String own = client.player.getName().getString();
            whisperMeta = new SenderMeta(client.player.getUuid(), own, own,
                    hit.content(), false, true, profile, null, null);
        }
        atomchat$routePrivate(message, whisperMeta, client);
        return true;
    }

    /** Client-side template claim for chat lines the guards cannot express. */
    @Unique
    private static SenderMeta atomchat$tryChatTemplate(String raw) {
        var cfg = AtomChatConfig.get();
        if (cfg.chatTemplates.isEmpty()) {
            return null;
        }
        var match = ChatTemplates.match(
                raw, cfg.chatTemplates, ChatPipeline.onlineNameCandidates());
        if (match.isEmpty()) {
            return null;
        }
        var tm = match.get();
        UUID uuid = ChatClassifier.resolveUuid(tm.playerName());
        return new SenderMeta(uuid, tm.displayLabel(), tm.playerName(), tm.content(), false);
    }

    /**
     * G4 (e33chat parity) miss diagnostics: when the whole claim chain fails
     * and templates are configured, log the raw line so real-server formats
     * that slip through can be fixed from the log alone. Gated behind the
     * debug flag to keep public-chat log volume at zero.
     */
    @Unique
    private static void atomchat$logParseMiss(String raw) {
        var cfg = AtomChatConfig.get();
        if (!cfg.debug) {
            return;
        }
        boolean hasTemplates = !cfg.chatTemplates.isEmpty() || !cfg.whisperTemplates.isEmpty();
        AtomChat.LOGGER.info("[atomchat] chat parse miss (guard{} matched nothing): {}",
                hasTemplates ? "+templates" : "", raw);
    }

    @Unique
    private static QuoteParser.Quote atomchat$quoteOf(String content) {
        return content == null ? null : QuoteParser.parse(content);
    }

    @Unique
    private static void atomchat$routePrivate(Text message, SenderMeta meta, MinecraftClient client) {
        // Suppressed outgoing echo sentinel: the local bubble already exists.
        if (meta.system()) {
            return;
        }
        String partnerName = meta.whisperPartner();
        if (partnerName == null) {
            return;
        }
        boolean own = isOwn(meta, message.getString(), client);
        UUID partnerUuid = own ? null : meta.senderUuid();
        PlayerRef partner = PlayerRef.of(partnerUuid, partnerName);

        String content = meta.contentText() != null
                ? meta.contentText()
                : ChatPipeline.extractContent(message.getString(), meta);
        String displayName = meta.profileName() != null ? meta.profileName() : meta.senderName();
        if (displayName == null) {
            displayName = partnerName;
        }
        QuoteParser.Quote quote = atomchat$quoteOf(content);
        String body = quote != null ? quote.body() : content;
        RichText senderRich = meta.senderComponent() != null
                ? RichText.of(meta.senderComponent()).stripInteractions()
                : RichText.literal(displayName);
        RichText contentRich = quote != null
                ? RichText.literal(body).linkifyUrls()
                : meta.contentComponent() != null
                        ? RichText.of(meta.contentComponent()).linkifyUrls()
                        : RichText.literal(body != null ? body : message.getString()).linkifyUrls();
        ChatMessage privateMessage = new ChatMessage(message, own, false,
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                own ? meta.senderUuid() : meta.senderUuid(), displayName,
                own ? meta.profileName() : meta.profileName(), body, senderRich, contentRich);
        if (own) {
            PrivateChatStore.addOutgoing(partner, privateMessage);
        } else if (!BlockList.isBlocked(partner)) {
            PrivateChatStore.addIncoming(partner, privateMessage);
            SeenPlayers.remember(partnerUuid, meta.profileName(), displayName);
        }
    }

    private static void addSystemMessage(Text message) {
        ChatStore.get().add(new ChatMessage(message, false, true, null, null,
                null, null, null, null, RichText.empty(), RichText.of(message).linkifyUrls()));
    }

    private static boolean isOwn(SenderMeta meta, String raw, MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        if (meta.senderUuid() != null) {
            return meta.senderUuid().equals(client.player.getUuid());
        }
        String ownProfile = client.player.getName().getString();
        if (meta.profileName() != null && meta.profileName().equals(ownProfile)) {
            return true;
        }
        if (meta.senderName() != null && meta.senderName().equals(ownProfile)) {
            return true;
        }
        FallbackIdentity fb = parseAngleFallback(raw);
        return fb != null && fb.name().equals(ownProfile);
    }

    private record FallbackIdentity(String name) {
    }

    private static FallbackIdentity parseAngleFallback(String raw) {
        if (raw != null && raw.startsWith("<")) {
            int end = raw.indexOf("> ");
            if (end > 0) {
                String sender = raw.substring(1, end);
                if (!sender.isBlank()) {
                    return new FallbackIdentity(sender);
                }
            }
        }
        return null;
    }
}
