package com.atom.chat.mixin;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.SenderMeta;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatHud.class, priority = 500)
public class ChatHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void atomchat$hideVanillaChatHud(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof AtomChatScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void atomchat$captureMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        String raw = message.getString();
        MinecraftClient client = MinecraftClient.getInstance();

        SenderMeta meta = MessageCapture.consume();
        if (meta == null) {
            // No channel-level identity: keep the old tolerant fallback so a
            // mod/server path we do not intercept still shows something sane.
            FallbackIdentity fb = parseAngleFallback(raw);
            if (fb != null && client.player != null && fb.name().equals(client.player.getName().getString())) {
                // Own message echo: already added locally by AtomChatScreen.
                return;
            }
            ChatStore.get().add(new ChatMessage(message, false, fb == null, null, null,
                    null,
                    fb != null ? fb.name() : null,
                    fb != null ? fb.name() : null,
                    null));
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
        ChatStore.get().add(new ChatMessage(message, false, meta.system(), null, null,
                meta.senderUuid(), displayName, meta.profileName(), content));
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
