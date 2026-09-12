package com.atom.chat.ui;

import com.atom.chat.config.AtomChatConfig;

/**
 * Central gate for decorative motion.
 *
 * <p>The settings switch is deliberately <em>not</em> a blanket freeze. Motion
 * that answers the pointer (hover tint, wheel glide, scrollbar drag) stays on
 * so the UI never feels dead; motion that only ornaments a state change
 * (message entrance, page push, panel open, poke shake, scroll snapping) is
 * what gets collapsed to zero.</p>
 *
 * <p>Every duration in this UI must be read through {@link #ms(long)} rather
 * than straight from {@link UiMotion}, so a single config flag governs the
 * whole surface instead of each call site growing its own condition.</p>
 */
public final class Animations {
    private Animations() {
    }

    /** Whether decorative motion is allowed at all. */
    public static boolean enabled() {
        return AtomChatConfig.get().animationEnabled;
    }

    /**
     * Returns {@code durationMs}, or 0 when decorative motion is off. A zero
     * duration must be honoured as "land on the target this frame", never as a
     * division by zero.
     */
    public static long ms(long durationMs) {
        return enabled() ? durationMs : 0L;
    }

    /**
     * Whether the QQ-style message entrance should play. Subordinate to the
     * master switch: turning decorative motion off also stops entrances even
     * if this specific option is still on.
     */
    public static boolean messageEntry() {
        return enabled() && AtomChatConfig.get().messageEntryAnimation;
    }

    /** Whether double-clicking another player's avatar pokes them. */
    public static boolean avatarPoke() {
        return AtomChatConfig.get().avatarPokeEnabled;
    }
}
