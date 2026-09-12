package com.atom.chat.mixin;

import net.minecraft.client.util.math.Rect2i;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public interface SuggestionWindowAccessor {
    @Mutable
    @Final
    @Accessor("area")
    Rect2i atomchat$getArea();

    @Mutable
    @Final
    @Accessor("area")
    void atomchat$setArea(Rect2i area);
}
