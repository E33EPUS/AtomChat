package com.atom.chat.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of the pack seam: where the shared logic meets NeoForge.
 *
 * <p>Mirrors {@link PackNetServer}'s counterpart on the server side - send port,
 * client facts, payload conversion - but names client types freely: nothing
 * references this class except the client entrypoint and the dist-guarded
 * branches of the payload registrar.
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
        PacketDistributor.sendToServer(toPayload(message));
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

    /** Neutral message to this target's payload. C2S only: the server unpacks those. */
    private static CustomPacketPayload toPayload(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello ignored -> new PackPayloads.Hello();
            case PackMessage.Need need -> new PackPayloads.Need(need.names());
            case PackMessage.Ack ack -> new PackPayloads.Ack(ack.ok(), ack.detail());
        };
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

    /** This target's S2C payload to a neutral message; called from the payload registrar. */
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
