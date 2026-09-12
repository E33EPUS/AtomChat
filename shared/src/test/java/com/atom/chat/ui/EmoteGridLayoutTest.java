package com.atom.chat.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The emote tab's two sources must lay out in one predictable grid. */
class EmoteGridLayoutTest {
    private static final int COLS = 6;

    @Test
    void localEmotesAreFollowedByTheAddSlot() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 3, 0);

        assertEquals(EmoteGridLayout.Kind.LOCAL, layout.cellAt(0, 0).kind());
        assertEquals(EmoteGridLayout.Kind.LOCAL, layout.cellAt(0, 2).kind());
        assertEquals(EmoteGridLayout.Kind.ADD, layout.cellAt(0, 3).kind());
        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(0, 4).kind());
        assertEquals(1, layout.rows());
    }

    @Test
    void theGridGrowsARowAtATimeAndScrollsWithIt() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 6, 0);

        assertEquals(EmoteGridLayout.Kind.ADD, layout.cellAt(1, 0).kind());
        assertEquals(2, layout.rows());
    }

    @Test
    void aServerSectionStartsWithAHeaderRow() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 3, 4);

        assertEquals(EmoteGridLayout.Kind.ADD, layout.cellAt(0, 3).kind());
        assertEquals(EmoteGridLayout.Kind.SERVER_HEADER, layout.cellAt(1, 0).kind());
        assertEquals(EmoteGridLayout.Kind.SERVER, layout.cellAt(2, 0).kind());
        assertEquals(0, layout.cellAt(2, 0).index());
        assertEquals(EmoteGridLayout.Kind.SERVER, layout.cellAt(2, 3).kind());
        assertEquals(3, layout.cellAt(2, 3).index());
        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(2, 4).kind());
        assertEquals(3, layout.rows());
    }

    @Test
    void noServerRowsWithoutAServerPack() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 2, 0);

        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(1, 0).kind());
        assertEquals(1, layout.rows());
    }

    @Test
    void anEmptyLocalListStillOffersTheAddSlot() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 0, 0);

        assertEquals(EmoteGridLayout.Kind.ADD, layout.cellAt(0, 0).kind());
        assertEquals(1, layout.rows());
    }

    @Test
    void manyServerEmotesWrapIntoMoreRowsAfterTheHeader() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 0, 13);

        assertEquals(EmoteGridLayout.Kind.ADD, layout.cellAt(0, 0).kind());
        assertEquals(EmoteGridLayout.Kind.SERVER_HEADER, layout.cellAt(1, 0).kind());
        assertEquals(11, layout.cellAt(3, 5).index());
        assertEquals(12, layout.cellAt(4, 0).index());
        assertEquals(5, layout.rows(), "one local row, the header, then three server rows");
    }

    @Test
    void outOfRangeCoordinatesAreEmptyNotCrashing() {
        EmoteGridLayout layout = new EmoteGridLayout(COLS, 2, 2);

        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(-1, 0).kind());
        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(0, -1).kind());
        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(0, COLS).kind());
        assertEquals(EmoteGridLayout.Kind.EMPTY, layout.cellAt(99, 0).kind());
    }
}
