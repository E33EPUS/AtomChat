package com.atom.chat.image;

import com.atom.chat.avatar.AvatarImage;
import com.atom.chat.avatar.AvatarStore;
import io.github.humbleui.skija.Image;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Local custom-avatar source: applies {@link AvatarStore} to the local player
 * only. Every other player keeps falling through to the skin source, and when
 * no custom avatar is set the local player does too.
 *
 * <p>Scope (grilled 2026-09-05): local-only for now. Cross-client visibility
 * is a future server-companion feature; until it exists the mod silently
 * degrades to skins, per the e33chat philosophy.
 */
public final class OwnPlayerAvatarSource implements PlayerAvatarSource {
    public static final OwnPlayerAvatarSource INSTANCE = new OwnPlayerAvatarSource();

    private OwnPlayerAvatarSource() {
    }

    private static UUID ownUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getUuid() : null;
    }

    @Override
    public Image face(UUID uuid, String name) {
        UUID own = ownUuid();
        if (uuid == null || own == null || !uuid.equals(own)) {
            return null;
        }
        AvatarStore store = AvatarHolder.STORE;
        return store != null ? AvatarImage.current(store.current()) : null;
    }

    /** Set once by the screen that owns the store instance; read-only after. */
    static final class AvatarHolder {
        static volatile AvatarStore STORE;

        private AvatarHolder() {
        }
    }

    /** Wires the store created by the screen; idempotent. */
    public static void attach(AvatarStore store) {
        AvatarHolder.STORE = store;
    }
}
