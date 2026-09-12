package com.atom.chat.screen;

import com.atom.chat.mixin.ChatInputSuggestorAccessor;
import com.atom.chat.mixin.SuggestionWindowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.Mth;

import java.util.function.IntSupplier;

/**
 * CommandSuggestions whose suggestion window anchors to our Skia input row
 * (window bottom sits on anchorTopY, left edge on anchorLeftX) instead of the
 * vanilla hard-coded screen bottom. The accessor mixins reach the package-
 * private SuggestionsList internals.
 */
public class AtomChatSuggestor extends CommandSuggestions {
    private final IntSupplier anchorTopY;
    private final IntSupplier anchorLeftX;

    public AtomChatSuggestor(Minecraft client, Screen owner, EditBox textField, Font textRenderer,
                             boolean commandsOnly, boolean onlyShowIfCursorPastError, int lineStartOffset,
                             int suggestionLineLimit, boolean anchorToBottom, int fillColor,
                             IntSupplier anchorTopY, IntSupplier anchorLeftX) {
        super(client, owner, textField, textRenderer, commandsOnly, onlyShowIfCursorPastError,
                lineStartOffset, suggestionLineLimit, anchorToBottom, fillColor);
        this.anchorTopY = anchorTopY;
        this.anchorLeftX = anchorLeftX;
    }

    @Override
    public void showSuggestions(boolean narrateFirstSuggestion) {
        super.showSuggestions(narrateFirstSuggestion);
        CommandSuggestions.SuggestionsList window = ((ChatInputSuggestorAccessor) this).atomchat$getSuggestions();
        if (window == null) {
            return;
        }
        Rect2i area = ((SuggestionWindowAccessor) window).atomchat$getRect();
        int rows = Mth.clamp(area.getHeight() / 12, 1, 1024);
        int width = Math.max(area.getWidth(), 40);
        int bottom = Mth.clamp(anchorTopY.getAsInt(), rows * 12, 4096);
        int left = Mth.clamp(anchorLeftX.getAsInt(), 0, 4096);
        ((SuggestionWindowAccessor) window).atomchat$setRect(new Rect2i(left, bottom - rows * 12, width, rows * 12));
    }
}
