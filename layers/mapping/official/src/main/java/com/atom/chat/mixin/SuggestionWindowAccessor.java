package com.atom.chat.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets AtomChatSuggestor reposition the package-private SuggestionsList rect. */
@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionWindowAccessor {
    @Accessor("rect")
    Rect2i atomchat$getRect();

    @Mutable
    @Final
    @Accessor("rect")
    void atomchat$setRect(Rect2i rect);
}
