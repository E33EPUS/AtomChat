package com.atom.chat.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server half of the pack seam: where the shared logic meets NeoForge.
 *
 * <p>Three jobs, all of them mechanical:
 * <ul>
 *   <li>the send port ({@link Net.ServerSide}) - neutral message to payload;</li>
 *   <li>the server facts the shared logic asks for ({@link Host.ServerSide});</li>
 *   <li>the payload to neutral message conversion for what arrives (C2S).</li>
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
    }

    private PackNetServer() {
    }

    @Override
    public void sendToPlayer(Object player, PackMessage.S2C message) {
        if (player instanceof ServerPlayer sender) {
            PacketDistributor.sendToPlayer(sender, toPayload(message));
        }
    }

    @Override
    public UUID playerId(Object player) {
        return player instanceof ServerPlayer sender ? sender.getUUID() : null;
    }

    @Override
    public String playerName(Object player) {
        return player instanceof ServerPlayer sender ? sender.getName().getString() : "";
    }

    @Override
    public Object playerById(Object server, UUID id) {
        return server instanceof MinecraftServer minecraftServer
                ? minecraftServer.getPlayerList().getPlayer(id)
                : null;
    }

    @Override
    public Object serverOf(Object player) {
        return player instanceof ServerPlayer sender ? sender.getServer() : null;
    }

    @Override
    public String motd(Object server) {
        return server instanceof MinecraftServer minecraftServer ? minecraftServer.getMotd() : "";
    }

    @Override
    public boolean dedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    /** Neutral message to this target's payload. S2C only: the client unpacks those. */
    private static CustomPacketPayload toPayload(PackMessage.S2C message) {
        return switch (message) {
            case PackMessage.Manifest manifest -> new PackPayloads.Manifest(manifest.enabled(),
                    manifest.packHash(), manifest.serverName(), manifest.icon(),
                    packFiles(manifest.files()), manifest.phrases());
            case PackMessage.Chunk chunk -> new PackPayloads.Chunk(chunk.name(), chunk.offset(),
                    chunk.totalBytes(), chunk.data());
            case PackMessage.Done done -> new PackPayloads.Done(done.fileCount());
        };
    }

    private static List<PackPayloads.PackFile> packFiles(List<PackMessage.PackFile> files) {
        List<PackPayloads.PackFile> converted = new ArrayList<>(files.size());
        for (PackMessage.PackFile file : files) {
            converted.add(new PackPayloads.PackFile(file.name(), file.sha256(), file.size()));
        }
        return converted;
    }

    /** This target's C2S payload to a neutral message; called from the payload registrar. */
    static PackMessage.Ack toMessage(PackPayloads.Ack payload) {
        return new PackMessage.Ack(payload.ok(), payload.detail());
    }
}
