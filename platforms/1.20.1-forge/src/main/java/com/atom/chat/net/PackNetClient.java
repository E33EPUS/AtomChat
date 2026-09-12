package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of the pack seam: where the shared logic meets Forge.
 *
 * <p>Mirrors {@link PackNetServer}'s counterpart on the server side - send port,
 * client facts, payload conversion - but names client types freely: nothing
 * references this class except the client entrypoint and the dist-guarded
 * branches of the channel setup.
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
        PackPayloads.CHANNEL.sendToServer(toPayload(message));
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

    /**
     * Neutral message to this target's payload. C2S only: the server unpacks those.
     *
     * <p>{@code instanceof} rather than a pattern switch - see {@link PackNetServer}
     * for why (Java 17 target, preview-only there).
     */
    private static Object toPayload(PackMessage.C2S message) {
        if (message instanceof PackMessage.Hello) {
            return new PackPayloads.Hello();
        }
        if (message instanceof PackMessage.Need need) {
            return new PackPayloads.Need(need.names());
        }
        if (message instanceof PackMessage.Ack ack) {
            return new PackPayloads.Ack(ack.ok(), ack.detail());
        }
        throw new IllegalArgumentException("unhandled C2S message: " + message);
    }

    /** This target's S2C payload to a neutral message; called from the channel setup. */
    static PackMessage.Manifest toMessage(PackPayloads.Manifest payload) {
        List<PackMessage.PackFile> files = new ArrayList<>(payload.files().size());
        for (PackPayloads.PackFile file : payload.files()) {
            files.add(new PackMessage.PackFile(file.name(), file.sha256(), file.size()));
        }
        return new PackMessage.Manifest(payload.enabled(), payload.packHash(), payload.serverName(),
                payload.icon(), files, payload.phrases());
    }

    static PackMessage.Chunk toMessage(PackPayloads.Chunk payload) {
        return new PackMessage.Chunk(payload.name(), payload.offset(), payload.totalBytes(),
                payload.data());
    }
}
