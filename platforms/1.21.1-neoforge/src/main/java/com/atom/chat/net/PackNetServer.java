package com.atom.chat.net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * The server half of the pack seam: where the shared logic meets NeoForge.
 *
 * <p>Two jobs, both mechanical: the send port ({@link Net.ServerSide}) and the
 * server facts the shared logic asks for ({@link Host.ServerSide}). The payload
 * conversion lives with the payload types (see {@link PackPayloads}).
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
            PacketDistributor.sendToPlayer(sender, PackPayloads.fromMessage(message));
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
}
