package com.atom.chat.mixin;

import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.SenderMeta;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Channel-level capture. Minecraft routes chat through three MessageHandler
 * methods; the decorated line only reaches ChatHud.addMessage later, by which
 * time structured identity (UUID/profile/type) is gone. This mixin grabs the
 * structure early and hands it to {@link ChatHudMixin} through
 * {@link MessageCapture}.
 *
 * <p>Architecture ported from e33chat's ChatListenerMixin (MIT, same author),
 * trimmed to AtomChat's current needs: no echo suppression, no whisper banners,
 * no server templates.
 */
@Mixin(value = MessageHandler.class, priority = 500)
public class MessageHandlerMixin {
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void atomchat$onSignedChat(SignedMessage message, GameProfile gameProfile,
                                       MessageType.Parameters params, CallbackInfo ci) {
        UUID uuid = gameProfile.getId();
        String profile = gameProfile.getName();
        String content = message.getContent().getString();
        MessageCapture.set(new SenderMeta(uuid, profile, profile, content, false));
    }

    @Inject(method = "onProfilelessMessage", at = @At("HEAD"))
    private void atomchat$onDisguisedChat(Text message, MessageType.Parameters params, CallbackInfo ci) {
        String text = message.getString();
        if (params.name() != null) {
            String display = params.name().getString();
            String profile = display;
            UUID uuid = ChatClassifier.resolveUuid(display);
            if (uuid != null) {
                var info = ChatClassifier.resolveOnlinePlayer(display);
                if (info != null) {
                    profile = info.getProfile().getName();
                }
            }
            MessageCapture.set(new SenderMeta(uuid, display, profile, null, false));
            return;
        }
        SenderMeta parsed = ChatPipeline.tryParsePlayerLine(text);
        if (parsed != null) {
            MessageCapture.set(parsed);
        }
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void atomchat$onSystemChat(Text message, boolean overlay, CallbackInfo ci) {
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
