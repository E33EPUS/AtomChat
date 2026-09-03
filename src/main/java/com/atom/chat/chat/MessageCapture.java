package com.atom.chat.chat;

/**
 * Short-lived handoff between the MessageHandler channel mixins and
 * ChatHud.addMessage. The channel layer knows structured identity (UUID,
 * profile name, decorated name); the HUD layer sees the final decorated line.
 * A stale meta must never leak onto an unrelated later message, so it expires
 * after {@link #TTL_MS}.
 */
public final class MessageCapture {
    /** Same value as e33chat: if addMessage is cancelled/never runs, drop the meta. */
    public static final long TTL_MS = 2_000L;

    /**
     * Timestamp travels with the meta (not in a shared static field): if capture
     * ever happens on a different thread than consume, each thread must still
     * age only its own handoff instead of borrowing another thread's clock.
     */
    private record Pending(SenderMeta meta, long setAtMs) {
    }

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    private MessageCapture() {
    }

    public static void set(SenderMeta meta) {
        if (meta == null) {
            return;
        }
        PENDING.set(new Pending(meta, System.currentTimeMillis()));
    }

    /** @return the pending meta when fresh, otherwise clears and returns null. */
    public static SenderMeta consume() {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (pending == null) {
            return null;
        }
        if (System.currentTimeMillis() - pending.setAtMs > TTL_MS) {
            return null;
        }
        return pending.meta;
    }
}
