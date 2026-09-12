package com.atom.chat.ui;

import com.atom.chat.render.Animator;
import com.atom.chat.render.Easing;
import com.atom.chat.render.SkiaDraw;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;

/**
 * One toggle switch: the control behind every settings row.
 *
 * <p>The knob owns an {@link Animator} rather than a plain eased value so a
 * mid-flight reversal retargets from the current position instead of snapping
 * back to an end — clicking twice quickly reads as one continuous slide.</p>
 *
 * <p>Geometry comes entirely from {@link UiTokens}: knob diameter is the track
 * height minus the two insets, so the travel is
 * {@code SWITCH_W - SWITCH_KNOB - 2 * SWITCH_INSET}.</p>
 */
public final class ToggleSwitch {
    private static final int TRACK_OFF = Color.makeARGB(70, 255, 255, 255);
    private static final int KNOB = Color.makeARGB(255, 255, 255, 255);

    private final Animator anim = new Animator(Easing::easeOutCubic);

    public ToggleSwitch() {
        anim.setValue(0.0F);
    }

    /** Jumps to the target with no animation (used when a page is first shown). */
    public void snapTo(boolean on) {
        anim.setValue(on ? 1.0F : 0.0F);
    }

    /**
     * Advances the knob. Safe to call every frame: {@code animateTo} is a no-op
     * while the target is unchanged, so an idle switch costs one comparison.
     */
    public void update(float dtMs, boolean on) {
        anim.animateTo(UiMotion.TOGGLE_MS, on ? 1.0F : 0.0F);
        anim.update(dtMs);
    }

    /**
     * @param x left edge of the track
     * @param y top edge of the track
     * @param accent the on-state track colour
     */
    public void render(Canvas canvas, float x, float y, int accent) {
        float p = Math.max(0.0F, Math.min(1.0F, anim.getValue()));
        SkiaDraw.drawRoundedRect(canvas, x, y, UiTokens.SWITCH_W, UiTokens.SWITCH_H,
                UiTokens.SWITCH_H / 2.0F, SkiaDraw.lerpColor(TRACK_OFF, accent, p));

        float travel = UiTokens.SWITCH_W - UiTokens.SWITCH_KNOB - UiTokens.SWITCH_INSET * 2.0F;
        float knobX = x + UiTokens.SWITCH_INSET + travel * p;
        float knobY = y + (UiTokens.SWITCH_H - UiTokens.SWITCH_KNOB) / 2.0F;
        SkiaDraw.drawRoundedRect(canvas, knobX, knobY, UiTokens.SWITCH_KNOB, UiTokens.SWITCH_KNOB,
                UiTokens.SWITCH_KNOB / 2.0F, KNOB);
    }
}
