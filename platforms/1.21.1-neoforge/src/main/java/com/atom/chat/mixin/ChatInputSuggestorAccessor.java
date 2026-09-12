package com.atom.chat.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets AtomChatSuggestor reach the private SuggestionsList instance. */
@Mixin(CommandSuggestions.class)
public interface ChatInputSuggestorAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList atomchat$getSuggestions();
}
