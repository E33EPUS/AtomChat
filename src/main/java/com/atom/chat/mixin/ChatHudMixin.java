package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.SenderMeta;
import com.atom.chat.text.ChatTextRewriter;
import com.atom.chat.text.RichText;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

        Text rewritten = ChatTextRewriter.rewrite(message);
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
        if (meta == null) {
            // No channel-level identity: translation-key system lines are
            // authoritative and must stay system even if their rendered text
            // happens to look like a player line. Other routes may only claim
            // a player when the text parser resolves the line to an online
            // player; otherwise they are system messages.
            if (ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM) {
                addSystemMessage(message);
                return;
            }
            SenderMeta parsed = ChatPipeline.tryParsePlayerLine(raw);
            if (parsed == null) {
                addSystemMessage(message);
                return;
            }
            if (isOwn(parsed, raw, client)) {
                // Own message echo: already added locally by AtomChatScreen.
                return;
            }
            String displayName = parsed.senderName() != null ? parsed.senderName() : parsed.profileName();
            var sliced = ChatPipeline.sliceRichText(message, parsed);
            if (sliced.isPresent()) {
                ChatStore.get().add(new ChatMessage(message, false, parsed.system(), null, null,
                        parsed.senderUuid(), displayName, parsed.profileName(), parsed.contentText(),
                        sliced.get().sender(), sliced.get().content().linkifyUrls()));
            } else {
                String fallbackContent = parsed.contentText() != null ? parsed.contentText() : raw;
                ChatStore.get().add(new ChatMessage(message, false, parsed.system(), null, null,
                        parsed.senderUuid(), displayName, parsed.profileName(), parsed.contentText(),
                        RichText.empty(), RichText.literal(fallbackContent).linkifyUrls()));
            }
            return;
        }

        boolean own = isOwn(meta, raw, client);
        if (own) {
            // Own message echo: already added locally by AtomChatScreen.
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
        } else {
            senderRich = meta.senderComponent() != null
                    ? RichText.of(meta.senderComponent())
                    : RichText.empty();
            contentRich = meta.contentComponent() != null
                    ? RichText.of(meta.contentComponent()).linkifyUrls()
                    : RichText.literal(content);
        }
        ChatStore.get().add(new ChatMessage(message, false, meta.system(), null, null,
                meta.senderUuid(), displayName, meta.profileName(), content,
                senderRich, contentRich));
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
