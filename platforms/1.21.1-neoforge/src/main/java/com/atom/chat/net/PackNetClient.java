package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * The client half of the pack seam: where the shared logic meets NeoForge.
 *
 * <p>Send port and client facts only - the payload conversion lives with the
 * payload types (see {@link PackPayloads}), and this class names client types
 * freely: nothing references it except the client entrypoint and the
 * dist-guarded branches of the payload registrar.
 */
public final class PackNetClient implements Net.ClientSide, Host.ClientSide {

    /** Installed from the client entrypoint, before any payload can arrive. */
    public static void install() {
        PackNetClient impl = new PackNetClient();
        Net.installClientSide(impl);
        Host.installClientSide(impl);
    }

    private PackNetClient() {
    }

    @Override
    public void sendToServer(PackMessage.C2S message) {
        PacketDistributor.sendToServer(PackPayloads.fromMessage(message));
    }

    @Override
    public boolean canSendToServer(PackMessage.C2S message) {
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            return connection != null && NetworkRegistry.hasChannel(connection, channelOf(message));
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void execute(Runnable work) {
        Minecraft.getInstance().execute(work);
    }

    @Override
    public String serverAddress() {
        ServerData data = Minecraft.getInstance().getCurrentServer();
        return data == null ? null : data.ip;
    }

    @Override
    public String levelName() {
        return Minecraft.getInstance().getSingleplayerServer() == null
                ? null
                : Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName();
    }

    /**
     * The channel a capability probe asks about.
     *
     * <p>NeoForge registers one channel per payload, so the probe has to name the
     * kind it means - unlike Forge, where the three share a single channel.
     */
    private static ResourceLocation channelOf(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello ignored -> PackPayloads.Hello.TYPE.id();
            case PackMessage.Need ignored -> PackPayloads.Need.TYPE.id();
            case PackMessage.Ack ignored -> PackPayloads.Ack.TYPE.id();
        };
    }
}
