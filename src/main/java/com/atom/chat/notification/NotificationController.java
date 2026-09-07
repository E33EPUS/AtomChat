package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.screen.AtomChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Turns capture-side events (mentions, quote replies, incoming whispers) into
 * notification side effects. The banner is Skia-rendered only while no screen
 * is open; the sound always respects its own toggle and a 2s dedupe gate.
 */
public final class NotificationController {
    private static final long SOUND_GATE_MS = 2000L;
    private static long lastSoundMs = Long.MIN_VALUE;

    private NotificationController() {
    }

    public static void onMention(ChatMessage message) {
        fire(NotificationBanner.Type.MENTION, message);
    }

    public static void onQuote(ChatMessage message) {
        fire(NotificationBanner.Type.QUOTE, message);
    }

    public static void onWhisper(ChatMessage message) {
        fire(NotificationBanner.Type.WHISPER, message);
    }

    private static void fire(NotificationBanner.Type type, ChatMessage message) {
        if (message == null || message.isOwn()) {
            return;
        }
        if (NotificationBanner.soundEnabled(type)) {
            playSound();
        }
        Minecraft client = Minecraft.getInstance();
        boolean canShowBanner = NotificationBanner.enabled(type)
                && client != null && client.level != null
                && (client.screen == null || client.screen instanceof AtomChatScreen);
        if (canShowBanner) {
            String sender = message.getSenderName();
            if (sender == null || sender.isBlank()) {
                sender = message.getProfileName();
            }
            NotificationBanner.INSTANCE.enqueue(type, sender, message.getDisplayText());
        }
    }

    private static void playSound() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSoundMs < SOUND_GATE_MS) {
            return;
        }
        lastSoundMs = now;
        float volume = Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().notifyVolume));
        // SimpleSoundInstance.forUI(SoundEvent, volume, pitch) — the second
        // float is volume. Use 1.0 pitch so the notification is clearly audible.
        client.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, volume, 1.0F));
    }
}
