package com.atom.chat.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wire format has to survive a round trip byte for byte. */
class PackPayloadsCodecTest {
    private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    /**
     * The pack codecs only move strings, varints, booleans and byte arrays, so
     * they never ask for a registry entry - which means the buffer needs no
     * bootstrapped registry manager (and the test needs no game bootstrap).
     */
    private static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = buf();
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    @Test
    void helloIsEmpty() {
        assertEquals(new PackPayloads.Hello(), roundTrip(PackPayloads.Hello.STREAM_CODEC, new PackPayloads.Hello()));
    }

    @Test
    void needCarriesNamesInOrder() {
        PackPayloads.Need out = roundTrip(PackPayloads.Need.STREAM_CODEC,
                new PackPayloads.Need(List.of("cat.png", "dog.gif")));

        assertEquals(List.of("cat.png", "dog.gif"), out.names());
    }

    @Test
    void anEmptyNeedIsValid() {
        assertEquals(List.of(), roundTrip(PackPayloads.Need.STREAM_CODEC, new PackPayloads.Need(List.of())).names());
    }

    @Test
    void ackCarriesTheReason() {
        PackPayloads.Ack out = roundTrip(PackPayloads.Ack.STREAM_CODEC, new PackPayloads.Ack(false, "timeout"));

        assertEquals(false, out.ok());
        assertEquals("timeout", out.detail());
    }

    @Test
    void manifestCarriesEverythingTheClientNeeds() {
        PackPayloads.Manifest in = new PackPayloads.Manifest(true, "a".repeat(64), "My Server", ICON,
                List.of(new PackPayloads.PackFile("cat.png", "b".repeat(64), 1234),
                        new PackPayloads.PackFile("dog.gif", "c".repeat(64), 4321)),
                List.of("hi", "第二行 / utf8"));

        PackPayloads.Manifest out = roundTrip(PackPayloads.Manifest.STREAM_CODEC, in);

        assertEquals(true, out.enabled());
        assertEquals(in.packHash(), out.packHash());
        assertEquals("My Server", out.serverName());
        assertArrayEquals(ICON, out.icon());
        assertEquals(2, out.files().size());
        assertEquals("cat.png", out.files().get(0).name());
        assertEquals(1234, out.files().get(0).size());
        assertEquals(in.files().get(1).sha256(), out.files().get(1).sha256());
        assertEquals(List.of("hi", "第二行 / utf8"), out.phrases());
    }

    @Test
    void aDisabledManifestStaysEmpty() {
        PackPayloads.Manifest out = roundTrip(PackPayloads.Manifest.STREAM_CODEC,
                new PackPayloads.Manifest(false, "", "", new byte[0], List.of(), List.of()));

        assertEquals(false, out.enabled());
        assertEquals(0, out.files().size());
        assertTrue(out.phrases().isEmpty());
        assertEquals(0, out.icon().length);
    }

    @Test
    void chunkCarriesItsSlice() {
        byte[] data = "a 24 KiB slice".getBytes(StandardCharsets.UTF_8);

        PackPayloads.Chunk out = roundTrip(PackPayloads.Chunk.STREAM_CODEC,
                new PackPayloads.Chunk("cat.png", 49152, 65536, data));

        assertEquals("cat.png", out.name());
        assertEquals(49152, out.offset());
        assertEquals(65536, out.totalBytes());
        assertArrayEquals(data, out.data());
    }

    @Test
    void doneCarriesTheCount() {
        assertEquals(7, roundTrip(PackPayloads.Done.STREAM_CODEC, new PackPayloads.Done(7)).fileCount());
    }
}
