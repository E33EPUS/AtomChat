package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-media companion payloads (0.2.7). Same-jar dual entrypoint: the
 * records and codecs are registered in the common entrypoint so a dedicated
 * server and the integrated server of a double-open client both speak the
 * protocol.
 *
 * <p>Protocol:
 * <ul>
 *   <li>C2S {@code StateRequest} → S2C {@code MediaState} — is hosting on and
 *       what is the per-file cap.</li>
 *   <li>C2S {@code UploadStart} / {@code UploadChunk} / {@code UploadFinish} →
 *       S2C {@code UploadResult} — chunked upload; the server answers with a
 *       content-addressed {@code mediaId} (or an error).</li>
 *   <li>C2S {@code Fetch} → S2C {@code Data} chunks (or {@code Missing}) —
 *       chunked download over the game connection; no HTTP port involved.</li>
 * </ul>
 *
 * <p>A client that did not negotiate these channels never receives an unknown
 * payload; callers probe with {@code ClientPlayNetworking.canSend} first and
 * silently fall back to the external image host.
 */
public final class MediaPayloads {
    private MediaPayloads() {
    }

    public record StateRequest() implements CustomPayload {
        public static final CustomPayload.Id<StateRequest> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_state_request"));
        public static final PacketCodec<RegistryByteBuf, StateRequest> CODEC =
                PacketCodec.of(StateRequest::write, StateRequest::read);

        private static void write(StateRequest payload, RegistryByteBuf buf) {
        }

        private static StateRequest read(RegistryByteBuf buf) {
            return new StateRequest();
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MediaState(boolean enabled, int maxBytes) implements CustomPayload {
        public static final CustomPayload.Id<MediaState> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_state"));
        public static final PacketCodec<RegistryByteBuf, MediaState> CODEC =
                PacketCodec.of(MediaState::write, MediaState::read);

        private static void write(MediaState payload, RegistryByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeInt(payload.maxBytes());
        }

        private static MediaState read(RegistryByteBuf buf) {
            return new MediaState(buf.readBoolean(), buf.readInt());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadStart(UUID uploadId, String name, int totalBytes) implements CustomPayload {
        public static final CustomPayload.Id<UploadStart> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_upload_start"));
        public static final PacketCodec<RegistryByteBuf, UploadStart> CODEC =
                PacketCodec.of(UploadStart::write, UploadStart::read);

        private static void write(UploadStart payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uploadId());
            buf.writeString(payload.name() == null ? "" : payload.name());
            buf.writeInt(payload.totalBytes());
        }

        private static UploadStart read(RegistryByteBuf buf) {
            return new UploadStart(buf.readUuid(), buf.readString(), buf.readInt());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadChunk(UUID uploadId, int offset, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<UploadChunk> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_upload_chunk"));
        public static final PacketCodec<RegistryByteBuf, UploadChunk> CODEC =
                PacketCodec.of(UploadChunk::write, UploadChunk::read);

        private static void write(UploadChunk payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uploadId());
            buf.writeInt(payload.offset());
            buf.writeByteArray(payload.data());
        }

        private static UploadChunk read(RegistryByteBuf buf) {
            return new UploadChunk(buf.readUuid(), buf.readInt(), buf.readByteArray());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UploadFinish(UUID uploadId) implements CustomPayload {
        public static final CustomPayload.Id<UploadFinish> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_upload_finish"));
        public static final PacketCodec<RegistryByteBuf, UploadFinish> CODEC =
                PacketCodec.of(UploadFinish::write, UploadFinish::read);

        private static void write(UploadFinish payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uploadId());
        }

        private static UploadFinish read(RegistryByteBuf buf) {
            return new UploadFinish(buf.readUuid());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** {@code mediaId} and {@code error} are empty strings rather than null. */
    public record UploadResult(UUID uploadId, String mediaId, String error) implements CustomPayload {
        public static final CustomPayload.Id<UploadResult> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_upload_result"));
        public static final PacketCodec<RegistryByteBuf, UploadResult> CODEC =
                PacketCodec.of(UploadResult::write, UploadResult::read);

        private static void write(UploadResult payload, RegistryByteBuf buf) {
            buf.writeUuid(payload.uploadId());
            buf.writeString(payload.mediaId() == null ? "" : payload.mediaId());
            buf.writeString(payload.error() == null ? "" : payload.error());
        }

        private static UploadResult read(RegistryByteBuf buf) {
            return new UploadResult(buf.readUuid(), buf.readString(), buf.readString());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Fetch(String mediaId) implements CustomPayload {
        public static final CustomPayload.Id<Fetch> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_fetch"));
        public static final PacketCodec<RegistryByteBuf, Fetch> CODEC =
                PacketCodec.of(Fetch::write, Fetch::read);

        private static void write(Fetch payload, RegistryByteBuf buf) {
            buf.writeString(payload.mediaId() == null ? "" : payload.mediaId());
        }

        private static Fetch read(RegistryByteBuf buf) {
            return new Fetch(buf.readString());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** One streamed slice; {@code offset + data.length == totalBytes} marks the end. */
    public record Data(String mediaId, int offset, int totalBytes, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<Data> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_data"));
        public static final PacketCodec<RegistryByteBuf, Data> CODEC =
                PacketCodec.of(Data::write, Data::read);

        private static void write(Data payload, RegistryByteBuf buf) {
            buf.writeString(payload.mediaId() == null ? "" : payload.mediaId());
            buf.writeInt(payload.offset());
            buf.writeInt(payload.totalBytes());
            buf.writeByteArray(payload.data());
        }

        private static Data read(RegistryByteBuf buf) {
            return new Data(buf.readString(), buf.readInt(), buf.readInt(), buf.readByteArray());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Missing(String mediaId) implements CustomPayload {
        public static final CustomPayload.Id<Missing> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "media_missing"));
        public static final PacketCodec<RegistryByteBuf, Missing> CODEC =
                PacketCodec.of(Missing::write, Missing::read);

        private static void write(Missing payload, RegistryByteBuf buf) {
            buf.writeString(payload.mediaId() == null ? "" : payload.mediaId());
        }

        private static Missing read(RegistryByteBuf buf) {
            return new Missing(buf.readString());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(StateRequest.ID, StateRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadStart.ID, UploadStart.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadChunk.ID, UploadChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(UploadFinish.ID, UploadFinish.CODEC);
        PayloadTypeRegistry.playC2S().register(Fetch.ID, Fetch.CODEC);
        PayloadTypeRegistry.playS2C().register(MediaState.ID, MediaState.CODEC);
        PayloadTypeRegistry.playS2C().register(UploadResult.ID, UploadResult.CODEC);
        PayloadTypeRegistry.playS2C().register(Data.ID, Data.CODEC);
        PayloadTypeRegistry.playS2C().register(Missing.ID, Missing.CODEC);
    }
}
