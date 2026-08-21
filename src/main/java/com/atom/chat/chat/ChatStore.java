package com.atom.chat.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatStore {
    private static final ChatStore INSTANCE = new ChatStore();
    private final List<ChatMessage> messages = new ArrayList<>();

    public static ChatStore get() {
        return INSTANCE;
    }

    public synchronized void add(ChatMessage message) {
        messages.add(message);
        if (messages.size() > 500) {
            messages.remove(0);
        }
    }

    public synchronized List<ChatMessage> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(messages));
    }
}
