package com.atom.chat.text;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RichTextTestExtra {
    @Test
    void stripInteractionsRemovesClickHoverUnderlineButKeepsColor() {
        Style interactive = Style.EMPTY.withColor(0xFF55FF)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg Steve "));
        RichText rich = RichText.of(Text.literal("Steve").setStyle(interactive));
        RichText clean = rich.stripInteractions();
        assertEquals("Steve", clean.getString());
        assertNull(clean.runs().get(0).style().getClickEvent());
        assertFalse(clean.runs().get(0).style().isUnderlined());
        assertEquals(0xFF55FF, clean.runs().get(0).style().getColor().getRgb());
    }
}
