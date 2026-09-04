package com.atom.chat.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable flat representation of a Minecraft {@link Text} tree.
 *
 * <p>The tree is flattened into runs of plain text plus their effective styles. A
 * separate root style is kept because it can be useful when rebuilding/rendering a
 * sliced piece whose selected content has no runs of its own.
 */
public final class RichText {
    public record RichRun(String text, Style style) {}

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"']+");

    private final List<RichRun> runs;
    private final Style rootStyle;

    private RichText(List<RichRun> runs, Style rootStyle) {
        this.runs = List.copyOf(runs);
        this.rootStyle = rootStyle;
    }

    public static RichText of(Text text) {
        List<RichRun> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Style[] currentStyle = new Style[1];
        TextVisitFactory.visitFormatted(text, Style.EMPTY, (index, style, codePoint) -> {
            if (currentStyle[0] != null && !currentStyle[0].equals(style)) {
                if (current.length() > 0) {
                    out.add(new RichRun(current.toString(), currentStyle[0]));
                    current.setLength(0);
                }
            }
            currentStyle[0] = style;
            current.appendCodePoint(codePoint);
            return true;
        });
        if (current.length() > 0) {
            out.add(new RichRun(current.toString(), currentStyle[0]));
        }
        return new RichText(out, text.getStyle());
    }

    public static RichText literal(String text) {
        return of(Text.literal(text));
    }

    public static RichText empty() {
        return new RichText(List.of(), Style.EMPTY);
    }

    public String getString() {
        StringBuilder sb = new StringBuilder();
        for (RichRun run : runs) {
            sb.append(run.text());
        }
        return sb.toString();
    }

    public List<RichRun> runs() {
        return runs;
    }

    public boolean isEmpty() {
        return runs.isEmpty();
    }

    /**
     * Rebuilds a Minecraft {@link Text} from this flat run list, preserving each
     * run's effective style. Useful when a rewrite has to splice original styled
     * slices back together with new placeholder runs.
     */
    public Text toText() {
        MutableText out = Text.literal("").setStyle(rootStyle);
        for (RichRun run : runs) {
            out.append(Text.literal(run.text()).setStyle(run.style()));
        }
        return out;
    }

    public RichText slice(int from, int to) {
        String full = getString();
        int start = Math.max(0, Math.min(from, full.length()));
        int end = Math.max(0, Math.min(to, full.length()));
        if (start > end) {
            return new RichText(List.of(), rootStyle);
        }
        start = adjustStartBoundary(full, start);
        end = adjustEndBoundary(full, end);
        if (start >= end) {
            return new RichText(List.of(), rootStyle);
        }

        List<RichRun> out = new ArrayList<>();
        int pos = 0;
        for (RichRun run : runs) {
            int runEnd = pos + run.text().length();
            if (runEnd > start && pos < end) {
                int s = Math.max(0, start - pos);
                int e = Math.min(run.text().length(), end - pos);
                if (s < e) {
                    out.add(new RichRun(run.text().substring(s, e), run.style()));
                }
            }
            pos = runEnd;
            if (pos >= end) {
                break;
            }
        }
        return new RichText(out, rootStyle);
    }

    public RichText linkifyUrls() {
        List<RichRun> out = new ArrayList<>();
        for (RichRun run : runs) {
            if (run.style().getClickEvent() != null) {
                out.add(run);
                continue;
            }

            Matcher matcher = URL_PATTERN.matcher(run.text());
            int last = 0;
            while (matcher.find()) {
                if (matcher.start() > last) {
                    out.add(new RichRun(run.text().substring(last, matcher.start()), run.style()));
                }
                String url = matcher.group();
                Style urlStyle = run.style()
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                out.add(new RichRun(url, urlStyle));
                last = matcher.end();
            }
            if (last < run.text().length()) {
                out.add(new RichRun(run.text().substring(last), run.style()));
            }
        }
        return new RichText(out, rootStyle);
    }

    public Style rootStyle() {
        return rootStyle;
    }

    /**
     * Moves a start index left so it never points between the two UTF-16 code
     * units of a surrogate pair.
     */
    private static int adjustStartBoundary(String text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index - 1;
        }
        return index;
    }

    /**
     * Moves an end index right so it never points between the two UTF-16 code
     * units of a surrogate pair.
     */
    private static int adjustEndBoundary(String text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index + 1;
        }
        return index;
    }
}
