package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollControllerTest {
    private static final float EPS = 0.001F;

    @Test
    void initialStateIsZeroed() {
        ScrollController scroll = new ScrollController();
        assertEquals(0.0F, scroll.getScrollY(), EPS);
        assertEquals(0.0F, scroll.getMaxScroll(), EPS);
        assertEquals(0.0F, scroll.getTarget(), EPS);
        assertFalse(scroll.isDragging());
    }

    @Test
    void setContentClampsScrollToMaxScroll() {
        ScrollController scroll = new ScrollController();
        scroll.setContent(1000.0F, 200.0F);
        scroll.scrollToBottom(false);
        assertEquals(800.0F, scroll.getScrollY(), EPS);

        // Shrinking content must clamp an old bottom position to the new max.
        scroll.setContent(300.0F, 200.0F);
        assertEquals(100.0F, scroll.getScrollY(), EPS);
        assertEquals(100.0F, scroll.getTarget(), EPS);
        assertEquals(100.0F, scroll.getMaxScroll(), EPS);
    }

    @Test
    void wheelClampsAndUpdatesTarget() {
        ScrollController scroll = new ScrollController();
        scroll.setContent(1000.0F, 200.0F);
        assertEquals(800.0F, scroll.getMaxScroll(), EPS);

        // Positive wheel scrolls up and is clamped at zero.
        scroll.wheel(10.0F);
        assertEquals(0.0F, scroll.getTarget(), EPS);

        // Negative wheel scrolls down by the standard wheel step.
        scroll.wheel(-1.0F);
        assertEquals(45.0F, scroll.getTarget(), EPS);

        // A huge negative wheel is clamped to the content bottom.
        scroll.wheel(-1000.0F);
        assertEquals(800.0F, scroll.getTarget(), EPS);
    }

    @Test
    void beginDragDragToEndDragClamps() {
        ScrollController scroll = new ScrollController();
        scroll.setContent(1000.0F, 200.0F);
        scroll.beginDrag(10.0F);
        assertTrue(scroll.isDragging());

        scroll.dragTo(1000.0F, 200.0F);
        assertEquals(800.0F, scroll.getScrollY(), EPS);
        assertEquals(800.0F, scroll.getTarget(), EPS);

        scroll.dragTo(-1000.0F, 200.0F);
        assertEquals(0.0F, scroll.getScrollY(), EPS);

        scroll.endDrag();
        assertFalse(scroll.isDragging());
    }

    @Test
    void resetRestoresZero() {
        ScrollController scroll = new ScrollController();
        scroll.setContent(1000.0F, 200.0F);
        scroll.scrollToBottom(false);
        scroll.wheel(-1.0F);
        scroll.beginDrag(1.0F);
        scroll.updateAnimation(System.currentTimeMillis());

        scroll.reset();

        assertEquals(0.0F, scroll.getScrollY(), EPS);
        assertEquals(0.0F, scroll.getMaxScroll(), EPS);
        assertEquals(0.0F, scroll.getTarget(), EPS);
        assertFalse(scroll.isDragging());
    }

    @Test
    void scrollToBottomPinsToMaxScrollAndNoContentIsNoOp() {
        ScrollController scroll = new ScrollController();
        scroll.setContent(1000.0F, 200.0F);
        scroll.scrollToBottom(false);
        assertEquals(800.0F, scroll.getScrollY(), EPS);
        assertEquals(800.0F, scroll.getTarget(), EPS);

        scroll.reset();
        scroll.scrollToBottom(false);
        assertEquals(0.0F, scroll.getScrollY(), EPS);
        assertEquals(0.0F, scroll.getTarget(), EPS);
        assertEquals(0.0F, scroll.getMaxScroll(), EPS);
    }
}
