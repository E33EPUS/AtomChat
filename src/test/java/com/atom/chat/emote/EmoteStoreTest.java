package com.atom.chat.emote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteStoreTest {
    @TempDir
    Path tmp;

    private EmoteStore newStore() {
        return new EmoteStore(tmp.resolve("emotes"));
    }

    private File touch(Path parent, String name, byte[] bytes) throws IOException {
        Files.createDirectories(parent);
        Path p = parent.resolve(name);
        Files.write(p, bytes);
        return p.toFile();
    }

    @Test
    void emptyDirYieldsEmptyList() {
        assertEquals(0, newStore().count());
    }

    @Test
    void isSupportedName() {
        assertTrue(EmoteStore.isSupportedName("a.png"));
        assertTrue(EmoteStore.isSupportedName("A.JPG"));
        assertTrue(EmoteStore.isSupportedName("b.jpeg"));
        assertFalse(EmoteStore.isSupportedName("c.gif"));
        assertFalse(EmoteStore.isSupportedName("d.webp"));
        assertFalse(EmoteStore.isSupportedName("e.bmp"));
        assertFalse(EmoteStore.isSupportedName("readme.txt"));
        assertFalse(EmoteStore.isSupportedName(null));
        assertFalse(EmoteStore.isSupportedName(""));
    }

    @Test
    void addCopiesSourceIntoDirAndKeepsSource() throws IOException {
        EmoteStore store = newStore();
        File source = touch(tmp, "cat.png", "png-bytes".getBytes(StandardCharsets.UTF_8));

        assertTrue(store.add(source));
        assertTrue(source.isFile(), "add must copy, not move");
        assertTrue(Files.exists(store.dir().resolve("cat.png")), "file copied into emote dir");
        assertEquals(1, store.count());
    }

    @Test
    void rejectsUnsupportedExtensions() throws IOException {
        EmoteStore store = newStore();
        File gif = touch(tmp, "anim.gif", "gif-bytes".getBytes(StandardCharsets.UTF_8));
        File txt = touch(tmp, "notes.txt", "hi".getBytes(StandardCharsets.UTF_8));

        assertFalse(store.add(gif));
        assertFalse(store.add(txt));
        assertEquals(0, store.count());
    }

    @Test
    void capsAtMax() throws IOException {
        EmoteStore store = newStore();
        File source = touch(tmp, "src.png", "x".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < EmoteStore.MAX; i++) {
            File f = touch(tmp, "s" + i + ".png", "x".getBytes(StandardCharsets.UTF_8));
            assertTrue(store.add(f), "add #" + i + " should succeed");
        }
        assertEquals(EmoteStore.MAX, store.count());
        assertTrue(store.isFull());
        assertFalse(store.add(source), "add beyond the cap must fail");
        assertEquals(EmoteStore.MAX, store.count());
    }

    @Test
    void listsSortedByName() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "zeta.png", "x".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "alpha.png", "x".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "mid.png", "x".getBytes(StandardCharsets.UTF_8)));

        List<File> files = store.list();
        assertEquals(List.of("alpha.png", "mid.png", "zeta.png"),
                files.stream().map(File::getName).toList());
    }

    @Test
    void duplicateNameOverwrites() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "same.png", "first".getBytes(StandardCharsets.UTF_8)));
        store.add(touch(tmp, "same.png", "second".getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, store.count());
        try {
            String content = Files.readString(store.dir().resolve("same.png"));
            assertEquals("second", content, "duplicate name must overwrite");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void removeDeletesFileAndRefreshes() throws IOException {
        EmoteStore store = newStore();
        store.add(touch(tmp, "gone.png", "x".getBytes(StandardCharsets.UTF_8)));
        File emote = store.list().get(0);

        assertTrue(store.remove(emote));
        assertEquals(0, store.count());
        assertFalse(Files.exists(store.dir().resolve("gone.png")));
    }

    @Test
    void refusesToRemoveFilesOutsideTheDir() throws IOException {
        EmoteStore store = newStore();
        File outside = touch(tmp, "outside.png", "x".getBytes(StandardCharsets.UTF_8));

        assertFalse(store.remove(outside));
        assertTrue(outside.isFile());
        assertEquals(0, store.count());
    }

    @Test
    void scansExternallyDroppedFilesOnRefresh() throws IOException {
        EmoteStore store = newStore();
        touch(store.dir(), "manual.png", "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, store.count(), "list is cached until refresh");
        store.refresh();
        assertEquals(1, store.count());
        assertEquals("manual.png", store.list().get(0).getName());
    }
}
