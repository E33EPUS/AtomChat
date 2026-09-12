package com.atom.chat.net;

import com.atom.chat.config.ServerConfigValues;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The config wire format has to survive a round trip, versions and all. */
class ConfigPayloadsCodecTest {
    private static RegistryFriendlyByteBuf buf() {
        // No registry lookup needed: these codecs only move primitives and strings.
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = buf();
        codec.encode(buf, value);
        return codec.decode(buf);
    }

    private static ServerConfigValues values() {
        return new ServerConfigValues(false, true, 4096, 256, 32, 0, 5000, 20, 6,
                "My Server", List.of("hi", "第二行 / utf8"));
    }

    @Test
    void aSnapshotCarriesValuesAndPackStatus() {
        ConfigPayloads.Snapshot out = roundTrip(ConfigPayloads.Snapshot.STREAM_CODEC,
                new ConfigPayloads.Snapshot(7, values(),
                        new ConfigPayloads.PackStatus(3, 12345L, "f".repeat(64))));

        assertEquals(7, out.configVersion());
        assertEquals(values(), out.values());
        assertEquals(3, out.pack().files());
        assertEquals(12345L, out.pack().bytes());
        assertEquals("f".repeat(64), out.pack().packHash());
    }

    @Test
    void aSaveCarriesItsVersion() {
        ConfigPayloads.Save out = roundTrip(ConfigPayloads.Save.STREAM_CODEC,
                new ConfigPayloads.Save(12, values()));

        assertEquals(12, out.configVersion());
        assertEquals(values(), out.values());
    }

    @Test
    void refreshIsEmpty() {
        assertEquals(new ConfigPayloads.Refresh(),
                roundTrip(ConfigPayloads.Refresh.STREAM_CODEC, new ConfigPayloads.Refresh()));
    }

    @Test
    void aStatusIsJustTheSummary() {
        ConfigPayloads.Status out = roundTrip(ConfigPayloads.Status.STREAM_CODEC,
                new ConfigPayloads.Status(new ConfigPayloads.PackStatus(0, 0L, "")));

        assertEquals(0, out.pack().files());
        assertEquals(0L, out.pack().bytes());
        assertEquals("", out.pack().packHash());
    }

    @Test
    void aResultCarriesItsVerdict() {
        assertEquals("conflict", roundTrip(ConfigPayloads.Result.STREAM_CODEC,
                new ConfigPayloads.Result(false, "conflict")).error());
        assertTrue(roundTrip(ConfigPayloads.Result.STREAM_CODEC, new ConfigPayloads.Result(true, "")).ok());
    }

    @Test
    void anOverlongNameOrPhraseIsCutToTheLimitRatherThanBreakingTheSend() {
        ConfigPayloads.Save out = roundTrip(ConfigPayloads.Save.STREAM_CODEC, new ConfigPayloads.Save(1,
                new ServerConfigValues(true, true, 1, 1, 1, 1, 1, 1, 1,
                        "n".repeat(ServerConfigValues.MAX_NAME_CHARS + 40),
                        List.of("p".repeat(ServerConfigValues.MAX_PHRASE_CHARS + 40)))));

        assertEquals(ServerConfigValues.MAX_NAME_CHARS, out.values().packName().length());
        assertEquals(ServerConfigValues.MAX_PHRASE_CHARS, out.values().phrases().get(0).length());
        assertNull(out.values().validate());
    }

    @Test
    void nullPhrasesDoNotBreakTheSend() {
        java.util.ArrayList<String> withNull = new java.util.ArrayList<>();
        withNull.add("hi");
        withNull.add(null);

        ConfigPayloads.Save out = roundTrip(ConfigPayloads.Save.STREAM_CODEC,
                new ConfigPayloads.Save(1, new ServerConfigValues(true, true, 1, 1, 1, 1, 1, 1, 1,
                        "", withNull)));

        assertEquals(2, out.values().phrases().size());
        assertEquals("", out.values().phrases().get(1));
    }
}
