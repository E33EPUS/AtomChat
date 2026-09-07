package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/**
 * Avatar-companion payloads (0.1.10). Same-jar dual entrypoint: the records
 * and codecs are registered from the common @Mod constructor so both the
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
 * </ul>
 *
 * <p>The client only sends on channels the server actually negotiated; servers
 * without this companion therefore never receive an unknown payload and the
 * client silently degrades to skins (e33chat philosophy).
 */
public final class AvatarPayloads {
    private AvatarPayloads() {
    }

    public static final int MAX_AVATAR_BYTES = 256 * 1024;

    public record AvatarUploadPayload(UUID uuid, byte[] data) implements CustomPacketPayload {
        public static final Type<AvatarUploadPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarUploadPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public AvatarUploadPayload decode(RegistryFriendlyByteBuf buf) {
                        return new AvatarUploadPayload(buf.readUUID(), buf.readByteArray());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, AvatarUploadPayload payload) {
                        buf.writeUUID(payload.uuid());
                        buf.writeByteArray(payload.data());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AvatarRequestPayload(UUID uuid) implements CustomPacketPayload {
        public static final Type<AvatarRequestPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarRequestPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public AvatarRequestPayload decode(RegistryFriendlyByteBuf buf) {
                        return new AvatarRequestPayload(buf.readUUID());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, AvatarRequestPayload payload) {
                        buf.writeUUID(payload.uuid());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Empty {@code data} = the uuid has no custom avatar. */
    public record AvatarDataPayload(UUID uuid, byte[] data) implements CustomPacketPayload {
        public static final Type<AvatarDataPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "avatar_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AvatarDataPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public AvatarDataPayload decode(RegistryFriendlyByteBuf buf) {
                        return new AvatarDataPayload(buf.readUUID(), buf.readByteArray());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buf, AvatarDataPayload payload) {
                        buf.writeUUID(payload.uuid());
                        buf.writeByteArray(payload.data());
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registers every payload on the NeoForge payload bus (common entry). */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(AvatarUploadPayload.TYPE, AvatarUploadPayload.STREAM_CODEC,
                AvatarCompanionServer::handleUpload);
        registrar.playToServer(AvatarRequestPayload.TYPE, AvatarRequestPayload.STREAM_CODEC,
                AvatarCompanionServer::handleRequest);
        // The S2C receiver touches client-only rendering classes; keep it in a
        // dist-guarded lambda so a dedicated server never loads them.
        registrar.playToClient(AvatarDataPayload.TYPE, AvatarDataPayload.STREAM_CODEC,
                (payload, ctx) -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                        ctx.enqueueWork(() -> AvatarCompanionClient.onAvatarData(payload.uuid(), payload.data()));
                    }
                });
    }
}
