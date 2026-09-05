package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Avatar-companion payloads (0.1.10). Same-jar dual entrypoint: the records
 * and codecs are registered in the common entrypoint so both the integrated
 * server of a double-open client and a dedicated server speak the protocol.
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
 * <p>Servers without this companion simply ignore the unknown channels and
 * the client silently degrades to skins (e33chat philosophy).
 */
public final class AvatarPayloads {
    private AvatarPayloads() {
    }

    public static final int MAX_AVATAR_BYTES = 256 * 1024;

    public record AvatarUploadPayload(UUID uuid, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<AvatarUploadPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "avatar_upload"));
        public static final PacketCodec<RegistryByteBuf, AvatarUploadPayload> CODEC =
                PacketCodec.of(AvatarUploadPayload::write, AvatarUploadPayload::read);

        private static void write(AvatarUploadPayload payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        private static AvatarUploadPayload read(RegistryByteBuf buf) {
            return new AvatarUploadPayload(buf.readUuid(), buf.readByteArray());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AvatarRequestPayload(UUID uuid) implements CustomPayload {
        public static final CustomPayload.Id<AvatarRequestPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "avatar_request"));
        public static final PacketCodec<RegistryByteBuf, AvatarRequestPayload> CODEC =
                PacketCodec.of(AvatarRequestPayload::write, AvatarRequestPayload::read);

        private static void write(AvatarRequestPayload payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uuid());
        }

        private static AvatarRequestPayload read(RegistryByteBuf buf) {
            return new AvatarRequestPayload(buf.readUuid());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Empty {@code data} = the uuid has no custom avatar. */
    public record AvatarDataPayload(UUID uuid, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<AvatarDataPayload> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "avatar_data"));
        public static final PacketCodec<RegistryByteBuf, AvatarDataPayload> CODEC =
                PacketCodec.of(AvatarDataPayload::write, AvatarDataPayload::read);

        private static void write(AvatarDataPayload payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uuid());
            buf.writeByteArray(payload.data());
        }

        private static AvatarDataPayload read(RegistryByteBuf buf) {
            return new AvatarDataPayload(buf.readUuid(), buf.readByteArray());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(AvatarUploadPayload.ID, AvatarUploadPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AvatarRequestPayload.ID, AvatarRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AvatarDataPayload.ID, AvatarDataPayload.CODEC);
    }
}
