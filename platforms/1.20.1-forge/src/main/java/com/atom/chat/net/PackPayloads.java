package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

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
 * genuinely Forge's - the packet ids, the channel, and the conversion to and from
 * the neutral {@link PackMessage}. Every read path still validates before
 * allocating: a peer that sends an absurd file count, name or hash is a protocol
 * violation, not something to store.
 */
public final class PackPayloads {
    /** Bumped when the packet layout changes; the channel rejects mismatches. */
    private static final String PROTOCOL = "1";

    /** Wire contract: the channel name and packet ids must never be renamed or reordered. */
    public static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist");
    public static final int ID_HELLO = 0;
    public static final int ID_NEED = 1;
    public static final int ID_ACK = 2;
    public static final int ID_MANIFEST = 3;
    public static final int ID_CHUNK = 4;
    public static final int ID_DONE = 5;

    public static SimpleChannel CHANNEL;

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
     * target's payload out.
     *
     * <p>{@code instanceof} chains rather than pattern switches: this target
     * compiles at Java 17, where those are still a preview feature (the other two
     * are 21 and switch exhaustively).
     */
    static Object fromMessage(PackMessage.C2S message) {
        if (message instanceof PackMessage.Hello hello) {
            return fromMessage(hello);
        }
        if (message instanceof PackMessage.Need need) {
            return fromMessage(need);
        }
        if (message instanceof PackMessage.Ack ack) {
            return fromMessage(ack);
        }
        throw new IllegalArgumentException("unhandled C2S message: " + message);
    }

    static Object fromMessage(PackMessage.S2C message) {
        if (message instanceof PackMessage.Manifest manifest) {
            return fromMessage(manifest);
        }
        if (message instanceof PackMessage.Chunk chunk) {
            return fromMessage(chunk);
        }
        if (message instanceof PackMessage.Done done) {
            return fromMessage(done);
        }
        throw new IllegalArgumentException("unhandled S2C message: " + message);
    }

    // ---------------------------------------------------------------- payloads

    /** "Send me your manifest." */
    public record Hello() {

        public static void encode(Hello payload, FriendlyByteBuf buf) {
            Wire.writeHello(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Hello decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readHello(new ForgeWireIo(buf)));
        }

    }

    /** "These files differ from my copy" - always checked against the manifest. */
    public record Need(List<String> names) {

        public static void encode(Need payload, FriendlyByteBuf buf) {
            Wire.writeNeed(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Need decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readNeed(new ForgeWireIo(buf)));
        }

    }

    /** "I am done: here is whether the pack verified." */
    public record Ack(boolean ok, String detail) {

        public static void encode(Ack payload, FriendlyByteBuf buf) {
            Wire.writeAck(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Ack decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readAck(new ForgeWireIo(buf)));
        }

    }

    /** Everything the client needs to decide what to fetch, plus the server identity. */
    public record Manifest(boolean enabled, String packHash, String serverName, byte[] icon,
                           List<PackFile> files, List<String> phrases) {

        public static void encode(Manifest payload, FriendlyByteBuf buf) {
            Wire.writeManifest(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Manifest decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readManifest(new ForgeWireIo(buf)));
        }

    }

    /** One 24 KiB slice of one file. */
    public record Chunk(String name, int offset, int totalBytes, byte[] data) {

        public static void encode(Chunk payload, FriendlyByteBuf buf) {
            Wire.writeChunk(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Chunk decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readChunk(new ForgeWireIo(buf)));
        }

    }

    /** "Everything you asked for has been sent." */
    public record Done(int fileCount) {

        public static void encode(Done payload, FriendlyByteBuf buf) {
            Wire.writeDone(new ForgeWireIo(buf), toMessage(payload));
        }

        public static Done decode(FriendlyByteBuf buf) {
            return fromMessage(Wire.readDone(new ForgeWireIo(buf)));
        }

    }

    /** Builds the channel and registers every packet id. Common setup. */
    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_NAME, () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL),
                NetworkRegistry.acceptMissingOr(PROTOCOL));

        CHANNEL.messageBuilder(Hello.class, ID_HELLO)
                .encoder(Hello::encode)
                .decoder(Hello::decode)
                .consumerMainThread((payload, ctx) -> {
                    ctx.get().enqueueWork(() -> PackSyncServer.onHello(ctx.get().getSender()));
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Need.class, ID_NEED)
                .encoder(Need::encode)
                .decoder(Need::decode)
                .consumerMainThread((payload, ctx) -> {
                    ctx.get().enqueueWork(() -> PackSyncServer.onNeed(ctx.get().getSender(), payload.names()));
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Ack.class, ID_ACK)
                .encoder(Ack::encode)
                .decoder(Ack::decode)
                .consumerMainThread((payload, ctx) -> {
                    ctx.get().enqueueWork(() -> PackSyncServer.onAck(ctx.get().getSender(),
                            toMessage(payload)));
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // The S2C handlers touch client-only classes; the body only runs on a
        // client, but the dist guard keeps class loading explicit.
        CHANNEL.messageBuilder(Manifest.class, ID_MANIFEST)
                .encoder(Manifest::encode)
                .decoder(Manifest::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> PackSyncClient.onManifest(toMessage(payload)));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Chunk.class, ID_CHUNK)
                .encoder(Chunk::encode)
                .decoder(Chunk::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> PackSyncClient.onChunk(toMessage(payload)));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Done.class, ID_DONE)
                .encoder(Done::encode)
                .decoder(Done::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> PackSyncClient.onDone(payload.fileCount()));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

    }
}
