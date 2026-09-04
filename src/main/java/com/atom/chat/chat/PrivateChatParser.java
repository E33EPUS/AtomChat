package com.atom.chat.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.UUID;

/**
 * Vanilla /msg key parser shared by the MessageHandler capture mixin and the
 * ChatHud fallback. Returns structured SenderMeta for
 * commands.message.display.incoming/outgoing, or null for any other message.
 */
public final class PrivateChatParser {
    private PrivateChatParser() {
    }

    public static SenderMeta tryParse(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent tc)) {
            return null;
        }
        String key = tc.getKey();
        Object[] args = tc.getArgs();
        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Text name = asText(args[0]);
            Text content = asText(args[1]);
            String display = clean(name.getString());
            PlayerListEntry info = display == null ? null : ChatClassifier.resolveOnlinePlayer(display);
            String profile = info != null ? info.getProfile().getName() : display;
            UUID uuid = info != null ? info.getProfile().getId() : null;
            return new SenderMeta(uuid, display, profile, content.getString(), false, true,
                    profile, name, content);
        }
        if (key.equals("commands.message.display.outgoing") && args.length >= 2) {
            Text partnerText = asText(args[0]);
            Text content = asText(args[1]);
            String display = clean(partnerText.getString());
            if (display == null) {
                return null;
            }
            PlayerListEntry info = ChatClassifier.resolveOnlinePlayer(display);
            String partner = info != null ? info.getProfile().getName() : display;
            if (PrivateEchoTracker.consumeIfMatch(PlayerRef.of(null, partner))) {
                // Local bubble already exists; tell ChatHudMixin to drop it.
                return new SenderMeta(null, null, null, null, true, true, partner,
                        null, null);
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return null;
            }
            String own = client.player.getName().getString();
            UUID ownUuid = client.player.getUuid();
            return new SenderMeta(ownUuid, own, own, content.getString(), false, true,
                    partner, Text.literal(own), content);
        }
        return null;
    }

    private static Text asText(Object arg) {
        return arg instanceof Text text ? text : Text.literal(String.valueOf(arg));
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "").trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
