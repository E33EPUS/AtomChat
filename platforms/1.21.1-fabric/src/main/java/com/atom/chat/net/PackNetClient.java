package com.atom.chat.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of the pack seam: where the shared logic meets Fabric.
 *
 * <p>Mirrors {@link PackNetServer}'s counterpart on the server side - send port,
 * client facts, payload conversion - but names client types freely: nothing
 * references this class except the client entrypoint.
 *
 * <p>Fabric registers the S2C receivers here rather than in the payload types,
 * so the conversion lives next to the port it belongs to.
 */
public final class PackNetClient implements Net.ClientSide, Host.ClientSide {

    /** Installed from the client entrypoint, before any payload can arrive. */
    public static void install() {
        PackNetClient impl = new PackNetClient();
        Net.installClientSide(impl);
        Host.installClientSide(impl);
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Manifest.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onManifest(toMessage(payload))));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Chunk.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onChunk(toMessage(payload))));
        ClientPlayNetworking.registerGlobalReceiver(PackPayloads.Done.ID,
                (payload, context) -> context.client().execute(
                        () -> PackSyncClient.onDone(payload.fileCount())));
    }

    private PackNetClient() {
    }

    @Override
    public void sendToServer(PackMessage.C2S message) {
        ClientPlayNetworking.send(toPayload(message));
    }

    /**
     * Fabric registers one channel per payload, so the probe has to name the kind
     * it means - unlike Forge, where the three share a single channel.
     */
    @Override
    public boolean canSendToServer(PackMessage.C2S message) {
        return ClientPlayNetworking.canSend(channelOf(message));
    }

    @Override
    public void execute(Runnable work) {
        MinecraftClient.getInstance().execute(work);
    }

    @Override
    public String serverAddress() {
        ServerInfo info = MinecraftClient.getInstance().getCurrentServerEntry();
        return info == null ? null : info.address;
    }

    @Override
    public String levelName() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getServer() == null
                ? null
                : client.getServer().getSaveProperties().getLevelName();
    }

    /** Neutral message to this target's payload. C2S only: the server unpacks those. */
    private static CustomPayload toPayload(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello ignored -> new PackPayloads.Hello();
            case PackMessage.Need need -> new PackPayloads.Need(need.names());
            case PackMessage.Ack ack -> new PackPayloads.Ack(ack.ok(), ack.detail());
        };
    }

    private static CustomPayload.Id<?> channelOf(PackMessage.C2S message) {
        return switch (message) {
            case PackMessage.Hello ignored -> PackPayloads.Hello.ID;
            case PackMessage.Need ignored -> PackPayloads.Need.ID;
            case PackMessage.Ack ignored -> PackPayloads.Ack.ID;
        };
    }

    /** This target's S2C payload to a neutral message; called from the receivers. */
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
