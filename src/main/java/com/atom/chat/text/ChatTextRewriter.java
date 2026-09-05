package com.atom.chat.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites vanilla ChatHud messages into compact placeholders.
 *
 * <p>Two rewrites exist:
 * <ul>
 *   <li>{@code [[CICode,...]]} image codes become a localized green
 *       {@code [图片]}/{@code [Image]} so the vanilla chat does not show a long
 *       raw URL.</li>
 *   <li>Quote replies whose visible text starts with {@code 「引用...」} become
 *       {@code [引用]}/{@code [Quote]} after the sender prefix, matching the
 *       e33chat banner convention.</li>
 * </ul>
 *
 * <p>Both rewrites splice original styled runs back together, so sender colors,
 * decorations and the surrounding text style survive.
 */
public final class ChatTextRewriter {
    private static final Pattern CICODE = Pattern.compile(
            "\\[\\[CICode,[^\\]]+\\]\\]|\\[CICode,[^\\]]+\\]", Pattern.CASE_INSENSITIVE);
    private static final Style IMAGE_GREEN = Style.EMPTY.withColor(0x55FF55);
    private static final Style QUOTE_BLUE = Style.EMPTY.withColor(0x4A90E2);

    private record Replacement(int start, int end, String langKey, Style style) {
    }

    private ChatTextRewriter() {
    }

    /**
     * Returns a rewritten copy of {@code message}, or {@code null} when no image
     * code or quote prefix is present.
     */
    public static Text rewrite(Text message) {
        if (message == null) {
            return null;
        }
        RichText rich = RichText.of(message);
        String full = rich.getString();
        List<Replacement> replacements = findReplacements(full);
        if (replacements.isEmpty()) {
            return null;
        }
        replacements.sort(Comparator.comparingInt(Replacement::start));

        MutableText out = Text.literal("");
        int pos = 0;
        for (Replacement replacement : replacements) {
            if (replacement.start() < pos) {
                continue; // overlapping (e.g. image code inside a removed quote)
            }
            if (replacement.start() > pos) {
                out.append(rich.slice(pos, replacement.start()).toText());
            }
            out.append(Text.translatable(replacement.langKey()).setStyle(replacement.style()));
            pos = replacement.end();
        }
        if (pos < full.length()) {
            out.append(rich.slice(pos, full.length()).toText());
        }
        return out;
    }

    /**
     * Rewrites vanilla private-message system lines to the e33chat-style
     * {@code <sender>[私聊] body} format. Returns null when the message is not a
     * vanilla /msg line.
     *
     * @param ownName real profile name used as the sender for outgoing echoes
     */
    public static Text rewritePrivate(Text message, String ownName) {
        if (!(message.getContent() instanceof TranslatableTextContent tc)) {
            return null;
        }
        String key = tc.getKey();
        Object[] args = tc.getArgs();
        Text name;
        Text content;
        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            name = asText(args[0]);
            content = asText(args[1]);
        } else if (key.equals("commands.message.display.outgoing") && args.length >= 2) {
            name = ownName == null || ownName.isBlank() ? Text.literal("") : Text.literal(ownName);
            content = asText(args[1]);
        } else {
            return null;
        }
        MutableText out = Text.literal("<");
        out.append(name);
        out.append(">");
        out.append(Text.translatable("atomchat.banner.whisper")
                .setStyle(Style.EMPTY.withColor(0xFF55FF)));
        out.append(" ");
        out.append(content);
        return out;
    }

    private static Text asText(Object arg) {
        return arg instanceof Text text ? text : Text.literal(String.valueOf(arg));
    }

    private static List<Replacement> findReplacements(String full) {
        List<Replacement> out = new ArrayList<>();
        Matcher matcher = CICODE.matcher(full);
        while (matcher.find()) {
            out.add(new Replacement(matcher.start(), matcher.end(),
                    "atomchat.hud.image", IMAGE_GREEN));
        }
        Replacement quote = quoteReplacement(full);
        if (quote != null) {
            out.add(quote);
        }
        return out;
    }

    private static Replacement quoteReplacement(String full) {
        int quote = full.indexOf("「引用");
        while (quote >= 0) {
            if (isQuotePrefix(full, quote)) {
                int close = full.indexOf('」', quote);
                if (close > quote) {
                    return new Replacement(quote, close + 1,
                            "atomchat.hud.quote", QUOTE_BLUE);
                }
            }
            quote = full.indexOf("「引用", quote + 1);
        }
        return null;
    }

    /**
     * Only the first visible content marker counts as a quote reply. A message
     * body that merely mentions {@code 「引用} mid-sentence is not rewritten.
     */
    private static boolean isQuotePrefix(String full, int quote) {
        if (quote == 0) {
            return true;
        }
        String before = full.substring(0, quote);
        // Public vanilla lines start "<name> 「引用..."; private rewrites start
        // "<name>[Whisper] 「引用..." — both should become [Quote]/[引用].
        return before.endsWith("> ") || before.endsWith(">")
                || before.endsWith("] ") || before.endsWith("]");
    }
}
