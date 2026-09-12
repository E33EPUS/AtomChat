package com.atom.chat.net;

import com.atom.chat.AtomChat;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

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

    /** "Send me your manifest." */
    public record Hello() {

        public static void encode(Hello payload, FriendlyByteBuf buf) {
        }

        public static Hello decode(FriendlyByteBuf buf) {
            return new Hello();
        }

    }

    /** "These files differ from my copy" - always checked against the manifest. */
    public record Need(List<String> names) {

        public static void encode(Need payload, FriendlyByteBuf buf) {
            List<String> names = payload.names() == null ? List.of() : payload.names();
            buf.writeVarInt(names.size());
            for (String name : names) {
                buf.writeUtf(name == null ? "" : name, MAX_NAME_CHARS);
            }
        }

        public static Need decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_WIRE_FILES) {
                throw new DecoderException("AtomChat pack need: bad file count " + count);
            }
            List<String> names = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                names.add(buf.readUtf(MAX_NAME_CHARS));
            }
            return new Need(names);
        }

    }

    /** "I am done: here is whether the pack verified." */
    public record Ack(boolean ok, String detail) {

        public static void encode(Ack payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.ok());
            buf.writeUtf(payload.detail() == null ? "" : payload.detail(), MAX_DETAIL_CHARS);
        }

        public static Ack decode(FriendlyByteBuf buf) {
            return new Ack(buf.readBoolean(), buf.readUtf(MAX_DETAIL_CHARS));
        }

    }

    /** Everything the client needs to decide what to fetch, plus the server identity. */
    public record Manifest(boolean enabled, String packHash, String serverName, byte[] icon,
                           List<PackFile> files, List<String> phrases) {

        public static void encode(Manifest payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeUtf(payload.packHash() == null ? "" : payload.packHash(), MAX_HASH_CHARS);
            buf.writeUtf(payload.serverName() == null ? "" : payload.serverName());
            buf.writeByteArray(payload.icon() == null ? new byte[0] : payload.icon());
            List<PackFile> files = payload.files() == null ? List.of() : payload.files();
            buf.writeVarInt(files.size());
            for (PackFile file : files) {
                buf.writeUtf(file.name() == null ? "" : file.name(), MAX_NAME_CHARS);
                buf.writeUtf(file.sha256() == null ? "" : file.sha256(), MAX_HASH_CHARS);
                buf.writeVarInt(file.size());
            }
            List<String> phrases = payload.phrases() == null ? List.of() : payload.phrases();
            buf.writeVarInt(phrases.size());
            for (String phrase : phrases) {
                buf.writeUtf(phrase == null ? "" : phrase);
            }
        }

        public static Manifest decode(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            String packHash = buf.readUtf(MAX_HASH_CHARS);
            String serverName = buf.readUtf();
            byte[] icon = buf.readByteArray();
            int fileCount = buf.readVarInt();
            if (fileCount < 0 || fileCount > MAX_WIRE_FILES) {
                throw new DecoderException("AtomChat pack manifest: bad file count " + fileCount);
            }
            List<PackFile> files = new ArrayList<>(fileCount);
            for (int i = 0; i < fileCount; i++) {
                files.add(new PackFile(buf.readUtf(MAX_NAME_CHARS), buf.readUtf(MAX_HASH_CHARS),
                        buf.readVarInt()));
            }
            int phraseCount = buf.readVarInt();
            if (phraseCount < 0 || phraseCount > MAX_WIRE_PHRASES) {
                throw new DecoderException("AtomChat pack manifest: bad phrase count " + phraseCount);
            }
            List<String> phrases = new ArrayList<>(phraseCount);
            for (int i = 0; i < phraseCount; i++) {
                phrases.add(buf.readUtf());
            }
            return new Manifest(enabled, packHash, serverName, icon, files, phrases);
        }

    }

    /** One 24 KiB slice of one file. */
    public record Chunk(String name, int offset, int totalBytes, byte[] data) {

        public static void encode(Chunk payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.name() == null ? "" : payload.name(), MAX_NAME_CHARS);
            buf.writeVarInt(payload.offset());
            buf.writeVarInt(payload.totalBytes());
            buf.writeByteArray(payload.data() == null ? new byte[0] : payload.data());
        }

        public static Chunk decode(FriendlyByteBuf buf) {
            String name = buf.readUtf(MAX_NAME_CHARS);
            int offset = buf.readVarInt();
            int total = buf.readVarInt();
            byte[] data = buf.readByteArray();
            return new Chunk(name, offset, total, data);
        }

    }

    /** "Everything you asked for has been sent." */
    public record Done(int fileCount) {

        public static void encode(Done payload, FriendlyByteBuf buf) {
            buf.writeVarInt(payload.fileCount());
        }

        public static Done decode(FriendlyByteBuf buf) {
            return new Done(buf.readVarInt());
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
                            PackNetServer.toMessage(payload)));
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
                    ctx.get().enqueueWork(() -> PackSyncClient.onManifest(PackNetClient.toMessage(payload)));
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(Chunk.class, ID_CHUNK)
                .encoder(Chunk::encode)
                .decoder(Chunk::decode)
                .consumerMainThread((payload, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                    ctx.get().enqueueWork(() -> PackSyncClient.onChunk(PackNetClient.toMessage(payload)));
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
