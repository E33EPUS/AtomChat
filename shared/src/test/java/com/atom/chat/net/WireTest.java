package com.atom.chat.net;

import com.atom.chat.config.ServerConfigValues;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire format, pinned byte for byte.
 *
 * <p>The goldens in {@code wire-goldens.txt} were produced <em>before</em> the byte
 * layer was extracted, by the payload codecs of a real target (NeoForge's
 * {@code RegistryFriendlyByteBuf}). So they prove the extraction moved no byte -
 * "each target still reads what it writes" is a different claim, and it stays with
 * each target's own {@code *CodecTest}. Both are needed: a target that quietly
 * reorders a field keeps passing its own round trip.
 *
 * <p>{@link Bytes} is a test-only buffer implementation that copies vanilla's rules
 * (seven-bit little-endian varints, a byte-length prefix before UTF-8, a varint
 * length before a byte array, and bounded reads that check before allocating).
 * Copying them by hand is the point: get one wrong and the goldens no longer match
 * what the real buffers produce.
 */
class WireTest {
    private static final Map<String, String> GOLDENS = loadGoldens();
    private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private static ServerConfigValues values() {
        return new ServerConfigValues(true, false, 512, 64, 128, 30, 1500, 32, 8,
                "Pack \u540d\u79f0 with \u00fcnicode", List.of("hi", "\u7b2c\u4e8c\u6761"));
    }

    // ------------------------------------------------------------------ writes

    @Test
    void packWritesMatchTheCapturedBytes() {
        assertEquals(golden("PACK_HELLO"),
                encode(out -> Wire.writeHello(out, new PackMessage.Hello())));
        assertEquals(golden("PACK_NEED"), encode(out -> Wire.writeNeed(out,
                new PackMessage.Need(List.of("cat.png", "dog.gif")))));
        assertEquals(golden("PACK_NEED_EMPTY"), encode(out -> Wire.writeNeed(out,
                new PackMessage.Need(List.of()))));
        assertEquals(golden("PACK_NEED_NULLS"), encode(out -> Wire.writeNeed(out,
                new PackMessage.Need(Arrays.asList(null, "x")))));
        assertEquals(golden("PACK_ACK"), encode(out -> Wire.writeAck(out,
                new PackMessage.Ack(false, "timeout"))));
        assertEquals(golden("PACK_ACK_100"), encode(out -> Wire.writeAck(out,
                new PackMessage.Ack(true, "d".repeat(100)))));
        assertEquals(golden("PACK_MANIFEST"), encode(out -> Wire.writeManifest(out, fullManifest())));
        assertEquals(golden("PACK_MANIFEST_OFF"), encode(out -> Wire.writeManifest(out,
                new PackMessage.Manifest(false, "", "", new byte[0], List.of(), List.of()))));
        assertEquals(golden("PACK_MANIFEST_NULLS"), encode(out -> Wire.writeManifest(out,
                new PackMessage.Manifest(true, null, null, null, null, null))));
        assertEquals(golden("PACK_CHUNK"), encode(out -> Wire.writeChunk(out,
                new PackMessage.Chunk("cat.png", 49152, 65536, new byte[] {1, 2, 3, 4}))));
        assertEquals(golden("PACK_DONE"), encode(out -> Wire.writeDone(out, new PackMessage.Done(7))));
    }

    @Test
    void configWritesMatchTheCapturedBytes() {
        assertEquals(golden("CFG_VALUES"), encode(out -> Wire.writeValues(out, values())));
        assertEquals(golden("CFG_SUMMARY"), encode(out -> Wire.writePackSummary(out,
                new Wire.PackSummary(0, 0L, ""))));
        // A snapshot is the values followed by the summary; its golden is the whole
        // captured message minus the version varint the payload record writes.
        assertEquals(golden("CFG_VALUES_AND_SUMMARY"), encode(out -> {
            Wire.writeValues(out, values());
            Wire.writePackSummary(out, new Wire.PackSummary(2, 123456789L, "hhhhhhhh"));
        }));
    }

    // ------------------------------------------------------------------- reads

    @Test
    void packReadsParseTheCapturedBytes() {
        assertEquals(List.of("cat.png", "dog.gif"), Wire.readNeed(reader("PACK_NEED")).names());
        assertEquals(List.of(), Wire.readNeed(reader("PACK_NEED_EMPTY")).names());
        assertEquals("", Wire.readNeed(reader("PACK_NEED_NULLS")).names().get(0));
        assertEquals(new PackMessage.Hello(), Wire.readHello(reader("PACK_HELLO")));

        PackMessage.Ack ack = Wire.readAck(reader("PACK_ACK"));
        assertEquals(false, ack.ok());
        assertEquals("timeout", ack.detail());

        PackMessage.Manifest manifest = Wire.readManifest(reader("PACK_MANIFEST"));
        assertTrue(manifest.enabled());
        assertEquals(64, manifest.packHash().length());
        assertEquals("My Server", manifest.serverName());
        assertArrayEquals(ICON, manifest.icon());
        assertEquals(2, manifest.files().size());
        assertEquals("dog.gif", manifest.files().get(1).name());
        assertEquals(4321, manifest.files().get(1).size());
        assertEquals(List.of("hi", "\u7b2c\u4e8c\u884c / utf8"), manifest.phrases());

        assertEquals(false, Wire.readManifest(reader("PACK_MANIFEST_OFF")).enabled());
        assertEquals("", Wire.readManifest(reader("PACK_MANIFEST_NULLS")).serverName());

        PackMessage.Chunk chunk = Wire.readChunk(reader("PACK_CHUNK"));
        assertEquals("cat.png", chunk.name());
        assertEquals(49152, chunk.offset());
        assertEquals(65536, chunk.totalBytes());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, chunk.data());

        assertEquals(7, Wire.readDone(reader("PACK_DONE")).fileCount());
    }

    @Test
    void configReadsParseTheCapturedBytes() {
        assertEquals(values(), Wire.readValues(reader("CFG_VALUES")));
        assertEquals(new Wire.PackSummary(0, 0L, ""),
                Wire.readPackSummary(reader("CFG_SUMMARY")));
    }

    @Test
    void aWrittenMessageReadsBackAsItself() {
        // Compared field by field on purpose: a record's equals compares its byte[]
        // component by reference, so whole-record equality would fail on identical
        // content (the same trap the manifest and chunk codecs could hide behind).
        PackMessage.Manifest manifest = Wire.readManifest(
                encodeAsReader(out -> Wire.writeManifest(out, fullManifest())));
        assertEquals(fullManifest().packHash(), manifest.packHash());
        assertEquals(fullManifest().serverName(), manifest.serverName());
        assertArrayEquals(ICON, manifest.icon());
        assertEquals(fullManifest().files(), manifest.files());
        assertEquals(fullManifest().phrases(), manifest.phrases());

        PackMessage.Chunk chunk = Wire.readChunk(encodeAsReader(out -> Wire.writeChunk(out,
                new PackMessage.Chunk("cat.png", 49152, 65536, new byte[] {1, 2, 3, 4}))));
        assertEquals("cat.png", chunk.name());
        assertEquals(49152, chunk.offset());
        assertEquals(65536, chunk.totalBytes());
        assertArrayEquals(new byte[] {1, 2, 3, 4}, chunk.data());

        assertEquals(values(), Wire.readValues(encodeAsReader(
                out -> Wire.writeValues(out, values()))));
    }

    // ------------------------------------------------------------------- rules

    @Test
    void aPeerThatSendsAnAbsurdCountIsRefusedBeforeAnythingIsAllocated() {
        Bytes tooManyNames = encodeAsReader(out -> Wire.writeNeed(out,
                new PackMessage.Need(Collections.nCopies(Wire.MAX_FILES + 1, "x"))));
        assertThrows(DecoderException.class, () -> Wire.readNeed(tooManyNames));

        Bytes tooManyFiles = encodeAsReader(out -> Wire.writeManifest(out,
                new PackMessage.Manifest(true, "", "", new byte[0],
                        Collections.nCopies(Wire.MAX_FILES + 1,
                                new PackMessage.PackFile("a.png", "b", 1)),
                        List.of())));
        assertThrows(DecoderException.class, () -> Wire.readManifest(tooManyFiles));

        Bytes tooManyPhrases = encodeAsReader(out -> Wire.writeManifest(out,
                new PackMessage.Manifest(true, "", "", new byte[0], List.of(),
                        Collections.nCopies(Wire.MAX_PHRASES + 1, "p"))));
        assertThrows(DecoderException.class, () -> Wire.readManifest(tooManyPhrases));

        // The slice size is the protocol's, not the peer's: no single message may
        // make a client allocate an arbitrary array.
        Bytes fatChunk = encodeAsReader(out -> Wire.writeChunk(out, new PackMessage.Chunk(
                "a.png", 0, MediaIds.CHUNK_BYTES + 1, new byte[MediaIds.CHUNK_BYTES + 1])));
        assertThrows(DecoderException.class, () -> Wire.readChunk(fatChunk));
    }

    @Test
    void configStringsAreTruncatedRatherThanRefused() {
        ServerConfigValues tooLong = new ServerConfigValues(true, true, 1, 1, 1, 1, 1, 1, 1,
                "n".repeat(ServerConfigValues.MAX_NAME_CHARS + 50),
                Collections.nCopies(Wire.MAX_CONFIG_PHRASES + 20,
                        "p".repeat(ServerConfigValues.MAX_PHRASE_CHARS + 50)));

        ServerConfigValues out = Wire.readValues(
                encodeAsReader(stream -> Wire.writeValues(stream, tooLong)));

        assertEquals(ServerConfigValues.MAX_NAME_CHARS, out.packName().length());
        assertEquals(Wire.MAX_CONFIG_PHRASES, out.phrases().size());
        assertEquals(ServerConfigValues.MAX_PHRASE_CHARS, out.phrases().get(0).length());
    }

    @Test
    void anOverlongNameIsRefusedRatherThanCutShort() {
        // Pack names are validated when the file is written, so the codec treats a
        // long one as a bug rather than silently shipping a truncated name.
        assertThrows(IllegalStateException.class, () -> encode(out -> Wire.writeChunk(out,
                new PackMessage.Chunk("n".repeat(200), 0, 1, new byte[] {1}))));
    }

    @Test
    void truncateNeverReturnsNull() {
        assertEquals("", Wire.truncate(null, 4));
        assertEquals("abcd", Wire.truncate("abcd", 4));
        assertEquals("abcd", Wire.truncate("abcdef", 4));
        assertEquals("", Wire.truncate("", 4));
    }

    // ------------------------------------------------------------------- tools

    private static PackMessage.Manifest fullManifest() {
        return new PackMessage.Manifest(true, "a".repeat(64), "My Server", ICON,
                List.of(new PackMessage.PackFile("cat.png", "b".repeat(64), 1234),
                        new PackMessage.PackFile("dog.gif", "c".repeat(64), 4321)),
                List.of("hi", "\u7b2c\u4e8c\u884c / utf8"));
    }

    private static String golden(String key) {
        String value = GOLDENS.get(key);
        if (value == null) {
            throw new IllegalStateException("no golden named " + key);
        }
        return value;
    }

    private static String encode(Consumer<Wire.Out> writer) {
        Bytes out = new Bytes();
        writer.accept(out);
        return out.hex();
    }

    private static Bytes encodeAsReader(Consumer<Wire.Out> writer) {
        Bytes out = new Bytes();
        writer.accept(out);
        return out.reader();
    }

    private static Bytes reader(String key) {
        return new Bytes(hexToBytes(golden(key)));
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static Map<String, String> loadGoldens() {
        Map<String, String> goldens = new HashMap<>();
        try (InputStream in = WireTest.class.getResourceAsStream("/wire-goldens.txt")) {
            if (in == null) {
                throw new IllegalStateException("wire-goldens.txt is not on the test classpath");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.split("\n")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int split = trimmed.indexOf('=');
                goldens.put(trimmed.substring(0, split), trimmed.substring(split + 1));
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read wire-goldens.txt", e);
        }
        if (goldens.size() < 16) {
            throw new IllegalStateException("expected 16 goldens, found " + goldens.size());
        }
        return goldens;
    }

    /** A test-only buffer: writes into a byte array, then reads from one. */
    private static final class Bytes implements Wire.Out, Wire.In {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final byte[] data;
        private int pos;

        private Bytes() {
            this.data = new byte[0];
        }

        private Bytes(byte[] data) {
            this.data = data;
        }

        /** Re-reads what was just written, so a bad frame can be built and parsed. */
        private Bytes reader() {
            return new Bytes(out.toByteArray());
        }

        private String hex() {
            StringBuilder sb = new StringBuilder();
            for (byte b : out.toByteArray()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        // ---- Wire.Out

        @Override
        public void bool(boolean value) {
            out.write(value ? 1 : 0);
        }

        @Override
        public void varInt(int value) {
            int rest = value;
            while ((rest & 0xFFFFFF80) != 0) {
                out.write((rest & 0x7F) | 0x80);
                rest >>>= 7;
            }
            out.write(rest);
        }

        @Override
        public void varLong(long value) {
            long rest = value;
            while ((rest & 0xFFFFFFFFFFFFFF80L) != 0) {
                out.write((int) (rest & 0x7F) | 0x80);
                rest >>>= 7;
            }
            out.write((int) rest);
        }

        @Override
        public void string(String value) {
            string(value, 32767);
        }

        @Override
        public void string(String value, int maxChars) {
            if (value.length() > maxChars) {
                throw new IllegalStateException("String too big (was " + value.length()
                        + " characters, max " + maxChars + ")");
            }
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            varInt(utf8.length);
            out.writeBytes(utf8);
        }

        @Override
        public void bytes(byte[] value) {
            varInt(value.length);
            out.writeBytes(value);
        }

        // ---- Wire.In

        @Override
        public boolean bool() {
            return data[pos++] != 0;
        }

        @Override
        public int varInt() {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && shift < 35);
            return value;
        }

        @Override
        public long varLong() {
            long value = 0L;
            int shift = 0;
            byte b;
            do {
                b = data[pos++];
                value |= (long) (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && shift < 70);
            return value;
        }

        @Override
        public String string() {
            return string(32767);
        }

        @Override
        public String string(int maxChars) {
            int length = varInt();
            if (length < 0 || length > maxChars * 3) {
                throw new DecoderException("bad string length " + length);
            }
            String value = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            if (value.length() > maxChars) {
                throw new DecoderException("string longer than " + maxChars);
            }
            return value;
        }

        @Override
        public byte[] bytes() {
            int length = varInt();
            if (length < 0) {
                throw new DecoderException("bad byte array length " + length);
            }
            return slice(length);
        }

        @Override
        public byte[] bytes(int maxBytes) {
            int length = varInt();
            if (length < 0 || length > maxBytes) {
                throw new DecoderException("byte array longer than " + maxBytes);
            }
            return slice(length);
        }

        private byte[] slice(int length) {
            byte[] value = new byte[length];
            System.arraycopy(data, pos, value, 0, length);
            pos += length;
            return value;
        }
    }
}
