package net.minecraft.client.gui.screen.atomchat;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lives inside MC's own screen package so the package-private SuggestionWindow
 * type can be referenced by the accessor.
 */
@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {
    @Accessor("window")
    ChatInputSuggestor.SuggestionWindow atomchat$getWindow();
}
