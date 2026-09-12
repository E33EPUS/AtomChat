package com.atom.chat.net;

import com.atom.chat.AtomChat;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server half of the pack seam: where the shared logic meets Fabric.
 *
 * <p>Three jobs, all of them mechanical:
 * <ul>
 *   <li>the send port ({@link Net.ServerSide}) - neutral message to payload;</li>
 *   <li>the server facts the shared logic asks for ({@link Host.ServerSide});</li>
 *   <li>the receivers, which unpack a payload into a neutral message and hand it
 *       to the shared logic (Fabric registers those here, not in the payload
 *       types).</li>
 * </ul>
 *
 * <p>Loaded on a dedicated server too, so it must not name a client type - the
 * dist guard scans this file. The client half lives in {@link PackNetClient}.
 */
public final class PackNetServer implements Net.ServerSide, Host.ServerSide {

    /** Installed from the common entrypoint, before any payload can arrive. */
    public static void install() {
        PackNetServer impl = new PackNetServer();
        Net.installServerSide(impl);
        Host.installServerSide(impl);
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Hello.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onHello(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Need.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onNeed(context.player(), payload.names())));
        ServerPlayNetworking.registerGlobalReceiver(PackPayloads.Ack.ID,
                (payload, context) -> onServer(context.player(),
                        () -> PackSyncServer.onAck(context.player(), toMessage(payload))));
        AtomChat.LOGGER.info("AtomChat pack distribution registered (server side)");
    }

    private PackNetServer() {
    }

    /**
     * File reads and hashing belong off the network thread.
     *
     * <p>The tick hook in the entrypoint drains the queues, so the work handed to
     * the server thread here only builds the queue.
     */
    private static void onServer(ServerPlayerEntity player, Runnable work) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server != null) {
            server.execute(work);
        }
    }

    @Override
    public void sendToPlayer(Object player, PackMessage.S2C message) {
        if (player instanceof ServerPlayerEntity sender) {
            ServerPlayNetworking.send(sender, toPayload(message));
        }
    }

    @Override
    public UUID playerId(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getUuid() : null;
    }

    @Override
    public String playerName(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getName().getString() : "";
    }

    @Override
    public Object playerById(Object server, UUID id) {
        return server instanceof MinecraftServer minecraftServer
                ? minecraftServer.getPlayerManager().getPlayer(id)
                : null;
    }

    @Override
    public Object serverOf(Object player) {
        return player instanceof ServerPlayerEntity sender ? sender.getServer() : null;
    }

    @Override
    public String motd(Object server) {
        return server instanceof MinecraftServer minecraftServer
                ? minecraftServer.getServerMotd()
                : "";
    }

    @Override
    public boolean dedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    /** Neutral message to this target's payload. S2C only: the client unpacks those. */
    private static PackPayloads.Manifest manifestOf(PackMessage.Manifest manifest) {
        List<PackPayloads.PackFile> files = new ArrayList<>(manifest.files().size());
        for (PackMessage.PackFile file : manifest.files()) {
            files.add(new PackPayloads.PackFile(file.name(), file.sha256(), file.size()));
        }
        return new PackPayloads.Manifest(manifest.enabled(), manifest.packHash(),
                manifest.serverName(), manifest.icon(), files, manifest.phrases());
    }

    private static CustomPayload toPayload(PackMessage.S2C message) {
        return switch (message) {
            case PackMessage.Manifest manifest -> manifestOf(manifest);
            case PackMessage.Chunk chunk -> new PackPayloads.Chunk(chunk.name(), chunk.offset(),
                    chunk.totalBytes(), chunk.data());
            case PackMessage.Done done -> new PackPayloads.Done(done.fileCount());
        };
    }

    /** This target's C2S payload to a neutral message; called from the receivers. */
    static PackMessage.Ack toMessage(PackPayloads.Ack payload) {
        return new PackMessage.Ack(payload.ok(), payload.detail());
    }
}
