package com.atom.chat.ui;

/**
 * Row and column maths for the emote tab, which shows two sources in one grid:
 * the player's own emotes with a trailing "+" slot, then - when a server pack is
 * installed - a header row followed by that server's read-only emotes.
 *
 * <p>Kept apart from the drawing code on purpose: hover, click and render each
 * ask this class what a cell means, so the three can never disagree about where
 * the boundary between "mine" and "theirs" is.
 */
public final class EmoteGridLayout {
    /** What a cell holds. */
    public enum Kind { LOCAL, ADD, SERVER_HEADER, SERVER, EMPTY }

    /** One resolved cell. */
    public record Cell(Kind kind, int index) {
        public static final Cell EMPTY = new Cell(Kind.EMPTY, -1);
    }

    private final int cols;
    private final int localCount;
    private final int serverCount;

    public EmoteGridLayout(int cols, int localCount, int serverCount) {
        this.cols = Math.max(1, cols);
        this.localCount = Math.max(0, localCount);
        this.serverCount = Math.max(0, serverCount);
    }

    private int localRows() {
        return (localCount + 1 + cols - 1) / cols;
    }

    private int serverRows() {
        return (serverCount + cols - 1) / cols;
    }

    /** Rows the tab needs, header included; the panel scrolls to fit them. */
    public int rows() {
        int rows = localRows();
        if (serverCount > 0) {
            rows += 1 + serverRows();
        }
        return rows;
    }

    /** Height of the whole grid, in cells. */
    public int cellRows() {
        return rows();
    }

    /** What sits at (row, col); {@link Kind#EMPTY} for padding past the content. */
    public Cell cellAt(int row, int col) {
        if (row < 0 || col < 0 || col >= cols) {
            return Cell.EMPTY;
        }
        int localRows = localRows();
        if (row < localRows) {
            int index = row * cols + col;
            if (index < localCount) {
                return new Cell(Kind.LOCAL, index);
            }
            if (index == localCount) {
                return new Cell(Kind.ADD, -1);
            }
            return Cell.EMPTY;
        }
        if (serverCount <= 0) {
            return Cell.EMPTY;
        }
        if (row == localRows) {
            return new Cell(Kind.SERVER_HEADER, -1);
        }
        int index = (row - localRows - 1) * cols + col;
        return index < serverCount ? new Cell(Kind.SERVER, index) : Cell.EMPTY;
    }
}
