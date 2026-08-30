package com.atom.chat.render;

public final class Easing {
    private Easing() {
    }

    public static float linear(float t) {
        return t;
    }

    public static float easeOutQuart(float t) {
        t = 1.0F - t;
        return 1.0F - t * t * t * t;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3) + c1 * (float) Math.pow(t - 1.0F, 2);
    }

    public static float easeOutCubic(float t) {
        t = 1.0F - t;
        return 1.0F - t * t * t;
    }

    /** Exponential decel (Tuui's EaseOutQuart): long smooth tail for scrolling. */
    public static float easeOutExpo(float t) {
        return t >= 1.0F ? 1.0F : (float) (1.0 - Math.pow(2.0, -10.0 * t));
    }
}
