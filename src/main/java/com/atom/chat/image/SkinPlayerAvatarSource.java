package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.util.UUID;

/** Skin-backed implementation: face + hat sampled from the 64x64 player skin. */
public final class SkinPlayerAvatarSource implements PlayerAvatarSource {
    public static final SkinPlayerAvatarSource INSTANCE = new SkinPlayerAvatarSource();

    private SkinPlayerAvatarSource() {
    }

    @Override
    public Image face(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return AvatarRenderer.face(SkinResolver.getSkin(uuid, name));
    }
}
