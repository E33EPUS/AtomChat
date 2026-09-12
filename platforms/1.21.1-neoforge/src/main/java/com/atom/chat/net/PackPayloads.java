package com.atom.chat.net;

import com.atom.chat.AtomChat;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
    public record Hello() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Hello> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AtomChat.MOD_ID, "dist_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> STREAM_CODEC =
                StreamCodec.of(Hello::write, Hello::read);

        private static void write(RegistryFriendlyByteBuf buf, Hello payload) {
        }

        private static Hello read(RegistryFriendlyByteBuf buf) {
            return new Hello();
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
            List<String> names = payload.names() == null ? List.of() : payload.names();
            buf.writeVarInt(names.size());
            for (String name : names) {
                buf.writeUtf(name == null ? "" : name, MAX_NAME_CHARS);
            }
        }

        private static Need read(RegistryFriendlyByteBuf buf) {
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
            buf.writeBoolean(payload.ok());
            buf.writeUtf(payload.detail() == null ? "" : payload.detail(), MAX_DETAIL_CHARS);
        }

        private static Ack read(RegistryFriendlyByteBuf buf) {
            return new Ack(buf.readBoolean(), buf.readUtf(MAX_DETAIL_CHARS));
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

        private static Manifest read(RegistryFriendlyByteBuf buf) {
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
            buf.writeUtf(payload.name() == null ? "" : payload.name(), MAX_NAME_CHARS);
            buf.writeVarInt(payload.offset());
            buf.writeVarInt(payload.totalBytes());
            buf.writeByteArray(payload.data() == null ? new byte[0] : payload.data());
        }

        private static Chunk read(RegistryFriendlyByteBuf buf) {
            String name = buf.readUtf(MAX_NAME_CHARS);
            int offset = buf.readVarInt();
            int total = buf.readVarInt();
            byte[] data = buf.readByteArray(MediaIds.CHUNK_BYTES);
            return new Chunk(name, offset, total, data);
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
            buf.writeVarInt(payload.fileCount());
        }

        private static Done read(RegistryFriendlyByteBuf buf) {
            return new Done(buf.readVarInt());
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
                        () -> PackSyncServer.onAck(ctx.player(), PackNetServer.toMessage(payload))));
        // The S2C receivers touch client-only classes; the dist check keeps those
        // references out of a dedicated server's class loading (e33chat pattern).
        registrar.playToClient(Manifest.TYPE, Manifest.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> PackSyncClient.onManifest(PackNetClient.toMessage(payload)));
            }
        });
        registrar.playToClient(Chunk.TYPE, Chunk.STREAM_CODEC, (payload, ctx) -> {
            if (dist() == Dist.CLIENT) {
                ctx.enqueueWork(() -> PackSyncClient.onChunk(PackNetClient.toMessage(payload)));
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
