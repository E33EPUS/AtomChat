package com.atom.chat.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the AWT file dialog clear MC's held-button state while it owns input. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("activeButton")
    void atomchat$setActiveButton(int button);
}
