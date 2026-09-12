package com.atom.chat.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AtomChatScreen;
import net.minecraft.client.gui.screen.AtomChatScreen.AtomChatOpenMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "openChatScreen", at = @At("HEAD"), cancellable = true)
    private void atomchat$openChatScreen(String text, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        client.setScreen(new AtomChatScreen(text, AtomChatOpenMode.DIRECT_WORLD));
        ci.cancel();
    }
}
