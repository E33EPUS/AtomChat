package com.atom.chat.mixin;

import net.minecraft.client.Minecraft;
import com.atom.chat.screen.AtomChatScreen;
import com.atom.chat.screen.AtomChatScreen.AtomChatOpenMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void atomchat$openChatScreen(String text, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        client.setScreen(new AtomChatScreen(text, AtomChatOpenMode.DIRECT_WORLD));
        ci.cancel();
    }
}
