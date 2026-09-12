package com.atom.chat.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notification seam for @-mentions (0.1.11): the capture layer fires
 * {@link #onMention} for every public message that mentions the local player,
 * and future features (banner, sound, per-conversation counters) plug in as
 * observers without touching the pipeline again — the e33chat
 * {@code MessageEffectObserver} pattern.
 *
 * <p>The default registry is empty: with no observers registered the mention
 * still increments the unread-mention counter, nothing else happens.
 */
public final class MentionObserver {
    private MentionObserver() {
    }

    private static final List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();

    public interface Observer {
        void onMention(ChatMessage message);
    }

    public static void register(Observer observer) {
        if (observer != null) {
            OBSERVERS.add(observer);
        }
    }

    public static void unregister(Observer observer) {
        OBSERVERS.remove(observer);
    }

    public static void fire(ChatMessage message) {
        for (Observer observer : OBSERVERS) {
            try {
                observer.onMention(message);
            } catch (Throwable ignored) {
                // A broken observer must never break message capture.
            }
        }
    }
}
