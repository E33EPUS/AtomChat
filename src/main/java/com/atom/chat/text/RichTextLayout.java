package com.atom.chat.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rich-text line wrapping and character hit testing.
 *
 * <p>Wrapping operates on UTF-16 code points, never splitting surrogate pairs.
 * Spaces at a wrap boundary are not kept on the previous line and are skipped
 * at the start of the next line; spaces that fit inside a line are preserved.
 */
public final class RichTextLayout {
    /**
     * A single wrapped line of kept visible text.
     *
     * <p>{@code textStart} and {@code textEnd} are absolute UTF-16 indices into
     * the original {@link RichText} string, not local offsets within this line.
     * They refer only to characters actually kept in this line's visible runs;
     * spaces dropped at a wrap boundary are not represented in either adjacent
     * line.
     *
     * @param runs the visible text runs that make up this line
     * @param textStart the absolute UTF-16 index in the original {@link RichText}
     *                  of the first kept visible character on this line
     * @param textEnd the exclusive absolute UTF-16 index in the original
     *                {@link RichText} after the last kept visible character on
     *                this line
     */
    public record RichLine(List<RichText.RichRun> runs, int textStart, int textEnd) {
        public RichLine {
            runs = List.copyOf(runs);
        }

        public String getPlainText() {
            StringBuilder sb = new StringBuilder();
            for (RichText.RichRun run : runs) {
                sb.append(run.text());
            }
            return sb.toString();
        }
    }

    private RichTextLayout() {
    }

    public static List<RichLine> wrap(RichText text, TextMeasurer measurer, float maxWidth) {
        List<RichLine> lines = new ArrayList<>();
        String full = text.getString();
        if (full.isEmpty()) {
            return lines;
        }

        List<RichText.RichRun> sourceRuns = text.runs();
        int lineStart = 0;
        int lineEnd = 0;
        float lineWidth = 0f;
        int index = 0;

        while (index < full.length()) {
            int codePoint = full.codePointAt(index);
            int codePointEnd = index + Character.charCount(codePoint);
            String codePointText = full.substring(index, codePointEnd);
            float codePointWidth = measurer.measure(codePointText);
            boolean space = codePoint == ' ';

            if (space) {
                if (lineEnd == lineStart) {
                    if (!lines.isEmpty()) {
                        // Don't let a wrapped line begin with a discarded space.
                        index = codePointEnd;
                        continue;
                    }
                    // Preserve leading spaces on the first line if they fit.
                    lineEnd = codePointEnd;
                    lineWidth += codePointWidth;
                    index = codePointEnd;
                    continue;
                }
                if (lineWidth + codePointWidth > maxWidth) {
                    emitTrimmedLine(lines, sourceRuns, full, lineStart, lineEnd);
                    lineStart = codePointEnd;
                    lineEnd = codePointEnd;
                    lineWidth = 0f;
                    index = codePointEnd;
                    continue;
                }
                lineEnd = codePointEnd;
                lineWidth += codePointWidth;
                index = codePointEnd;
            } else {
                if (lineEnd > lineStart && lineWidth + codePointWidth > maxWidth) {
                    emitTrimmedLine(lines, sourceRuns, full, lineStart, lineEnd);
                    lineStart = index;
                    lineEnd = codePointEnd;
                    lineWidth = codePointWidth;
                } else {
                    if (lineEnd == lineStart) {
                        lineStart = index;
                    }
                    lineEnd = codePointEnd;
                    lineWidth += codePointWidth;
                }
                index = codePointEnd;
            }
        }

        if (lineEnd > lineStart) {
            lines.add(makeLine(sourceRuns, full, lineStart, lineEnd));
        }
        return lines;
    }

    /**
     * Returns the character hit at {@code localX} as an absolute UTF-16 index
     * into the original {@link RichText} string, not a local offset within the
     * line. The returned index is always within {@code [line.textStart(),
     * line.textEnd()]}.
     *
     * @param line the line to hit-test
     * @param measurer measures character widths using the same metrics that
     *                 produced the line layout
     * @param localX the x position relative to this line's left edge
     * @return the absolute UTF-16 index into the original {@link RichText}
     */
    public static int charAt(RichLine line, TextMeasurer measurer, float localX) {
        String plain = line.getPlainText();
        if (localX <= 0f || plain.isEmpty()) {
            return line.textStart();
        }

        float x = 0f;
        int index = 0;
        while (index < plain.length()) {
            int codePoint = plain.codePointAt(index);
            int codePointEnd = index + Character.charCount(codePoint);
            float width = measurer.measure(plain.substring(index, codePointEnd));
            if (localX < x + width) {
                return line.textStart() + index;
            }
            x += width;
            index = codePointEnd;
        }
        return line.textEnd();
    }

    private static void emitTrimmedLine(List<RichLine> lines, List<RichText.RichRun> sourceRuns,
                                        String full, int start, int end) {
        int visibleEnd = end;
        while (visibleEnd > start && full.charAt(visibleEnd - 1) == ' ') {
            visibleEnd--;
        }
        if (visibleEnd > start) {
            lines.add(makeLine(sourceRuns, full, start, visibleEnd));
        }
    }

    private static RichLine makeLine(List<RichText.RichRun> sourceRuns, String full,
                                     int start, int end) {
        if (end < start || start < 0 || end > full.length()) {
            throw new IllegalArgumentException("Invalid line range " + start + ".." + end);
        }
        return new RichLine(sliceRuns(sourceRuns, start, end), start, end);
    }

    private static List<RichText.RichRun> sliceRuns(List<RichText.RichRun> sourceRuns,
                                                    int start, int end) {
        List<RichText.RichRun> out = new ArrayList<>();
        int pos = 0;
        for (RichText.RichRun run : sourceRuns) {
            int runEnd = pos + run.text().length();
            if (runEnd > start && pos < end) {
                int sliceStart = Math.max(0, start - pos);
                int sliceEnd = Math.min(run.text().length(), end - pos);
                if (sliceStart < sliceEnd) {
                    out.add(new RichText.RichRun(run.text().substring(sliceStart, sliceEnd), run.style()));
                }
            }
            pos = runEnd;
            if (pos >= end) {
                break;
            }
        }
        return out;
    }
}
