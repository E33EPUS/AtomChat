package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The client half of the pack seam: where the shared logic meets Forge.
 *
 * <p>Send port and client facts only - the payload conversion lives with the
 * payload types (see {@link PackPayloads}), and this class names client types
 * freely: nothing references it except the client entrypoint and the
 * dist-guarded branches of the channel setup.
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
        PackPayloads.CHANNEL.sendToServer(PackPayloads.fromMessage(message));
    }

    /**
     * All three C2S payloads share one channel here, so the probe ignores which
     * kind it was asked about - unlike NeoForge and Fabric, where each payload
     * has its own channel and the answer can differ per kind.
     */
    @Override
    public boolean canSendToServer(PackMessage.C2S message) {
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            return connection != null
                    && PackPayloads.CHANNEL.isRemotePresent(connection.getConnection());
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
}
