package com.atom.chat.net;

import com.atom.chat.config.ServerConfigScreen;
import net.minecraft.client.Minecraft;

/**
 * Client half of the config screen (0.2.9): opens it and feeds it the server's
 * answers.
 *
 * <p>Deliberately separate from the screen itself so no payload handler ever
 * references a {@code Screen} - NeoForge's RuntimeDistCleaner refuses a dual-side
 * class that touches client types, which is why the reference lives behind this
 * bridge and behind a dist check in {@link ConfigPayloads#register}.
 */
public final class ConfigScreenClient {
    private static ServerConfigScreen open;

    private ConfigScreenClient() {
    }

    /** S2C: the server's current settings - build the screen on this client. */
    public static void onSnapshot(ConfigPayloads.Snapshot payload) {
        Minecraft client = Minecraft.getInstance();
        ServerConfigScreen screen = new ServerConfigScreen(client.screen, payload.configVersion(),
                payload.values(), payload.pack());
        open = screen;
        client.setScreen(screen);
    }

    /** S2C: the pack summary changed after a rescan. */
    public static void onStatus(ConfigPayloads.PackStatus status) {
        if (open != null) {
            open.acceptStatus(status);
        }
    }

    /** S2C: the verdict of a save. */
    public static void onResult(ConfigPayloads.Result result) {
        if (open != null) {
            open.onResult(result.ok(), result.error());
        }
        if (result.ok()) {
            // A saved change may alter the pack (phrases, server name), so pull a
            // fresh manifest - the panel reads the stored one.
            PackSyncClient.resync();
        }
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
