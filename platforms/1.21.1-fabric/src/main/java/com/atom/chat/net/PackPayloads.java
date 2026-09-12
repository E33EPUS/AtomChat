package com.atom.chat.net;

import com.atom.chat.AtomChat;
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
 * <p>The byte format, its limits and its validation live in {@link Wire}, so the
 * three targets cannot drift apart field by field; this file keeps what is
 * genuinely Fabric's - the payload ids, the codec registration, and the
 * conversion to and from the neutral {@link PackMessage}. Every read path still
 * validates before allocating: a peer that sends an absurd file count, name or
 * hash is a protocol violation, not something to store.
 */
public final class PackPayloads {
    private PackPayloads() {
    }

    /** One manifest entry: name, content hash and size. */
    public record PackFile(String name, String sha256, int size) {
    }

    // ------------------------------------------------------- neutral conversion

    static PackMessage.Hello toMessage(Hello payload) {
        return new PackMessage.Hello();
    }

    static PackMessage.Need toMessage(Need payload) {
        return new PackMessage.Need(payload.names());
    }

    static PackMessage.Ack toMessage(Ack payload) {
        return new PackMessage.Ack(payload.ok(), payload.detail());
    }

    static PackMessage.Manifest toMessage(Manifest payload) {
        return new PackMessage.Manifest(payload.enabled(), payload.packHash(), payload.serverName(),
                payload.icon(), filesToMessage(payload.files()), payload.phrases());
    }

    static PackMessage.Chunk toMessage(Chunk payload) {
        return new PackMessage.Chunk(payload.name(), payload.offset(), payload.totalBytes(),
                payload.data());
    }

    static PackMessage.Done toMessage(Done payload) {
        return new PackMessage.Done(payload.fileCount());
    }

    static Hello fromMessage(PackMessage.Hello message) {
        return new Hello();
    }

    static Need fromMessage(PackMessage.Need message) {
        return new Need(message.names());
    }

    static Ack fromMessage(PackMessage.Ack message) {
        return new Ack(message.ok(), message.detail());
    }

    static Manifest fromMessage(PackMessage.Manifest message) {
        List<PackFile> files = new ArrayList<>(message.files().size());
        for (PackMessage.PackFile file : message.files()) {
            files.add(new PackFile(file.name(), file.sha256(), file.size()));
        }
        return new Manifest(message.enabled(), message.packHash(), message.serverName(),
                message.icon(), files, message.phrases());
    }

    static Chunk fromMessage(PackMessage.Chunk message) {
        return new Chunk(message.name(), message.offset(), message.totalBytes(), message.data());
    }

    static Done fromMessage(PackMessage.Done message) {
        return new Done(message.fileCount());
    }

    private static List<PackMessage.PackFile> filesToMessage(List<PackFile> files) {
        List<PackMessage.PackFile> converted =
                new ArrayList<>(files == null ? 0 : files.size());
        for (PackFile file : files == null ? List.<PackFile>of() : files) {
            converted.add(new PackMessage.PackFile(file.name(), file.sha256(), file.size()));
        }
        return converted;
    }

    /** Direction-wide dispatch for the send port; sealed interfaces keep it exhaustive. */
    static CustomPayload fromMessage(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello hello -> fromMessage(hello);
            case PackMessage.Need need -> fromMessage(need);
            case PackMessage.Ack ack -> fromMessage(ack);
        };
    }

    static CustomPayload fromMessage(PackMessage.S2C message) {
        return switch (message) {
            case PackMessage.Manifest manifest -> fromMessage(manifest);
            case PackMessage.Chunk chunk -> fromMessage(chunk);
            case PackMessage.Done done -> fromMessage(done);
        };
    }

    // ---------------------------------------------------------------- payloads

    /** "Send me your manifest." */
    public record Hello() implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID =
                new CustomPayload.Id<>(Identifier.of(AtomChat.MOD_ID, "dist_hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC =
                PacketCodec.of(Hello::write, Hello::read);

        private static void write(Hello payload, RegistryByteBuf buf) {
            Wire.writeHello(new FabricWireIo(buf), toMessage(payload));
        }

        private static Hello read(RegistryByteBuf buf) {
            return fromMessage(Wire.readHello(new FabricWireIo(buf)));
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
            Wire.writeNeed(new FabricWireIo(buf), toMessage(payload));
        }

        private static Need read(RegistryByteBuf buf) {
            return fromMessage(Wire.readNeed(new FabricWireIo(buf)));
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
            Wire.writeAck(new FabricWireIo(buf), toMessage(payload));
        }

        private static Ack read(RegistryByteBuf buf) {
            return fromMessage(Wire.readAck(new FabricWireIo(buf)));
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
            Wire.writeManifest(new FabricWireIo(buf), toMessage(payload));
        }

        private static Manifest read(RegistryByteBuf buf) {
            return fromMessage(Wire.readManifest(new FabricWireIo(buf)));
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
            Wire.writeChunk(new FabricWireIo(buf), toMessage(payload));
        }

        private static Chunk read(RegistryByteBuf buf) {
            return fromMessage(Wire.readChunk(new FabricWireIo(buf)));
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
            Wire.writeDone(new FabricWireIo(buf), toMessage(payload));
        }

        private static Done read(RegistryByteBuf buf) {
            return fromMessage(Wire.readDone(new FabricWireIo(buf)));
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
