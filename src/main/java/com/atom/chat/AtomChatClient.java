package com.atom.chat;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.render.PanelBlurRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class AtomChatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtomChatConfig.get();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(AtomChat.MOD_ID, "panel_blur_shaders");
            }

            @Override
            public void reload(ResourceManager manager) {
                PanelBlurRenderer.resetShader();
            }
        });
        AtomChat.LOGGER.info("AtomChat client initialized");
    }
}
