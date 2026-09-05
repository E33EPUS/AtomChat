package com.atom.chat.net;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Image;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client side of the avatar companion: lazy loading with a memory cache.
 *
 * <p>Flow: rendering asks {@link #currentAvatar(UUID)} for a player with no
 * cached avatar → a request payload goes out (deduplicated) → the companion
 * answers with the PNG bytes (or an empty array = no avatar) → the image is
 * decoded on a daemon thread and cached by uuid.
 *
 * <p>Cache policy (grilled 0.1.10): the cache lives for one server session
 * and is wiped on join, so a changed avatar shows up after re-entering and
 * the server keeps no per-viewer state. No disk layer — with a wipe-on-join
 * policy it could never serve a hit.
 *
 * <p>Companion presence: the client never knows up front. The first request
 * doubles as a probe — if no answer arrives within {@link #PROBE_TIMEOUT_MS}
 * the server is marked companion-less for the rest of the session and all
 * traffic stops (silent degradation to skins).
 */
public final class AvatarCompanionClient {
    private AvatarCompanionClient() {
    }

    private static final long PROBE_TIMEOUT_MS = 3_000L;
    private static final long NO_AVATAR_TTL_MS = 30_000L;

    private enum Presence { UNKNOWN, YES, NO }

    private static volatile Presence presence = Presence.UNKNOWN;
    private static final AtomicInteger GENERATION = new AtomicInteger();

    /** Decoded images keyed by uuid; Skija finalises evicted native memory. */
    private static final Map<UUID, Image> decoded = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> requestedAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> noAvatarUntil = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> decoding = new ConcurrentHashMap<>();

    /** Client init: registers the S2C receiver. */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(AvatarPayloads.AvatarDataPayload.ID, (payload, context) ->
                context.client().execute(() -> onAvatarData(payload.uuid(), payload.data())));
    }

    /** Reset on join: avatars are re-fetched lazily after the join. */
    public static void onJoin() {
        presence = Presence.UNKNOWN;
        requestedAt.clear();
        noAvatarUntil.clear();
        decoding.clear();
        int generation = GENERATION.incrementAndGet();
        for (Map.Entry<UUID, Image> entry : decoded.entrySet()) {
            decoded.remove(entry.getKey());
            entry.getValue().close();
        }
        if (generation != GENERATION.get()) {
            // Another join raced us; the later wipe wins.
            return;
        }
    }

    /**
     * Returns the decoded companion avatar for the uuid, or null while it is
     * absent/loading. Calling this may start a lazy request.
     */
    public static Image currentAvatar(UUID uuid) {
        if (uuid == null || presence == Presence.NO) {
            return null;
        }
        Image cached = decoded.get(uuid);
        if (cached != null) {
            return cached;
        }
        Long noUntil = noAvatarUntil.get(uuid);
        long now = System.currentTimeMillis();
        if (noUntil != null && now < noUntil) {
            return null;
        }
        Long sentAt = requestedAt.get(uuid);
        if (sentAt != null) {
            if (presence == Presence.UNKNOWN && now - sentAt > PROBE_TIMEOUT_MS) {
                // No answer to the probe: the server has no companion.
                presence = Presence.NO;
                requestedAt.clear();
            }
            return null;
        }
        requestedAt.put(uuid, now);
        ClientPlayNetworking.send(new AvatarPayloads.AvatarRequestPayload(uuid));
        return null;
    }

    /** Pushes the local avatar to the server; a no-op without a companion. */
    public static void uploadOwnAvatar(UUID uuid, byte[] pngBytes) {
        if (presence != Presence.YES || uuid == null || pngBytes == null
                || pngBytes.length == 0 || pngBytes.length > AvatarPayloads.MAX_AVATAR_BYTES) {
            return;
        }
        ClientPlayNetworking.send(new AvatarPayloads.AvatarUploadPayload(uuid, pngBytes));
    }

    private static void onAvatarData(UUID uuid, byte[] data) {
        if (uuid == null) {
            return;
        }
        int generation = GENERATION.get();
        requestedAt.remove(uuid);
        noAvatarUntil.remove(uuid);
        if (presence == Presence.UNKNOWN) {
            presence = Presence.YES;
        }
        if (data == null || data.length == 0) {
            // No avatar on the server; back off before asking again.
            noAvatarUntil.put(uuid, System.currentTimeMillis() + NO_AVATAR_TTL_MS);
            return;
        }
        if (decoding.putIfAbsent(uuid, Boolean.TRUE) != null) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                Image image = Image.makeFromEncoded(data);
                if (image != null && generation == GENERATION.get()) {
                    decoded.put(uuid, image);
                } else if (image != null) {
                    image.close();
                }
            } catch (Throwable t) {
                AtomChat.LOGGER.warn("Failed to decode companion avatar for {}", uuid, t);
            } finally {
                decoding.remove(uuid);
            }
        }, "AtomChat-CompanionAvatarDecode");
        worker.setDaemon(true);
        worker.start();
    }
}
