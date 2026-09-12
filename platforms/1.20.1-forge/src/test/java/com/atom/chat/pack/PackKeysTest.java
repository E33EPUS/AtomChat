package com.atom.chat.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Folder naming: stable, collision-resistant, and never taken from the wire. */
class PackKeysTest {
    @Test
    void keysAreStableAndCaseInsensitive() {
        assertEquals(PackKeys.serverKey("play.example.com:25565"),
                PackKeys.serverKey("Play.Example.COM:25565"));
        assertEquals(PackKeys.serverKey("  host:1  "), PackKeys.serverKey("host:1"));
        assertEquals(PackKeys.KEY_CHARS, PackKeys.serverKey("host:1").length());
    }

    @Test
    void differentEndpointsGetDifferentFolders() {
        assertNotEquals(PackKeys.serverKey("a:1"), PackKeys.serverKey("a:2"));
        assertNotEquals(PackKeys.serverKey("a:1"), PackKeys.serverKey("b:1"));
        // A level that looks like an address must not share that server's folder.
        assertNotEquals(PackKeys.serverKey("127.0.0.1:25565"), PackKeys.levelKey("127.0.0.1:25565"));
        assertEquals(PackKeys.levelKey("world"), PackKeys.levelKey("world"));
        assertNotEquals(PackKeys.levelKey("world"), PackKeys.levelKey("World 2"));
    }

    @Test
    void onlyOurOwnKeysPass() {
        assertTrue(PackKeys.isKey(PackKeys.serverKey("host:1")));
        assertTrue(PackKeys.isKey("0123456789abcdef"));

        assertFalse(PackKeys.isKey(null));
        assertFalse(PackKeys.isKey(""));
        assertFalse(PackKeys.isKey("short"));
        assertFalse(PackKeys.isKey("0123456789abcde"));
        assertFalse(PackKeys.isKey("0123456789abcdef0"));
        assertFalse(PackKeys.isKey("ABCDEF0123456789"), "uppercase is not our shape");
        assertFalse(PackKeys.isKey("zzzzzzzzzzzzzzzz"));
        assertFalse(PackKeys.isKey("../etc/passwd"));
        assertFalse(PackKeys.isKey("0123456789abcde/"));
    }
}
