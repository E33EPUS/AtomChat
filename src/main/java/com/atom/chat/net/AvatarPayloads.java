package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;

/**
 * Avatar-companion packets (0.1.10). Same-jar dual entrypoint: the channel and
 * its handlers are registered from the common mod constructor so both the
 * integrated server of a double-open client and a dedicated server speak the
 * protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code upload} — the local player pushed their custom avatar
 *       (uuid must equal the sender, validated server-side).</li>
 *   <li>C2S {@code request} — the client needs the avatar of {@code uuid}
 *       (lazy loading; the server keeps no state).</li>
 *   <li>S2C {@code data} — the requested avatar's PNG bytes, or an empty
 *       array when the uuid has no custom avatar (negative answer).</li>
 *   <li>S2C {@code changed} — a stored avatar was replaced mid-session.</li>
 * </ul>
 *
 * <p>The channel accepts connections that never negotiated it
 * ({@link NetworkRegistry#acceptMissingOr}), so a vanilla or non-mod server
 * simply ignores the unknown payloads and the client silently degrades to
 * skins (e33chat philosophy).
 */
public final class AvatarPayloads {
    private AvatarPayloads() {
    }

    public static final int MAX_AVATAR_BYTES = 256 * 1024;

    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar");
    public static final int ID_UPLOAD = 0;
    public static final int ID_REQUEST = 1;
    public static final int ID_DATA = 2;
    public static final int ID_CHANGED = 3;

    public static SimpleChannel CHANNEL;

    /** Builds the channel and registers every packet id. Common setup. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(AvatarUploadPayload.class, ID_UPLOAD)
                .encoder(AvatarUploadPayload::encode)
                .decoder(AvatarUploadPayload::decode)
                .consumerMainThread(AvatarCompanionServer::handleUpload)
                .add();

        CHANNEL.messageBuilder(AvatarRequestPayload.class, ID_REQUEST)
                .encoder(AvatarRequestPayload::encode)
                .decoder(AvatarRequestPayload::decode)
                .consumerMainThread(AvatarCompanionServer::handleRequest)
                .add();

        // The S2C handlers touch client-only rendering classes; the body only
        // runs on a client, but the dist guard keeps class loading explicit.
        CHANNEL.messageBuilder(AvatarDataPayload.class, ID_DATA)
                .encoder(AvatarDataPayload::encode)
                .decoder(AvatarDataPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() ->
                                AvatarCompanionClient.onAvatarData(payload.uuid(), payload.data()));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(AvatarChangedPayload.class, ID_CHANGED)
                .encoder(AvatarChangedPayload::encode)
                .decoder(AvatarChangedPayload::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ctx.get().enqueueWork(() ->
                                AvatarCompanionClient.onAvatarChanged(payload.uuid()));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    public record AvatarUploadPayload(UUID uuid, byte[] data) {
        public static void encode(AvatarUploadPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        public static AvatarUploadPayload decode(FriendlyByteBuf buf) {
            return new AvatarUploadPayload(buf.readUUID(), buf.readByteArray());
        }
    }

    public record AvatarRequestPayload(UUID uuid) {
        public static void encode(AvatarRequestPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
        }

        public static AvatarRequestPayload decode(FriendlyByteBuf buf) {
            return new AvatarRequestPayload(buf.readUUID());
        }
    }

    /** Empty {@code data} = the uuid has no custom avatar. */
    public record AvatarDataPayload(UUID uuid, byte[] data) {
        public static void encode(AvatarDataPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        public static AvatarDataPayload decode(FriendlyByteBuf buf) {
            return new AvatarDataPayload(buf.readUUID(), buf.readByteArray());
        }
    }

    /**
     * S2C notification that {@code uuid}'s stored avatar just changed. A
     * session-wide cache has no rejoin to lean on (0.2.5 hunt: an uploaded
     * avatar never reached players who had already negative-cached or decoded
     * the older copy), so every successful upload is announced and receivers
     * drop that uuid's cache before the next frame re-requests it.
     */
    public record AvatarChangedPayload(UUID uuid) {
        public static void encode(AvatarChangedPayload payload, FriendlyByteBuf buf) {
            buf.writeUUID(payload.uuid());
        }

        public static AvatarChangedPayload decode(FriendlyByteBuf buf) {
            return new AvatarChangedPayload(buf.readUUID());
        }
    }
}
