package com.atom.chat.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarStoreTest {
    @TempDir
    Path tmp;

    private AvatarStore newStore() {
        return new AvatarStore(tmp.resolve("avatar"));
    }

    private Path touch(String name) throws IOException {
        Path p = tmp.resolve(name);
        Files.write(p, "image-bytes".getBytes(StandardCharsets.UTF_8));
        return p;
    }

    @Test
    void isSupportedName() {
        assertTrue(AvatarStore.isSupportedName("a.png"));
        assertTrue(AvatarStore.isSupportedName("A.JPG"));
        assertTrue(AvatarStore.isSupportedName("b.jpeg"));
        assertTrue(AvatarStore.isSupportedName("c.webp"));
        assertFalse(AvatarStore.isSupportedName("d.gif"));
        assertFalse(AvatarStore.isSupportedName("e.bmp"));
        assertFalse(AvatarStore.isSupportedName("readme.txt"));
        assertFalse(AvatarStore.isSupportedName(null));
        assertFalse(AvatarStore.isSupportedName(""));
    }

    @Test
    void setCopiesSourceAndKeepsSource() throws IOException {
        AvatarStore store = newStore();
        Path source = touch("me.png");

        assertTrue(store.set(source));
        assertTrue(Files.exists(source), "set must copy, not move");
        assertTrue(Files.exists(tmp.resolve("avatar").resolve("avatar.png")));
        assertTrue(store.isSet());
    }

    @Test
    void setReplacesPreviousAvatar() throws IOException {
        AvatarStore store = newStore();
        assertTrue(store.set(touch("one.png")));
        assertTrue(store.set(touch("two.jpg")));

        assertFalse(Files.exists(tmp.resolve("avatar").resolve("avatar.png")),
                "previous avatar.* must be dropped");
        assertTrue(Files.exists(tmp.resolve("avatar").resolve("avatar.jpg")));
    }

    @Test
    void clearRemovesAndFallsBackToSkin() throws IOException {
        AvatarStore store = newStore();
        assertTrue(store.set(touch("me.png")));

        assertTrue(store.clear());
        assertFalse(store.isSet());
        assertNull(store.current());
    }

    @Test
    void refreshRecognizesManuallyDroppedFile() throws IOException {
        AvatarStore store = newStore();
        Path dir = tmp.resolve("avatar");
        Files.createDirectories(dir);
        Files.write(dir.resolve("avatar.png"), "dropped".getBytes(StandardCharsets.UTF_8));

        store.refresh();
        assertTrue(store.isSet());
    }

    @Test
    void rejectsUnsupportedAndMissingSources() throws IOException {
        AvatarStore store = newStore();
        assertFalse(store.set(touch("cat.gif")));
        assertFalse(store.set(tmp.resolve("missing.png")));
        assertFalse(store.isSet());
    }
}
