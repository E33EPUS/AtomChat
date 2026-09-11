package com.atom.chat.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Pending outgoing public messages, so the server's relay of our own line can be
 * recognised even when the channel-level identity is gone.
 *
 * <p>Why this exists (2026-09-11 "{@code /team} prefix echo"): on NCR-style
 * servers our own message comes back through the identity-less text path with a
 * decorated name ({@code 123E33EPUS}), where "looks like me" is not proof. The
 * one thing that <em>is</em> proof is that we just sent exactly this text, so
 * the send path arms a record here and the capture path consumes it. Ported from
 * e33chat's {@code EchoTracker.pendingEchoes} (same 10s TTL, same
 * newest-first matching, same "never time-blind" rule: content must match).
 *
 * <p>Records expire: if the echo never arrives (another mod cancels it, or the
 * send was filtered server-side) a stale marker must not swallow a later
 * genuine message.
 */
public final class PublicEchoTracker {
    /** e33chat parity: echoes not consumed within 10s are stale. */
    public static final long TTL_MS = 10_000L;
    private static final int MAX_PENDING = 8;

    private static final Deque<Pending> PENDING = new ArrayDeque<>();

    private PublicEchoTracker() {
    }

    private record Pending(String text, long sentAtMs) {
    }

    /** Arms a record for a public message we just sent. */
    public static synchronized void markSent(String text) {
        purge(now());
        if (text == null || text.isBlank()) {
            return;
        }
        PENDING.addLast(new Pending(text, now()));
        while (PENDING.size() > MAX_PENDING) {
            PENDING.pollFirst();
        }
    }

    /**
     * @param content extracted message body of the arriving line (preferred)
     * @param rawLine the whole rendered line, used as a fallback when the
     *                extractor could not separate the body
     * @return true when the line is the relay of a message we just sent; the
     *         matching record is consumed either way it is found.
     */
    public static synchronized boolean consumeIfEcho(String content, String rawLine) {
        long now = now();
        purge(now);
        if (PENDING.isEmpty()) {
            return false;
        }
        // Newest first: two quick sends must resolve to the send that produced
        // this echo, otherwise a stale record would steal its quote/image state.
        for (Iterator<Pending> it = PENDING.descendingIterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (matches(p.text(), content, rawLine)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    static boolean matches(String sentText, String content, String rawLine) {
        if (sentText == null || sentText.isBlank()) {
            return false;
        }
        if (sentText.equals(content)) {
            return true;
        }
        // Relays that keep the message as-is (the server echoes exactly what we
        // sent, name decoration included in the same component).
        return rawLine != null && rawLine.endsWith(sentText);
    }

    /** Drops expired records; also used by tests. */
    public static synchronized void purge(long nowMs) {
        PENDING.removeIf(p -> nowMs - p.sentAtMs() > TTL_MS);
    }

    /** Test/diagnostic hook. */
    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    /** Clears every record (join/disconnect/reset). */
    public static synchronized void clear() {
        PENDING.clear();
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
