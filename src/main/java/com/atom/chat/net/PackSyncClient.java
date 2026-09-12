package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.pack.PackAssembler;
import com.atom.chat.pack.PackDiff;
import com.atom.chat.pack.PackKeys;
import com.atom.chat.pack.PackManifestFile;
import com.atom.chat.pack.ServerPack;
import com.atom.chat.pack.ServerPackStore;
import com.atom.chat.util.CacheDirs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client side of the pack distribution (0.2.9).
 *
 * <p>On join the client asks for the manifest, hashes what it already has on
 * disk and asks only for the files that are missing or different - so deleting
 * one emote costs one file, and a second join costs nothing (decisions 18, 21).
 * The answer arrives in 24 KiB chunks that are reassembled and verified against
 * the manifest, and the whole pack is swapped into place as one directory, so a
 * half-finished sync is never readable as a working pack.
 *
 * <p>Two hard limits protect us from a hostile server (decision 16): more than
 * {@value #MAX_FILES} files or {@value #MAX_BYTES} bytes is refused outright,
 * whatever the config says.
 */
public final class PackSyncClient {
    /** Files a pack may contain before this client refuses it. */
    public static final int MAX_FILES = 200;
    /** Bytes a pack may total before this client refuses it. */
    public static final long MAX_BYTES = 16L * 1024L * 1024L;
    /** How long to wait for the first chunk / the next chunk before giving up. */
    private static final long TIMEOUT_MS = 30_000L;
    /** How long to wait for the manifest itself. */
    private static final long HELLO_TIMEOUT_MS = 15_000L;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "atomchat-pack-sync");
        thread.setDaemon(true);
        return thread;
    });

    private static String key;
    private static ServerPack expected;
    private static Set<String> wanted = Set.of();
    private static final Map<String, PackAssembler.FileBuffer> buffers = new LinkedHashMap<>();
    private static final Map<String, byte[]> ready = new LinkedHashMap<>();
    private static int filesSent;
    private static long deadline;
    private static boolean active;
    /** Last thing that happened, for the panel (Phase 4 reads it). */
    private static volatile String detail = "";

    private PackSyncClient() {
    }

    /** Registers the receivers; called once from the client entrypoint. */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Manifest.ID,
                (payload, context) -> context.client().execute(() -> onManifest(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Chunk.ID,
                (payload, context) -> context.client().execute(() -> onChunk(payload)));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Done.ID,
                (payload, context) -> context.client().execute(() -> onDone(context.client(), payload)));
    }

    /** Join hook: ask this server what it offers, unless the player opted out. */
    public static void onJoin(MinecraftClient client) {
        reset();
        if (!AtomChatConfig.get().serverPacksEnabled) {
            detail = "disabled";
            return;
        }
        if (!ClientPlayNetworking.canSend(PackPayloads.Hello.ID)) {
            // A server without AtomChat never sees this packet, and the panel
            // simply keeps showing the plain world name.
            detail = "no_server_mod";
            return;
        }
        key = worldKey(client);
        if (key == null) {
            detail = "no_world_key";
            return;
        }
        // Show whatever is already installed while the handshake runs.
        ServerPackStore.refresh(CacheDirs.packsRoot(), key);
        active = true;
        deadline = System.currentTimeMillis() + HELLO_TIMEOUT_MS;
        ClientPlayNetworking.send(new PackPayloads.Hello());
    }

    /** Disconnect hook: nothing carries over between worlds. */
    public static void onDisconnect() {
        reset();
    }

    private static void reset() {
        ServerPackStore.clear();
        key = null;
        expected = null;
        wanted = Set.of();
        buffers.clear();
        ready.clear();
        filesSent = 0;
        deadline = 0L;
        active = false;
    }

    private static void onManifest(MinecraftClient client, PackPayloads.Manifest payload) {
        if (!active) {
            return;
        }
        if (!payload.enabled()) {
            // The server stopped offering packs: drop the copy so the panel does
            // not keep showing content that is no longer served.
            detail = "server_disabled";
            active = false;
            ServerPackStore.clear();
            return;
        }
        ServerPack manifest;
        try {
            manifest = toPack(payload);
        } catch (RuntimeException e) {
            abort("bad_manifest");
            return;
        }
        long total = manifest.totalBytes();
        if (manifest.files().size() > MAX_FILES || total > MAX_BYTES) {
            abort("too_large");
            return;
        }
        expected = manifest;
        Path dir = CacheDirs.packDir(key);
        ServerPack stored = PackManifestFile.read(dir);
        if (stored != null && stored.packHash().equals(manifest.packHash())) {
            // Same pack we already have on disk: still ack so the server can log it.
            IO.execute(() -> {
                Map<String, String> hashes = PackManifestFile.localHashes(dir);
                PackDiff.Plan plan = PackDiff.plan(manifest, hashes);
                client.execute(() -> {
                    if (!active) {
                        return;
                    }
                    if (plan.isUpToDate()) {
                        detail = "up_to_date";
                        active = false;
                        ServerPackStore.refresh(CacheDirs.packsRoot(), key);
                        send(new PackPayloads.Ack(true, "up_to_date"));
                    } else {
                        // Something was deleted or edited behind our back.
                        request(client, plan);
                    }
                });
            });
            return;
        }
        IO.execute(() -> {
            Map<String, String> hashes = PackManifestFile.localHashes(dir);
            PackDiff.Plan plan = PackDiff.plan(manifest, hashes);
            client.execute(() -> {
                if (!active) {
                    return;
                }
                request(client, plan);
            });
        });
    }

    /** Asks for the diff, or installs straight away when there is nothing to fetch. */
    private static void request(MinecraftClient client, PackDiff.Plan plan) {
        PackManifestFile.deleteStale(CacheDirs.packDir(key), plan.remove());
        if (plan.fetch().isEmpty()) {
            installExisting(client);
            return;
        }
        wanted = new LinkedHashSet<>();
        for (ServerPack.FileEntry entry : plan.fetch()) {
            wanted.add(entry.name());
        }
        filesSent = 0;
        buffers.clear();
        ready.clear();
        deadline = System.currentTimeMillis() + TIMEOUT_MS;
        detail = "fetching";
        ClientPlayNetworking.send(new PackPayloads.Need(new ArrayList<>(wanted)));
    }

    /** Nothing to download: the files on disk are already right, so store the metadata. */
    private static void installExisting(MinecraftClient client) {
        Path dir = CacheDirs.packDir(key);
        IO.execute(() -> {
            Map<String, byte[]> contents = new LinkedHashMap<>();
            for (ServerPack.FileEntry entry : expected.files()) {
                try {
                    contents.put(entry.name(),
                            java.nio.file.Files.readAllBytes(
                                    dir.resolve(PackManifestFile.EMOTES_DIR).resolve(entry.name())));
                } catch (IOException e) {
                    client.execute(() -> abort("read_failed"));
                    return;
                }
            }
            try {
                PackManifestFile.install(CacheDirs.packsRoot(), key, expected, contents);
            } catch (IOException e) {
                client.execute(() -> abort("write_failed"));
                return;
            }
            client.execute(() -> {
                detail = "up_to_date";
                active = false;
                ServerPackStore.refresh(CacheDirs.packsRoot(), key);
                send(new PackPayloads.Ack(true, "metadata"));
            });
        });
    }

    private static void onChunk(PackPayloads.Chunk payload) {
        if (!active || expected == null) {
            return;
        }
        String name = payload.name();
        if (!wanted.contains(name) || payload.totalBytes() <= 0) {
            abort("unexpected_chunk");
            return;
        }
        ServerPack.FileEntry entry = findByManifest(name);
        if (entry == null || entry.size() != payload.totalBytes()) {
            abort("size_mismatch");
            return;
        }
        PackAssembler.FileBuffer buffer = buffers.computeIfAbsent(name,
                ignored -> new PackAssembler.FileBuffer(entry));
        if (!buffer.offer(payload.offset(), payload.data())) {
            abort("bad_chunk");
            return;
        }
        deadline = System.currentTimeMillis() + TIMEOUT_MS;
        if (!buffer.isComplete()) {
            return;
        }
        byte[] bytes = buffer.verified();
        if (bytes == null) {
            abort("hash_mismatch");
            return;
        }
        ready.put(name, bytes);
        buffers.remove(name);
    }

    private static void onDone(MinecraftClient client, PackPayloads.Done payload) {
        if (!active || expected == null) {
            return;
        }
        if (ready.size() != wanted.size()) {
            abort("incomplete");
            return;
        }
        Map<String, byte[]> contents = new LinkedHashMap<>(ready);
        ServerPack pack = expected;
        String installKey = key;
        String hash = pack.packHash().substring(0, 8);
        int count = contents.size();
        IO.execute(() -> {
            try {
                PackManifestFile.install(CacheDirs.packsRoot(), installKey, pack, contents);
            } catch (IOException e) {
                AtomChat.LOGGER.warn("Failed to store the server pack", e);
                client.execute(() -> abort("write_failed"));
                return;
            }
            client.execute(() -> {
                detail = "synced";
                active = false;
                ServerPackStore.refresh(CacheDirs.packsRoot(), installKey);
                send(new PackPayloads.Ack(true, count + " file(s) " + hash));
                AtomChat.LOGGER.info("Server pack {} synced ({} file(s), {} KB)", hash, count,
                        pack.totalBytes() / 1024);
            });
        });
    }

    /** Called every client tick: the only way a stalled sync ever ends. */
    public static void tick() {
        if (active && deadline > 0L && System.currentTimeMillis() > deadline) {
            abort("timeout");
        }
    }

    private static void abort(String reason) {
        AtomChat.LOGGER.warn("Server pack sync failed: {}", reason);
        detail = reason;
        active = false;
        send(new PackPayloads.Ack(false, reason));
    }

    private static void send(PackPayloads.Ack ack) {
        if (ClientPlayNetworking.canSend(PackPayloads.Ack.ID)) {
            ClientPlayNetworking.send(ack);
        }
    }

    private static ServerPack.FileEntry findByManifest(String name) {
        for (ServerPack.FileEntry entry : expected.files()) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    private static ServerPack toPack(PackPayloads.Manifest payload) {
        List<ServerPack.FileEntry> files = new ArrayList<>(payload.files().size());
        for (PackPayloads.PackFile file : payload.files()) {
            files.add(new ServerPack.FileEntry(file.name(), file.sha256(), file.size()));
        }
        return new ServerPack(payload.packHash(), files, payload.phrases(),
                payload.serverName(), payload.icon());
    }

    /** Last sync outcome, for the panel and the logs. */
    public static String detail() {
        return detail;
    }

    /** The pack this client currently has for the joined world, or null. */
    public static ServerPack installed() {
        if (key == null) {
            return null;
        }
        return PackManifestFile.read(CacheDirs.packDir(key));
    }

    /** Folder name of the joined world's pack, or null when not in a world. */
    public static String currentKey() {
        return key;
    }

    private static String worldKey(MinecraftClient client) {
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return PackKeys.serverKey(info.address);
        }
        if (client.getServer() != null) {
            return PackKeys.levelKey(client.getServer().getSaveProperties().getLevelName());
        }
        return null;
    }
}
