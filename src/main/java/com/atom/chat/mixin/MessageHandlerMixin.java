package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.PrivateChatParser;
import com.atom.chat.chat.SenderMeta;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Channel-level capture (NeoForge ChatListener variant).
 *
 * <p>Minecraft routes chat through {@link ChatListener}; the decorated line
 * only reaches {@code ChatComponent.addMessage} later, by which time structured
 * identity (UUID/profile/type) is gone. This mixin captures that structure and
 * hands it to {@link ChatHudMixin} through {@link MessageCapture}.
 *
 * <p>Architecture ported from e33chat's NeoForge ChatListenerMixin (MIT, same
 * author), trimmed to AtomChat's current needs.
 */
@Mixin(value = ChatListener.class, priority = 500)
public class MessageHandlerMixin {
    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void atomchat$capturePlayerChat(PlayerChatMessage message, GameProfile gameProfile,
                                            ChatType.Bound bound, CallbackInfo ci) {
        UUID uuid = message.sender();
        String profile = gameProfile.getName();
        // decoratedContent() keeps the server/plugin rich body (unsigned
        // content); signedContent() is the plain signing payload, and wrapping
        // it in Component.literal() dropped every style — coloured @mentions
        // arrived colourless on this leg. e33chat's Forge port does the same
        // (decoratedContent) and Fabric captures the equivalent getContent().
        Component contentComponent = message.decoratedContent();
        String content = contentComponent.getString();
        Component senderComponent = bound.name();
        MessageCapture.set(new SenderMeta(uuid, profile, profile, content, false,
                senderComponent, contentComponent));
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void atomchat$captureDisguised(Component message, ChatType.Bound bound, CallbackInfo ci) {
        Component decorated = bound.decorate(message);
        String decoratedStr = decorated.getString();
        Component senderComponent = bound.name();
        if (senderComponent != null && !senderComponent.getString().isBlank()) {
            String display = senderComponent.getString();
            String profile = display;
            UUID uuid = ChatClassifier.resolveUuid(display);
            if (uuid != null) {
                PlayerInfo info = ChatClassifier.resolveOnlinePlayer(display);
                if (info != null) {
                    profile = info.getProfile().getName();
                }
            }
            MessageCapture.set(new SenderMeta(uuid, display, profile, null, false,
                    senderComponent, message));
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(decoratedStr);
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void atomchat$captureSystem(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) {
            return;
        }
        SenderMeta privateMeta = PrivateChatParser.tryParse(message);
        if (privateMeta != null) {
            MessageCapture.set(privateMeta);
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
