package com.atom.chat.notification;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (NotificationBanner.enabled(type)
                && client != null && client.world != null
                && client.currentScreen == null) {
            String sender = message.getSenderName();
            if (sender == null || sender.isBlank()) {
                sender = message.getProfileName();
            }
            NotificationBanner.INSTANCE.enqueue(type, sender, message.getDisplayText());
        }
    }

    private static void playSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSoundMs < SOUND_GATE_MS) {
            return;
        }
        lastSoundMs = now;
        float volume = Math.max(0.0F, Math.min(1.0F, AtomChatConfig.get().notifyVolume));
        client.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25F, volume));
    }
}
