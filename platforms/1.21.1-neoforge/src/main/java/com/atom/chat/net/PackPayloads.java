package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
 * genuinely NeoForge's - the payload types the loader demands, the channel ids,
 * and the conversion to and from the neutral {@link PackMessage}. Every read path
 * still validates before allocating: a peer that sends an absurd file count, name
 * or hash is a protocol violation, not something to store.
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

    /**
     * Direction-wide dispatch for the send port: one neutral message in, this
     * target's payload out. The switch stays exhaustive because the two
     * direction interfaces are sealed.
     */
    static CustomPacketPayload fromMessage(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello hello -> fromMessage(hello);
            case PackMessage.Need need -> fromMessage(need);
            case PackMessage.Ack ack -> fromMessage(ack);
        };
    }

    static CustomPacketPayload fromMessage(PackMessage.S2C message) {
        return switch (message) {
            case PackMessage.Manifest manifest -> fromMessage(manifest);
            case PackMessage.Chunk chunk -> fromMessage(chunk);
            case PackMessage.Done done -> fromMessage(done);
        };
    }

    // ---------------------------------------------------------------- payloads

    /** "Send me your manifest." */
    public record Hello() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hello> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> STREAM_CODEC =
                StreamCodec.of(Hello::write, Hello::read);

        private static void write(RegistryFriendlyByteBuf buf, Hello payload) {
            Wire.writeHello(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Hello read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readHello(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "These files differ from my copy" - always checked against the manifest. */
    public record Need(List<String> names) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Need> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_need"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Need> STREAM_CODEC =
                StreamCodec.of(Need::write, Need::read);

        private static void write(RegistryFriendlyByteBuf buf, Need payload) {
            Wire.writeNeed(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Need read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readNeed(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "I am done: here is whether the pack verified." */
    public record Ack(boolean ok, String detail) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Ack> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ack> STREAM_CODEC =
                StreamCodec.of(Ack::write, Ack::read);

        private static void write(RegistryFriendlyByteBuf buf, Ack payload) {
            Wire.writeAck(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Ack read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readAck(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Everything the client needs to decide what to fetch, plus the server identity. */
    public record Manifest(boolean enabled, String packHash, String serverName, byte[] icon,
                           List<PackFile> files, List<String> phrases) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Manifest> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_manifest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Manifest> STREAM_CODEC =
                StreamCodec.of(Manifest::write, Manifest::read);

        private static void write(RegistryFriendlyByteBuf buf, Manifest payload) {
            Wire.writeManifest(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Manifest read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readManifest(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One 24 KiB slice of one file. */
    public record Chunk(String name, int offset, int totalBytes, byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Chunk> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> STREAM_CODEC =
                StreamCodec.of(Chunk::write, Chunk::read);

        private static void write(RegistryFriendlyByteBuf buf, Chunk payload) {
            Wire.writeChunk(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Chunk read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readChunk(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "Everything you asked for has been sent." */
    public record Done(int fileCount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Done> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Done> STREAM_CODEC =
                StreamCodec.of(Done::write, Done::read);

        private static void write(RegistryFriendlyByteBuf buf, Done payload) {
            Wire.writeDone(new NeoForgeWireIo(buf), toMessage(payload));
        }

        private static Done read(RegistryFriendlyByteBuf buf) {
            return fromMessage(Wire.readDone(new NeoForgeWireIo(buf)));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Registers every payload on the NeoForge payload bus (common entry). */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Hello.TYPE, Hello.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackSyncServer.onHello(ctx.player())));
        registrar.playToServer(Need.TYPE, Need.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> PackSyncServer.onNeed(ctx.player(), payload.names())));
        registrar.playToServer(Ack.TYPE, Ack.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(
                        () -> PackSyncServer.onAck(ctx.player(), toMessage(payload))));
        // The S2C receivers touch client-only classes; the dist check keeps those
        // references out of a dedicated server's class loading (e33chat pattern).
        registrar.playToClient(Manifest.TYPE, Manifest.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> PackSyncClient.onManifest(toMessage(payload)));
            }
        });
        registrar.playToClient(Chunk.TYPE, Chunk.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> PackSyncClient.onChunk(toMessage(payload)));
            }
        });
        registrar.playToClient(Done.TYPE, Done.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> PackSyncClient.onDone(payload.fileCount()));
            }
        });
    }

    private static Dist dist() {
        return FMLEnvironment.dist;
    }
}
