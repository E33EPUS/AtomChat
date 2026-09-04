package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatStore {
    private static final ChatStore INSTANCE = new ChatStore();
    private final List<ChatMessage> messages = new ArrayList<>();
    private static volatile boolean publicActive;
    private static volatile int publicUnread;

    public static ChatStore get() {
        return INSTANCE;
    }

    public synchronized void add(ChatMessage message) {
        messages.add(message);
        if (!message.isOwn() && !publicActive) {
            publicUnread++;
        }
        if (messages.size() > 500) {
            messages.remove(0);
        }
    }

    public synchronized List<ChatMessage> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }

    /** Marks the public channel as the currently viewed page. */
    public static synchronized void setPublicActive(boolean active) {
        publicActive = active;
        if (active) {
            publicUnread = 0;
        }
    }

    public static synchronized int publicUnread() {
        return publicUnread;
    }

    public static synchronized void markPublicRead() {
        publicUnread = 0;
    }

    public static synchronized void resetUnread() {
        publicUnread = 0;
    }

    /** Clears all public history and unread when leaving a server/world. */
    public static synchronized void reset() {
        INSTANCE.messages.clear();
        publicActive = false;
        publicUnread = 0;
    }
}
