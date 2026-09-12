package com.atom.chat.notification;

/**
 * Pure de-duplication gate for the notification cue.
 *
 * <p>{@link #NEVER} is the "no cue played yet" sentinel. The elapsed-time
 * subtraction must be guarded against it: {@code now - Long.MIN_VALUE} wraps to
 * a negative long, so a bare comparison would report "inside the window" and
 * suppress the first cue forever.
 */
public final class NotificationSoundGate {
    /** Minimum gap between two notification cues, in milliseconds. */
    public static final long MIN_INTERVAL_MS = 2000L;
    /** Sentinel for "this cue has never played". */
    public static final long NEVER = Long.MIN_VALUE;

    private NotificationSoundGate() {
    }

    /**
     * @return true when a cue may play at {@code nowMs} given the previous
     *         play time, false while still inside the de-duplication window.
     */
    public static boolean shouldPlay(long nowMs, long lastPlayedMs) {
        return lastPlayedMs == NEVER || nowMs - lastPlayedMs >= MIN_INTERVAL_MS;
    }
}
