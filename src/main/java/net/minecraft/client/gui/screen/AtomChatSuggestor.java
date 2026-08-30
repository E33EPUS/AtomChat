package net.minecraft.client.gui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.util.math.MathHelper;

import net.minecraft.client.gui.screen.atomchat.ChatInputSuggestorAccessor;
import com.atom.chat.mixin.SuggestionWindowAccessor;

import java.util.function.IntSupplier;

/**
 * ChatInputSuggestor whose suggestion window anchors to our Skia input row
 * (window bottom sits on anchorTopY, left edge on anchorLeftX) instead of the
 * vanilla hard-coded screen bottom. Lives in MC's own package so the
 * package-private SuggestionWindow can be reached through the accessors.
 */
public class AtomChatSuggestor extends ChatInputSuggestor {
    private final IntSupplier anchorTopY;
    private final IntSupplier anchorLeftX;

    public AtomChatSuggestor(MinecraftClient client, Screen owner, TextFieldWidget textField, TextRenderer textRenderer,
                             boolean slashOptional, boolean suggestingWhenEmpty, int inWindowIndexOffset,
                             int maxSuggestionSize, boolean chatScreenSized, int color,
                             IntSupplier anchorTopY, IntSupplier anchorLeftX) {
        super(client, owner, textField, textRenderer, slashOptional, suggestingWhenEmpty,
                inWindowIndexOffset, maxSuggestionSize, chatScreenSized, color);
        this.anchorTopY = anchorTopY;
        this.anchorLeftX = anchorLeftX;
    }

    @Override
    public void show(boolean narrateFirstSuggestion) {
        super.show(narrateFirstSuggestion);
        Object window = ((ChatInputSuggestorAccessor) this).atomchat$getWindow();
        if (window == null) {
            return;
        }
        Rect2i area = ((SuggestionWindowAccessor) window).atomchat$getArea();
        int rows = MathHelper.clamp(area.getHeight() / 12, 1, 1024);
        int width = Math.max(area.getWidth(), 40);
        int bottom = MathHelper.clamp(anchorTopY.getAsInt(), rows * 12, 4096);
        int left = MathHelper.clamp(anchorLeftX.getAsInt(), 0, 4096);
        ((SuggestionWindowAccessor) window).atomchat$setArea(new Rect2i(left, bottom - rows * 12, width, rows * 12));
    }
}
