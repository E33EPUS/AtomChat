package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingTest {
    @Test
    void linearEndpoints() {
        assertEquals(0.0F, Easing.linear(0.0F), 0.001F);
        assertEquals(1.0F, Easing.linear(1.0F), 0.001F);
    }

    @Test
    void easeOutQuartMonotonic() {
        assertTrue(Easing.easeOutQuart(0.0F) < Easing.easeOutQuart(0.5F));
        assertTrue(Easing.easeOutQuart(0.5F) < Easing.easeOutQuart(1.0F));
        assertEquals(1.0F, Easing.easeOutQuart(1.0F), 0.001F);
    }
}
