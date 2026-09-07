package com.atom.chat;

import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.image.ImageLoader;
import com.atom.chat.notification.NotificationBanner;
import com.atom.chat.render.PanelBlurRenderer;
import com.atom.chat.render.SkiaGraphics;
import com.atom.chat.screen.AtomChatScreen;
import com.atom.chat.util.CacheDirs;
import com.atom.chat.wallpaper.WallpaperStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = AtomChat.MOD_ID, dist = Dist.CLIENT)
public class AtomChatClient {
    public static final KeyMapping OPEN_ATOMCHAT_KEY = new KeyMapping(
            "key.atomchat.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.atomchat.category");

    public AtomChatClient(ModContainer container, IEventBus modEventBus) {
        // The AWT/Swing image picker needs a real toolkit, not the headless
        // AWT some launchers/other mods select. This must run before any AWT
        // class initialises, so it lives here at the very start of client init.
        System.setProperty("java.awt.headless", "false");

        modEventBus.addListener(PanelBlurRenderer::registerShaders);
        modEventBus.addListener(AtomChatClient::onClientSetup);
        modEventBus.addListener(AtomChatClient::registerKeyMappings);
        modEventBus.addListener(AtomChatClient::registerReloadListener);

        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onPlayerDisconnect);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AtomChatClient::onRenderGui);
    }


    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        AtomChatConfig.get();
        CacheDirs.migrateFromOldConfigPaths();
        WallpaperStore.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/wallpaper"));
        ImageLoader.get().init(CacheDirs.imageCacheDir());
        com.atom.chat.net.AvatarCompanionClient.init();
        com.atom.chat.history.ChatHistory.init(
                net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("atomchat/history"));
        AtomChat.LOGGER.info("AtomChat client initialized");
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ATOMCHAT_KEY);
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> PanelBlurRenderer.resetShader());
    }

    private static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        // Adopt the world key first so a saved history can be loaded before
        // join-time system lines start arriving.
        com.atom.chat.history.ChatHistory.onJoin(client);
        com.atom.chat.page.ProfilePage.noteJoin();
        com.atom.chat.net.AvatarCompanionClient.onJoin();
        com.atom.chat.chat.TeleportCommands.reset();
    }

    private static void onPlayerDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft client = Minecraft.getInstance();
        // Save before the stores are cleared, then drop the world key.
        com.atom.chat.history.ChatHistory.onDisconnect(client);
        PrivateChatStore.reset();
        PrivateEchoTracker.clear();
        ChatStore.reset();
        com.atom.chat.chat.SeenPlayers.clear();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        com.atom.chat.history.ChatHistory.tick(client);
        NotificationBanner.INSTANCE.tick();
        while (OPEN_ATOMCHAT_KEY.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new AtomChatScreen("", AtomChatScreen.AtomChatOpenMode.RESTORE));
            }
        }
    }

    /**
     * Skia notification banners. They render only when no screen is open;
     * while AtomChat is open the panel already shows the conversation.
     */
    private static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.screen != null) {
            return;
        }
        if (!NotificationBanner.INSTANCE.hasActive()) {
            return;
        }
        SkiaGraphics.INSTANCE.draw(null, (canvas, worldSnapshot) -> {
            float fbH = client.getMainRenderTarget().height;
            float density = Math.max(1.0F, fbH / 1080.0F);
            float screenW = client.getMainRenderTarget().width / density;
            float screenH = fbH / density;
            NotificationBanner.INSTANCE.render(canvas, screenW, screenH);
        });
    }
}
