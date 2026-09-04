package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.SenderMeta;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.UUID;

/**
 * Channel-level capture.
 *
 * <p>Minecraft routes chat through {@link MessageHandler}; the decorated line
 * only reaches {@code ChatHud.addMessage} later, by which time structured
 * identity (UUID/profile/type) is gone. This mixin captures that structure and
 * hands it to {@link ChatHudMixin} through {@link MessageCapture}.
 *
 * <p>Unlike a naive HEAD-of-public-method capture, the injects are placed
 * immediately before the real {@code ChatHud.addMessage} calls. That keeps the
 * single-slot handoff correct under vanilla's chat-delay queue and under
 * messages that are filtered/blocked before reaching the HUD (no set without a
 * matching consume, so a stale meta cannot leak onto the next unrelated line).
 *
 * <p>Architecture ported from e33chat's ChatListenerMixin (MIT, same author),
 * trimmed to AtomChat's current needs: no whisper banners, no server templates.
 * Own-echo suppression happens at the HUD layer in {@link ChatHudMixin}.
 */
@Mixin(value = MessageHandler.class, priority = 500)
public class MessageHandlerMixin {
    /**
     * Signed chat. Two {@code ChatHud.addMessage} branches exist in
     * {@code processChatMessageInternal} (pass-through filter and filtered
     * text); this handler runs before either one, so it only fires when the
     * message is actually going to be displayed.
     */
    @Inject(method = "processChatMessageInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void atomchat$captureSignedBeforeAdd(MessageType.Parameters params, SignedMessage message,
                                                 Text decorated, GameProfile gameProfile,
                                                 boolean onlyShowSecure, Instant instant,
                                                 CallbackInfoReturnable<Boolean> cir) {
        UUID uuid = message.getSender() != null ? message.getSender() : gameProfile.getId();
        String profile = gameProfile.getName();
        String content = message.getContent().getString();
        MessageCapture.set(new SenderMeta(uuid, profile, profile, content, false,
                params.name(), message.getContent()));
    }

    /**
     * Profileless / disguised chat is decorated by {@code method_45745} just
     * before it reaches {@code ChatHud.addMessage}. Injecting there lets us run
     * the text-level parser on the decorated line instead of the raw body.
     */
    @Inject(method = "method_45745", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void atomchat$captureProfilelessBeforeAdd(MessageType.Parameters params, Text message,
                                                      Instant instant,
                                                      CallbackInfoReturnable<Boolean> cir) {
        String decorated = params.applyChatDecoration(message).getString();
        if (params.name() != null && !params.name().getString().isBlank()) {
            String display = params.name().getString();
            String profile = display;
            UUID uuid = ChatClassifier.resolveUuid(display);
            if (uuid != null) {
                var info = ChatClassifier.resolveOnlinePlayer(display);
                if (info != null) {
                    profile = info.getProfile().getName();
                }
            }
            MessageCapture.set(new SenderMeta(uuid, display, profile, null, false,
                    params.name(), message));
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(decorated);
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }

    /**
     * System/game messages. The inject sits on the non-overlay branch that
     * actually calls {@code ChatHud.addMessage}, after vanilla's
     * hide-matched-names and blocked-sender early returns.
     */
    @Inject(method = "onGameMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;addMessage(Lnet/minecraft/text/Text;)V"))
    private void atomchat$captureGameBeforeAdd(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) {
            return;
        }
        if (ChatClassifier.isVanillaBroadcast(message)) {
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(message.getString());
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }
}
