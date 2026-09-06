package com.atom.chat.ui.input;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing-order semantics: first consumer wins, non-consumers are skipped in
 * order, and an unconsumed event reports false so the caller can fall through
 * to vanilla.
 */
class InputRouterTest {

    /** Records which handlers saw each event, to assert consultation order. */
    private static class RecordingHandler implements InputHandler {
        final String name;
        final boolean consumes;
        final List<String> seen;

        RecordingHandler(String name, boolean consumes, List<String> seen) {
            this.name = name;
            this.consumes = consumes;
            this.seen = seen;
        }

        private boolean record() {
            seen.add(name);
            return consumes;
        }

        @Override
        public boolean onClick(double mouseX, double mouseY, int button) {
            return record();
        }

        @Override
        public boolean onDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return record();
        }

        @Override
        public boolean onRelease(double mouseX, double mouseY, int button) {
            return record();
        }

        @Override
        public boolean onScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            return record();
        }

        @Override
        public boolean onKey(int keyCode, int scanCode, int modifiers) {
            return record();
        }

        @Override
        public boolean onChar(char chr, int modifiers) {
            return record();
        }
    }

    private final List<String> seen = new ArrayList<>();

    private InputRouter router(boolean firstConsumes, boolean secondConsumes) {
        InputRouter router = new InputRouter();
        router.add(new RecordingHandler("first", firstConsumes, seen));
        router.add(new RecordingHandler("second", secondConsumes, seen));
        router.add(new RecordingHandler("third", true, seen));
        return router;
    }

    @Test
    void firstConsumerWinsAndLaterHandlersAreNotConsulted() {
        InputRouter router = router(true, true);
        assertTrue(router.click(1, 2, 0));
        assertEquals(List.of("first"), seen);
    }

    @Test
    void nonConsumersFallThroughInOrder() {
        InputRouter router = router(false, false);
        assertTrue(router.click(1, 2, 0));
        assertEquals(List.of("first", "second", "third"), seen);
    }

    @Test
    void unconsumedEventReportsFalse() {
        InputRouter router = new InputRouter();
        router.add(new RecordingHandler("pass", false, seen));
        assertFalse(router.click(0, 0, 0));
        assertFalse(router.drag(0, 0, 0, 1, 1));
        assertFalse(router.release(0, 0, 0));
        assertFalse(router.scroll(0, 0, 0, 1));
        assertFalse(router.key(256, 0, 0));
        assertFalse(router.charTyped('a', 0));
        assertEquals(6, seen.size());
    }

    @Test
    void everyEventTypeRoutesIndependently() {
        InputRouter router = new InputRouter();
        seen.clear();
        RecordingHandler consumesKeyOnly = new RecordingHandler("keyonly", false, seen) {
            @Override
            public boolean onKey(int keyCode, int scanCode, int modifiers) {
                seen.add(name);
                return true;
            }
        };
        router.add(consumesKeyOnly);
        router.add(new RecordingHandler("rest", true, seen));
        assertTrue(router.key(42, 0, 0));
        // The first handler only consumes keys; every other event falls through
        // to the second handler, which consumes it. Recording also captures the
        // first handler's pass, so the click is logged three times in total.
        assertTrue(router.click(0, 0, 0));
        assertEquals(List.of("keyonly", "keyonly", "rest"), seen);
    }
}
