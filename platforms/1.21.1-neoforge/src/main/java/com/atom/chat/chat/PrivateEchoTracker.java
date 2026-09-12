package com.atom.chat.chat;

/**
 * Short-lived marker that an outgoing private message was already rendered
 * locally. When the server echoes {@code /msg} back through
 * {@code commands.message.display.outgoing}, the capture pipeline consumes the
 * marker and drops that echo so the conversation never gets a duplicate bubble.
 *
 * <p>TTL mirrors e33chat: if the echo never arrives (offline target, cancelled
 * command, another mod swallowing it), the stale marker must not suppress a
 * later genuine outgoing echo from a different message.
 */
public final class PrivateEchoTracker {
    public static final long TTL_MS = 5_000L;

    private static volatile Pending pending;

    private PrivateEchoTracker() {
    }

    private record Pending(PlayerRef partner, long sentAtMs) {
    }

    /** Arm suppression for the next outgoing echo to this partner. */
    public static synchronized void markOutgoing(PlayerRef partner) {
        if (partner == null) {
            return;
        }
        pending = new Pending(partner, System.currentTimeMillis());
    }

    /**
     * @return true when this outgoing echo is the duplicate of a locally added
     *         bubble and should be dropped.
     */
    public static synchronized boolean consumeIfMatch(PlayerRef partner) {
        Pending p = pending;
        if (p == null || partner == null) {
            return false;
        }
        pending = null;
        return System.currentTimeMillis() - p.sentAtMs <= TTL_MS && p.partner.equals(partner);
    }

    /** Clears any armed marker (used on disconnect/reset). */
    public static synchronized void clear() {
        pending = null;
    }
}
