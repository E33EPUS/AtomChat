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
        if (uuid == null || uuid.equals(OwnPlayerAvatarSource.ownUuid())) {
            // The local player's avatar is decided by the local file alone.
            // Without this guard, "use skin" clears the local file but the
            // companion hands the previously uploaded copy straight back —
            // the avatar then survives even a game restart.
            return null;
        }
        return com.atom.chat.net.AvatarCompanionClient.currentAvatar(uuid);
    }
}
