package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.util.UUID;

/**
 * Resolves a player's circular avatar as a square Skia image (the caller clips
 * it into a circle). The default implementation reads the real Minecraft skin;
 * future custom-avatar overlays implement this interface and take precedence.
 */
public interface PlayerAvatarSource {
    /**
     * @param uuid  real player UUID when known, else null
     * @param name  real profile name (never null for player cards)
     * @return face image, or null when unavailable
     */
    Image face(UUID uuid, String name);
}
