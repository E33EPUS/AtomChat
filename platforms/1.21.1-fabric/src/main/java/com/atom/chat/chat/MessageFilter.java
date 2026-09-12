package com.atom.chat.chat;

/**
 * View filter for the public world feed, cycled by the header filter button.
 * Pure logic: the cycle order and which messages a mode accepts. It never
 * touches storage, unread counters or previews — the filtered list is only
 * what the world-chat list draws.
 */
public enum MessageFilter {
    ALL, SYSTEM, PLAYERS;

    /** ALL -> SYSTEM -> PLAYERS -> ALL. */
    public MessageFilter next() {
        return switch (this) {
            case ALL -> SYSTEM;
            case SYSTEM -> PLAYERS;
            case PLAYERS -> ALL;
        };
    }

    public boolean accepts(ChatMessage message) {
        return switch (this) {
            case ALL -> true;
            case SYSTEM -> message.isSystem();
            case PLAYERS -> !message.isSystem();
        };
    }

    /** True when this mode hides anything (drives the accent-tinted button state). */
    public boolean isFiltering() {
        return this != ALL;
    }
}
