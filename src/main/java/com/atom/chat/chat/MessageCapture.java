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

    private static final ThreadLocal<SenderMeta> PENDING = new ThreadLocal<>();
    private static long pendingSetAtMs;

    private MessageCapture() {
    }

    public static void set(SenderMeta meta) {
        if (meta == null) {
            return;
        }
        PENDING.set(meta);
        pendingSetAtMs = System.currentTimeMillis();
    }

    /** @return the pending meta when fresh, otherwise clears and returns null. */
    public static SenderMeta consume() {
        SenderMeta meta = PENDING.get();
        PENDING.remove();
        if (meta == null) {
            return null;
        }
        if (System.currentTimeMillis() - pendingSetAtMs > TTL_MS) {
            return null;
        }
        return meta;
    }
}
