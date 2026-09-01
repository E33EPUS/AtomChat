package com.atom.chat.ui;

/**
 * Single source of truth for transition timing.
 *
 * Two rules every transition must obey:
 *
 * 1. A duration here is the *total* time to reach the target, not a time
 *    constant. The old per-frame formula {@code v += (target - v) * dt / D}
 *    decays asymptotically, so a 120ms hover actually stayed visible for
 *    ~550ms and never reached 0 — that is the sticky/laggy feel we are fixing.
 * 2. Transitions snap to the target on the frame that gets there, so state
 *    (hover highlight, scrollbar alpha, popup fade) always lands exactly on
 *    0 or 1 instead of hovering just above it forever.
 */
public final class UiMotion {
    /** Panel open/close: slide + fade. */
    public static final long PANEL_MS = 150;
    /** New message entry. */
    public static final long MESSAGE_MS = 140;
    /** Snap back to the bottom after sending. */
    public static final long SCROLL_SNAP_MS = 110;
    /** Wheel scroll glide. */
    public static final long SCROLL_WHEEL_MS = 180;
    /** Button hover / press tint. */
    public static final long HOVER_MS = 90;
    /** Scrollbar fade in/out. */
    public static final long SCROLLBAR_FADE_MS = 140;
    /** Scrollbar hover emphasis. */
    public static final long SCROLLBAR_EMPHASIS_MS = 100;
    /** Emoji panel / context menu pop. */
    public static final long POPUP_MS = 110;
    /** Input bar growing/shrinking by one line. */
    public static final long INPUT_GROW_MS = 110;

    private UiMotion() {
    }

    /**
     * Moves {@code value} toward {@code target} by the fraction of
     * {@code durationMs} that {@code elapsedMs} covers, and returns
     * {@code target} once the remaining distance fits in this frame's step.
     *
     * <p>Frame-rate independent: a 60fps and a 200fps client clear the same
     * highlight in the same wall-clock time, and both end at exactly 0.</p>
     */
    public static float approach(float value, float target, float elapsedMs, long durationMs) {
        if (durationMs <= 0L) {
            return target;
        }
        float step = elapsedMs / (float) durationMs;
        if (step >= 1.0F) {
            return target;
        }
        float diff = target - value;
        if (Math.abs(diff) <= step) {
            return target;
        }
        return value + Math.signum(diff) * step;
    }
}
