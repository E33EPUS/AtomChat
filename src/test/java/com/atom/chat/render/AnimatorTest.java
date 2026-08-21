package com.atom.chat.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimatorTest {
    @Test
    void animatorReachesTarget() {
        Animator animator = new Animator(Easing::linear).setValue(0.0F);
        animator.animateTo(100, 10.0F);
        animator.update(50);
        assertEquals(5.0F, animator.getValue(), 0.001F);
        assertTrue(!animator.isDone());
        animator.update(50);
        assertEquals(10.0F, animator.getValue(), 0.001F);
        assertTrue(animator.isDone());
    }

    @Test
    void setValueIsImmediate() {
        Animator animator = new Animator(Easing::easeOutQuart).setValue(7.0F);
        assertEquals(7.0F, animator.getValue());
        assertTrue(animator.isDone());
    }
}
