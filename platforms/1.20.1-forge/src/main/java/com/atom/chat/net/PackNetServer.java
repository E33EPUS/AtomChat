package com.atom.chat.net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The server half of the pack seam: where the shared logic meets Forge.
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
            PackPayloads.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    toPayload(message));
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

    /**
     * Neutral message to this target's payload. S2C only: the client unpacks those.
     *
     * <p>{@code instanceof} rather than a pattern switch: this target compiles at
     * Java 17, where those are still a preview feature. The other two are 21 and
     * use the switch, which the compiler keeps exhaustive; here a message type
     * nobody handles fails loudly instead of being caught at compile time.
     */
    private static Object toPayload(PackMessage.S2C message) {
        if (message instanceof PackMessage.Manifest manifest) {
            return new PackPayloads.Manifest(manifest.enabled(), manifest.packHash(),
                    manifest.serverName(), manifest.icon(), packFiles(manifest.files()),
                    manifest.phrases());
        }
        if (message instanceof PackMessage.Chunk chunk) {
            return new PackPayloads.Chunk(chunk.name(), chunk.offset(), chunk.totalBytes(),
                    chunk.data());
        }
        if (message instanceof PackMessage.Done done) {
            return new PackPayloads.Done(done.fileCount());
        }
        throw new IllegalArgumentException("unhandled S2C message: " + message);
    }

    private static List<PackPayloads.PackFile> packFiles(List<PackMessage.PackFile> files) {
        List<PackPayloads.PackFile> converted = new ArrayList<>(files.size());
        for (PackMessage.PackFile file : files) {
            converted.add(new PackPayloads.PackFile(file.name(), file.sha256(), file.size()));
        }
        return converted;
    }

    /** This target's C2S payload to a neutral message; called from the channel setup. */
    static PackMessage.Ack toMessage(PackPayloads.Ack payload) {
        return new PackMessage.Ack(payload.ok(), payload.detail());
    }
}
