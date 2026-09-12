package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.pack.PackBuilder;
import com.atom.chat.pack.ServerEmoteMigration;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.util.CacheDirs;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the pack distribution (0.2.9): answers a client's {@code Hello}
 * with the manifest and streams whatever that client says it is missing.
 *
 * <p>The pack itself is built from the admin's own content - the emote folder,
 * the phrase list in the config and {@code server-icon.png} plus the MOTD - and
 * is cached for {@value #CACHE_MS} ms so a busy join minute does not hash the
 * same files for every player.
 *
 * <p>Only one download per player runs at a time and each tick gives every
 * waiting player at most {@value #CHUNKS_PER_TICK} chunks, so twenty players
 * joining at once cannot starve each other or the tick loop.
 */
public final class PackSyncServer {
    /** Chunks a single player may receive per tick (about 96 KB). */
    private static final int CHUNKS_PER_TICK = 4;
    /** How long a built pack is reused before the folders are scanned again. */
    private static final long CACHE_MS = 60_000L;

    private static final Map<UUID, Download> DOWNLOADS = new ConcurrentHashMap<>();
    /** The pack each player was shown last, so a rebuild cannot change what we send mid-sync. */
    private static final Map<UUID, ServerPack> OFFERED = new ConcurrentHashMap<>();
    private static volatile Cached cached;
    /** The legacy emote carry-over runs once per JVM, on the first pack build. */
    private static final java.util.concurrent.atomic.AtomicBoolean LEGACY_CHECKED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private PackSyncServer() {
    }

    private record Cached(ServerPack pack, long builtAtMs) {
    }

    /** One player's remaining chunks for one request. */
    private static final class Download {
        final Queue<PackPayloads.Chunk> chunks = new ArrayDeque<>();
        int files;

        void add(String name, byte[] bytes) {
            for (int offset = 0; offset < bytes.length; offset += MediaIds.CHUNK_BYTES) {
                int length = Math.min(MediaIds.CHUNK_BYTES, bytes.length - offset);
                chunks.add(new PackPayloads.Chunk(name, offset, bytes.length,
                        Arrays.copyOfRange(bytes, offset, offset + length)));
            }
            files++;
        }
    }

    /** Registers the receivers and the tick that drains the queues. */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Hello.ID, (payload, context) ->
                onServer(context.player(), () -> onHello(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Need.ID, (payload, context) ->
                onServer(context.player(), () -> onNeed(context.player(), payload.names())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Ack.ID, (payload, context) ->
                onServer(context.player(), () -> onAck(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(PackSyncServer::tick);
        AtomChat.LOGGER.info("AtomChat pack distribution registered (server side)");
    }

    /** The pack this server is currently offering (for the config screen). */
    public static ServerPack peek(MinecraftServer server) {
        return pack(server);
    }

    /** Drops the cached pack so the next request re-scans the emote folder. */
    public static void invalidate() {
        cached = null;
    }

    private static void onServer(ServerPlayerEntity player, Runnable work) {
        MinecraftServer server = player == null ? null : player.getServer();
        // File reads and hashing belong off the network thread.
        if (server != null) {
            server.execute(work);
        }
    }

    private static void onHello(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        DOWNLOADS.remove(player.getUuid());
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.packEnabled) {
            // Answering beats staying silent: the client shows "this server does
            // not offer a pack" instead of waiting for a timeout.
            ServerPlayNetworking.send(player, new PackPayloads.Manifest(
                    false, "", "", null, List.of(), List.of()));
            return;
        }
        ServerPack pack = pack(player.getServer());
        OFFERED.put(player.getUuid(), pack);
        ServerPlayNetworking.send(player, manifestOf(pack));
    }

    private static PackPayloads.Manifest manifestOf(ServerPack pack) {
        List<PackPayloads.PackFile> files = new ArrayList<>(pack.files().size());
        for (ServerPack.FileEntry entry : pack.files()) {
            files.add(new PackPayloads.PackFile(entry.name(), entry.sha256Hex(), entry.size()));
        }
        return new PackPayloads.Manifest(true, pack.packHash(), pack.serverName(),
                pack.icon(), files, pack.phrases());
    }

    private static ServerPack pack(MinecraftServer server) {
        Cached current = cached;
        long now = System.currentTimeMillis();
        if (current != null && now - current.builtAtMs() < CACHE_MS) {
            return current.pack();
        }
        carryOverLegacyServerEmotes();
        AtomChatServerConfig config = AtomChatServerConfig.get();
        ServerPack pack = PackBuilder.build(CacheDirs.serverEmotesDir(), config.phrases, config.packName,
                server == null ? "" : server.getServerMotd(), readIcon(),
                config.packMaxFiles, config.packMaxBytes());
        cached = new Cached(pack, now);
        return pack;
    }

    /**
     * A server used to hand out {@code config/atomchat/emotes/}, the folder a
     * client also keeps its own stickers in. Dedicated servers therefore carry
     * their files over into the server's own folder once; anywhere else those
     * files belong to the player and must stay private.
     */
    private static void carryOverLegacyServerEmotes() {
        if (!LEGACY_CHECKED.compareAndSet(false, true)) {
            return;
        }
        ServerEmoteMigration.migrate(CacheDirs.emotesDir(), CacheDirs.serverEmotesDir(),
                FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER);
    }

    private static byte[] readIcon() {
        Path icon = CacheDirs.gameDir().resolve("server-icon.png");
        try {
            return Files.isRegularFile(icon) ? Files.readAllBytes(icon) : null;
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to read server-icon.png", e);
            return null;
        }
    }

    private static void onNeed(ServerPlayerEntity player, List<String> names) {
        if (player == null) {
            return;
        }
        AtomChatServerConfig config = AtomChatServerConfig.get();
        // Send exactly the pack this player was shown: a rebuild between the
        // manifest and the download would otherwise ship bodies whose hashes
        // contradict the manifest the client is verifying against.
        ServerPack pack = OFFERED.get(player.getUuid());
        if (pack == null) {
            pack = pack(player.getServer());
        }
        if (!config.packEnabled || names == null || names.isEmpty()) {
            DOWNLOADS.remove(player.getUuid());
            ServerPlayNetworking.send(player, new PackPayloads.Done(0));
            return;
        }
        Map<String, ServerPack.FileEntry> offered = new HashMap<>();
        for (ServerPack.FileEntry entry : pack.files()) {
            offered.put(entry.name(), entry);
        }
        Download download = new Download();
        Path dir = CacheDirs.serverEmotesDir();
        for (String name : names) {
            // A client can only ask for what the manifest already listed.
            ServerPack.FileEntry entry = offered.get(name);
            if (entry == null) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(dir.resolve(entry.name()));
            } catch (IOException e) {
                AtomChat.LOGGER.warn("Server pack file {} vanished before it could be sent", name);
                continue;
            }
            if (bytes.length != entry.size()
                    || !ServerPack.sha256Hex(bytes).equals(entry.sha256Hex())) {
                // The admin edited the file mid-sync: skip it rather than ship a
                // body that contradicts the manifest the client is verifying against.
                AtomChat.LOGGER.info("Server pack file {} changed on disk mid-sync; skipped", name);
                continue;
            }
            download.add(entry.name(), bytes);
        }
        if (download.files == 0) {
            ServerPlayNetworking.send(player, new PackPayloads.Done(0));
            return;
        }
        DOWNLOADS.put(player.getUuid(), download);
    }

    private static void tick(MinecraftServer server) {
        if (DOWNLOADS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Download>> iterator = DOWNLOADS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Download> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                OFFERED.remove(entry.getKey());
                continue;
            }
            Download download = entry.getValue();
            int sent = 0;
            while (sent < CHUNKS_PER_TICK && !download.chunks.isEmpty()) {
                ServerPlayNetworking.send(player, download.chunks.poll());
                sent++;
            }
            if (download.chunks.isEmpty()) {
                ServerPlayNetworking.send(player, new PackPayloads.Done(download.files));
                iterator.remove();
            }
        }
    }

    private static void onAck(ServerPlayerEntity player, PackPayloads.Ack ack) {
        if (player == null) {
            return;
        }
        OFFERED.remove(player.getUuid());
        Cached current = cached;
        String hash = current == null ? "-" : current.pack().packHash().substring(0, 8);
        String name = player.getName().getString();
        if (ack.ok()) {
            AtomChat.LOGGER.info("Player {} verified the server pack {} ({})", name, hash,
                    ack.detail().isEmpty() ? "up to date" : ack.detail());
        } else {
            AtomChat.LOGGER.warn("Player {} could not sync the server pack {}: {}", name, hash,
                    ack.detail());
        }
    }
}
