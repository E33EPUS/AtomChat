package com.atom.chat.net;

import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatServerConfig;
import com.atom.chat.util.CacheDirs;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the media companion (0.2.7): hosts chat images / GIFs in
 * {@code <gameDir>/atomchat-data/media/} and streams them back over the game
 * connection. No HTTP port, no public exposure - bytes only ever reach a
 * connected player.
 *
 * <p>Hardening: uploads are content-addressed (sha256), size capped and
 * magic-sniffed (only png/jpg/gif/webp/bmp); the client name is ignored so it
 * can never influence a path. Uploads are rate limited per player and the
 * store is trimmed by total size / age. {@code hostingEnabled} is the master
 * switch shared with the avatar companion.
 *
 * <p>Handlers arrive from {@link MediaPayloads#register}; every one of them
 * hops onto the server thread through {@code ctx.enqueueWork} before touching
 * a player or the store.
 */
public final class MediaCompanionServer {
    private MediaCompanionServer() {
    }

    private static final class Pending {
        final MediaUploadBuffer buffer;

        Pending(MediaUploadBuffer buffer) {
            this.buffer = buffer;
        }
    }

    private static final int MAX_PENDING_PER_PLAYER = 4;

    private static final Map<UUID, Long> lastUploadMs = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Pending>> uploads = new ConcurrentHashMap<>();
    private static volatile Path mediaDir;

    /** Resolved lazily: payload registration can run before the game dir exists. */
    private static Path mediaDir() {
        Path dir = mediaDir;
        if (dir == null) {
            dir = CacheDirs.mediaDataDir();
            mediaDir = dir;
        }
        return dir;
    }

    /** C2S state request; also the point where a hand-edited config is picked up. */
    public static void handleStateRequest(MediaPayloads.StateRequest payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                AtomChatServerConfig.reload();
                sendState(player);
            }
        });
    }

    public static void handleUploadStart(MediaPayloads.UploadStart payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                onStart(player, payload);
            }
        });
    }

    public static void handleUploadChunk(MediaPayloads.UploadChunk payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                onChunk(player, payload);
            }
        });
    }

    public static void handleUploadFinish(MediaPayloads.UploadFinish payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                onFinish(player, payload);
            }
        });
    }

    public static void handleFetch(MediaPayloads.Fetch payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                onFetch(player, payload);
            }
        });
    }

    private static void sendState(ServerPlayer player) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        PacketDistributor.sendToPlayer(player,
                new MediaPayloads.MediaState(config.hostingEnabled, config.maxFileBytes()));
    }

    private static void onStart(ServerPlayer player, MediaPayloads.UploadStart payload) {
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.hostingEnabled) {
            result(player, payload.uploadId(), "", "disabled");
            return;
        }
        if (payload.uploadId() == null) {
            return;
        }
        Map<UUID, Pending> existing = uploads.get(player.getUUID());
        if (existing != null && existing.size() >= MAX_PENDING_PER_PLAYER) {
            result(player, payload.uploadId(), "", "busy");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUploadMs.get(player.getUUID());
        if (last != null && now - last < Math.max(0, config.uploadCooldownMs)) {
            result(player, payload.uploadId(), "", "rate_limited");
            return;
        }
        if (payload.totalBytes() <= 0 || payload.totalBytes() > config.maxFileBytes()) {
            result(player, payload.uploadId(), "", "too_large");
            return;
        }
        lastUploadMs.put(player.getUUID(), now);
        uploads.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>())
                .put(payload.uploadId(), new Pending(new MediaUploadBuffer(payload.totalBytes(), config.maxFileBytes())));
    }

    private static void onChunk(ServerPlayer player, MediaPayloads.UploadChunk payload) {
        Pending pending = pending(player.getUUID(), payload.uploadId());
        if (pending == null) {
            return;
        }
        if (!pending.buffer.write(payload.offset(), payload.data())) {
            remove(player.getUUID(), payload.uploadId());
            result(player, payload.uploadId(), "", "invalid");
        }
    }

    private static void onFinish(ServerPlayer player, MediaPayloads.UploadFinish payload) {
        Pending pending = remove(player.getUUID(), payload.uploadId());
        if (pending == null) {
            return;
        }
        if (!pending.buffer.isComplete()) {
            result(player, payload.uploadId(), "", "incomplete");
            return;
        }
        byte[] content = pending.buffer.bytes();
        var server = player.getServer();
        if (server == null) {
            return;
        }
        // File IO belongs off the network thread.
        server.execute(() -> store(player, payload.uploadId(), content));
    }

    private static void store(ServerPlayer player, UUID uploadId, byte[] content) {
        String ext = MediaIds.extensionOf(content);
        if (ext == null) {
            result(player, uploadId, "", "unsupported");
            return;
        }
        String mediaId = MediaIds.idFor(content, ext);
        try {
            Path dir = mediaDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(mediaId);
            if (!Files.exists(file)) {
                Path tmp = dir.resolve(mediaId + ".tmp");
                Files.write(tmp, content);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            prune();
            AtomChat.LOGGER.info("Stored hosted media {} ({} bytes)", mediaId, content.length);
            result(player, uploadId, mediaId, "");
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to store hosted media", e);
            result(player, uploadId, "", "io");
        }
    }

    private static void onFetch(ServerPlayer player, MediaPayloads.Fetch payload) {
        String mediaId = payload.mediaId();
        AtomChatServerConfig config = AtomChatServerConfig.get();
        if (!config.hostingEnabled || !MediaIds.isSafeId(mediaId)) {
            missing(player, mediaId);
            return;
        }
        var server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> stream(player, mediaId));
    }

    private static void stream(ServerPlayer player, String mediaId) {
        Path dir = mediaDir();
        try {
            Path file = dir == null ? null : dir.resolve(mediaId);
            if (file == null || !Files.isRegularFile(file)) {
                missing(player, mediaId);
                return;
            }
            int maxBytes = AtomChatServerConfig.get().maxFileBytes();
            if (Files.size(file) > maxBytes) {
                missing(player, mediaId);
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            int total = bytes.length;
            if (total == 0) {
                missing(player, mediaId);
                return;
            }
            for (int offset = 0; offset < total; offset += MediaIds.CHUNK_BYTES) {
                int len = Math.min(MediaIds.CHUNK_BYTES, total - offset);
                PacketDistributor.sendToPlayer(player, new MediaPayloads.Data(
                        mediaId, offset, total, Arrays.copyOfRange(bytes, offset, offset + len)));
            }
        } catch (IOException e) {
            missing(player, mediaId);
        }
    }

    /** Deletes expired files, then trims oldest-first to the total-size cap. */
    private static void prune() {
        Path dir = mediaDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        AtomChatServerConfig config = AtomChatServerConfig.get();
        long cutoff = config.retentionDays > 0
                ? System.currentTimeMillis() - config.retentionDays * 86_400_000L
                : Long.MIN_VALUE;
        List<Path> files = new ArrayList<>();
        long total = 0L;
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                if (modifiedOrZero(p) < cutoff) {
                    deleteQuietly(p);
                    continue;
                }
                files.add(p);
                total += sizeOrZero(p);
            }
        } catch (IOException e) {
            AtomChat.LOGGER.warn("Failed to scan hosted media directory", e);
            return;
        }
        long max = config.maxTotalBytes();
        if (total <= max) {
            return;
        }
        files.sort(Comparator.comparingLong(MediaCompanionServer::modifiedOrZero));
        for (Path p : files) {
            if (total <= max) {
                break;
            }
            long size = sizeOrZero(p);
            if (deleteQuietly(p)) {
                total -= size;
            }
        }
    }

    private static boolean deleteQuietly(Path p) {
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            return false;
        }
    }

    private static long sizeOrZero(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long modifiedOrZero(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Pending pending(UUID player, UUID uploadId) {
        if (player == null || uploadId == null) {
            return null;
        }
        Map<UUID, Pending> map = uploads.get(player);
        return map == null ? null : map.get(uploadId);
    }

    private static Pending remove(UUID player, UUID uploadId) {
        if (player == null || uploadId == null) {
            return null;
        }
        Map<UUID, Pending> map = uploads.get(player);
        if (map == null) {
            return null;
        }
        Pending pending = map.remove(uploadId);
        if (map.isEmpty()) {
            uploads.remove(player, map);
        }
        return pending;
    }

    private static void result(ServerPlayer player, UUID uploadId, String mediaId, String error) {
        if (uploadId == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new MediaPayloads.UploadResult(uploadId, mediaId, error));
    }

    private static void missing(ServerPlayer player, String mediaId) {
        PacketDistributor.sendToPlayer(player,
                new MediaPayloads.Missing(mediaId == null ? "" : mediaId));
    }
}
