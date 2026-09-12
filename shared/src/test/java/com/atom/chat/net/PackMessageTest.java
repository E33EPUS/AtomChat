package com.atom.chat.net;

import com.atom.chat.pack.ServerPack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The neutral message has to carry exactly what the payloads carried.
 *
 * <p>These two conversions are the only place where the wire records meet the
 * shared layer, and they are shared by all three targets - so a field dropped
 * here would vanish everywhere at once, which is precisely the failure mode a
 * per-target copy used to hide.
 */
class PackMessageTest {
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    private static ServerPack pack(byte[] icon) {
        return new ServerPack(HASH,
                List.of(new ServerPack.FileEntry("wave.png", OTHER_HASH, 12),
                        new ServerPack.FileEntry("hug.gif", HASH, 340)),
                List.of("hi", "bye"), "Example Server", icon);
    }

    @Test
    void manifestCarriesEveryFieldBackAndForth() {
        ServerPack original = pack(new byte[] {1, 2, 3});
        PackMessage.Manifest message = PackMessage.manifestOf(original);

        assertTrue(message.enabled(), "a real pack is always offered");
        assertEquals(HASH, message.packHash());
        assertEquals("Example Server", message.serverName());
        assertEquals(2, message.files().size());
        assertEquals("wave.png", message.files().get(0).name());
        assertEquals(OTHER_HASH, message.files().get(0).sha256());
        assertEquals(12, message.files().get(0).size());
        assertEquals(List.of("hi", "bye"), message.phrases());
        assertEquals(3, message.icon().length);

        ServerPack parsed = PackMessage.toPack(message);
        assertEquals(original.packHash(), parsed.packHash());
        assertEquals(original.serverName(), parsed.serverName());
        assertEquals(original.files(), parsed.files(), "file order is the canonical order");
        assertEquals(original.phrases(), parsed.phrases());
        assertEquals(3, parsed.icon().length);
        assertTrue(parsed.hasIcon());
    }

    @Test
    void aMissingIconIsAnEmptyArrayOnTheWireAndNullAgainAfterwards() {
        PackMessage.Manifest message = PackMessage.manifestOf(pack(null));

        assertEquals(0, message.icon().length, "the payload layer has no null byte[]");
        assertNull(PackMessage.toPack(message).icon());
        assertFalse(PackMessage.toPack(message).hasIcon());
    }

    @Test
    void theDisabledAnswerIsAnEmptyManifest() {
        PackMessage.Manifest message = PackMessage.disabledManifest();

        assertFalse(message.enabled());
        assertEquals("", message.packHash());
        assertEquals("", message.serverName());
        assertEquals(0, message.icon().length);
        assertTrue(message.files().isEmpty());
        assertTrue(message.phrases().isEmpty());
    }

    @Test
    void aMalformedManifestIsRejectedRatherThanStored() {
        // The client turns this into "bad_manifest"; whatever is wrong with the
        // peer's answer, none of it reaches the disk.
        assertThrows(IllegalArgumentException.class, () -> PackMessage.toPack(
                new PackMessage.Manifest(true, HASH, "srv", new byte[0],
                        List.of(new PackMessage.PackFile("../../evil.png", OTHER_HASH, 1)),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> PackMessage.toPack(
                new PackMessage.Manifest(true, HASH, "srv", new byte[0],
                        List.of(new PackMessage.PackFile("wave.png", "too-short", 1)),
                        List.of())));
        assertThrows(IllegalArgumentException.class, () -> PackMessage.toPack(
                new PackMessage.Manifest(true, HASH, "srv", new byte[0],
                        List.of(new PackMessage.PackFile("wave.png", OTHER_HASH, -1)),
                        List.of())));
        assertThrows(NullPointerException.class, () -> PackMessage.toPack(
                new PackMessage.Manifest(true, HASH, "srv", new byte[0], null, List.of())));
    }
}
