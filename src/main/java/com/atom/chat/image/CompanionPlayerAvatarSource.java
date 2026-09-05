package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.util.UUID;

/**
 * Server-companion-backed avatar source (0.1.10): serves custom avatars
 * pushed by other players through the companion protocol, lazily requested
 * and cached by {@code AvatarCompanionClient}. Falls through to the skin
 * when the companion is absent or the player has no custom avatar.
 */
public final class CompanionPlayerAvatarSource implements PlayerAvatarSource {
    public static final CompanionPlayerAvatarSource INSTANCE = new CompanionPlayerAvatarSource();

    private CompanionPlayerAvatarSource() {
    }

    @Override
    public Image face(UUID uuid, String name) {
        return uuid != null ? com.atom.chat.net.AvatarCompanionClient.currentAvatar(uuid) : null;
    }
}
