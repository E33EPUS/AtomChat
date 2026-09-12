package com.atom.chat.net;

import com.atom.chat.config.ServerConfigScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client half of the config screen (0.2.9): the receivers that open it and feed
 * it the server's answers.
 *
 * <p>Deliberately separate from the screen itself so no payload handler ever
 * references a {@code Screen}. The Forge/NeoForge port needs that split - their
 * RuntimeDistCleaner refuses a dual-side class that touches client types - and
 * keeping it here costs nothing on Fabric.
 */
public final class ConfigScreenClient {
    private static ServerConfigScreen open;

    private ConfigScreenClient() {
    }

    /** Registers the receivers; called once from the client entrypoint. */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Snapshot.ID, (payload, context) ->
                context.client().execute(() -> openScreen(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Status.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (open != null) {
                        open.acceptStatus(payload.pack());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayloads.Result.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (open != null) {
                        open.onResult(payload.ok(), payload.error());
                    }
                    if (payload.ok()) {
                        // A saved change may alter the pack (phrases, server name),
                        // so pull a fresh manifest - the panel reads the stored one.
                        PackSyncClient.resync();
                    }
                }));
    }

    private static void openScreen(MinecraftClient client, ConfigPayloads.Snapshot payload) {
        ServerConfigScreen screen = new ServerConfigScreen(client.currentScreen, payload.configVersion(),
                payload.values(), payload.pack());
        open = screen;
        client.setScreen(screen);
    }

    /** The screen telling us it is gone, so a late answer cannot touch it. */
    public static void forget(ServerConfigScreen screen) {
        if (open == screen) {
            open = null;
        }
    }

    public static void onDisconnect() {
        open = null;
    }
}
