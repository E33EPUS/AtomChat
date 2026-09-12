package com.atom.chat.net;

import com.atom.chat.AtomChat;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client pack distribution payloads (0.2.9), on their own
 * {@code atomchat:dist/...} channel so the feature can be switched off without
 * touching the media or avatar companions.
 *
 * <p>Protocol, all of it client-initiated:
 * <ul>
 *   <li>C2S {@code Hello} to S2C {@code Manifest} - "what are you offering?" The
 *       server answers with the whole manifest (a few KB) or {@code enabled=false}.</li>
 *   <li>C2S {@code Need(names)} to S2C {@code Chunk} ... then {@code Done} - the
 *       client computes the per-file diff locally and asks only for what it is
 *       missing (decision 21); the server streams 24 KiB chunks.</li>
 *   <li>C2S {@code Ack(ok, detail)} - the outcome, so the server can log it
 *       (decision 28: there is no /atomchat info command to fall back on).</li>
 * </ul>
 *
 * <p>Every read path validates before allocating: a peer that sends an absurd
 * file count, name or hash is a protocol violation, not something to store.
 */
public final class PackPayloads {
    /** Files a manifest may declare; well above the server cap, low enough to refuse nonsense. */
    public static final int MAX_WIRE_FILES = 512;
    /** Phrases a manifest may carry. */
    public static final int MAX_WIRE_PHRASES = 64;
    /** Longest name, hash or detail string we accept off the wire. */
    private static final int MAX_NAME_CHARS = 96;
    private static final int MAX_HASH_CHARS = 80;
    private static final int MAX_DETAIL_CHARS = 120;

    private PackPayloads() {
    }

    /** One manifest entry: name, content hash and size. */
    public record PackFile(String name, String sha256, int size) {
    }

    /** "Send me your manifest." */
    public record Hello() implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC =
                PacketCodec.of(Hello::write, Hello::read);

        private static void write(Hello payload, RegistryByteBuf buf) {
        }

        private static Hello read(RegistryByteBuf buf) {
            return new Hello();
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "These files differ from my copy" - always checked against the manifest. */
    public record Need(List<String> names) implements CustomPayload {
        public static final CustomPayload.Id<Need> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_need"));
        public static final PacketCodec<RegistryByteBuf, Need> CODEC =
                PacketCodec.of(Need::write, Need::read);

        private static void write(Need payload, RegistryByteBuf buf) {
            List<String> names = payload.names() == null ? List.of() : payload.names();
            buf.writeVarInt(names.size());
            for (String name : names) {
                buf.writeString(name == null ? "" : name, MAX_NAME_CHARS);
            }
        }

        private static Need read(RegistryByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_WIRE_FILES) {
                throw new DecoderException("AtomChat pack need: bad file count " + count);
            }
            List<String> names = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                names.add(buf.readString(MAX_NAME_CHARS));
            }
            return new Need(names);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "I am done: here is whether the pack verified." */
    public record Ack(boolean ok, String detail) implements CustomPayload {
        public static final CustomPayload.Id<Ack> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_ack"));
        public static final PacketCodec<RegistryByteBuf, Ack> CODEC =
                PacketCodec.of(Ack::write, Ack::read);

        private static void write(Ack payload, RegistryByteBuf buf) {
            buf.writeBoolean(payload.ok());
            buf.writeString(payload.detail() == null ? "" : payload.detail(), MAX_DETAIL_CHARS);
        }

        private static Ack read(RegistryByteBuf buf) {
            return new Ack(buf.readBoolean(), buf.readString(MAX_DETAIL_CHARS));
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Everything the client needs to decide what to fetch, plus the server identity. */
    public record Manifest(boolean enabled, String packHash, String serverName, byte[] icon,
                           List<PackFile> files, List<String> phrases) implements CustomPayload {
        public static final CustomPayload.Id<Manifest> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_manifest"));
        public static final PacketCodec<RegistryByteBuf, Manifest> CODEC =
                PacketCodec.of(Manifest::write, Manifest::read);

        private static void write(Manifest payload, RegistryByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeString(payload.packHash() == null ? "" : payload.packHash(), MAX_HASH_CHARS);
            buf.writeString(payload.serverName() == null ? "" : payload.serverName());
            buf.writeByteArray(payload.icon() == null ? new byte[0] : payload.icon());
            List<PackFile> files = payload.files() == null ? List.of() : payload.files();
            buf.writeVarInt(files.size());
            for (PackFile file : files) {
                buf.writeString(file.name() == null ? "" : file.name(), MAX_NAME_CHARS);
                buf.writeString(file.sha256() == null ? "" : file.sha256(), MAX_HASH_CHARS);
                buf.writeVarInt(file.size());
            }
            List<String> phrases = payload.phrases() == null ? List.of() : payload.phrases();
            buf.writeVarInt(phrases.size());
            for (String phrase : phrases) {
                buf.writeString(phrase == null ? "" : phrase);
            }
        }

        private static Manifest read(RegistryByteBuf buf) {
            boolean enabled = buf.readBoolean();
            String packHash = buf.readString(MAX_HASH_CHARS);
            String serverName = buf.readString();
            byte[] icon = buf.readByteArray();
            int fileCount = buf.readVarInt();
            if (fileCount < 0 || fileCount > MAX_WIRE_FILES) {
                throw new DecoderException("AtomChat pack manifest: bad file count " + fileCount);
            }
            List<PackFile> files = new ArrayList<>(fileCount);
            for (int i = 0; i < fileCount; i++) {
                files.add(new PackFile(buf.readString(MAX_NAME_CHARS), buf.readString(MAX_HASH_CHARS),
                        buf.readVarInt()));
            }
            int phraseCount = buf.readVarInt();
            if (phraseCount < 0 || phraseCount > MAX_WIRE_PHRASES) {
                throw new DecoderException("AtomChat pack manifest: bad phrase count " + phraseCount);
            }
            List<String> phrases = new ArrayList<>(phraseCount);
            for (int i = 0; i < phraseCount; i++) {
                phrases.add(buf.readString());
            }
            return new Manifest(enabled, packHash, serverName, icon, files, phrases);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** One 24 KiB slice of one file. */
    public record Chunk(String name, int offset, int totalBytes, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<Chunk> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_chunk"));
        public static final PacketCodec<RegistryByteBuf, Chunk> CODEC =
                PacketCodec.of(Chunk::write, Chunk::read);

        private static void write(Chunk payload, RegistryByteBuf buf) {
            buf.writeString(payload.name() == null ? "" : payload.name(), MAX_NAME_CHARS);
            buf.writeVarInt(payload.offset());
            buf.writeVarInt(payload.totalBytes());
            buf.writeByteArray(payload.data() == null ? new byte[0] : payload.data());
        }

        private static Chunk read(RegistryByteBuf buf) {
            String name = buf.readString(MAX_NAME_CHARS);
            int offset = buf.readVarInt();
            int total = buf.readVarInt();
            byte[] data = buf.readByteArray(MediaIds.CHUNK_BYTES);
            return new Chunk(name, offset, total, data);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "Everything you asked for has been sent." */
    public record Done(int fileCount) implements CustomPayload {
        public static final CustomPayload.Id<Done> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_done"));
        public static final PacketCodec<RegistryByteBuf, Done> CODEC =
                PacketCodec.of(Done::write, Done::read);

        private static void write(Done payload, RegistryByteBuf buf) {
            buf.writeVarInt(payload.fileCount());
        }

        private static Done read(RegistryByteBuf buf) {
            return new Done(buf.readVarInt());
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Registers every codec; must run on both logical sides (common init). */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Hello.ID, Hello.CODEC);
        PayloadTypeRegistry.playC2S().register(Need.ID, Need.CODEC);
        PayloadTypeRegistry.playC2S().register(Ack.ID, Ack.CODEC);
        PayloadTypeRegistry.playS2C().register(Manifest.ID, Manifest.CODEC);
        PayloadTypeRegistry.playS2C().register(Chunk.ID, Chunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Done.ID, Done.CODEC);
    }
}
