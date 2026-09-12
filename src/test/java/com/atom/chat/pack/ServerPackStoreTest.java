package com.atom.chat.pack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the panel sees of a downloaded pack: present, absent, or gone. */
class ServerPackStoreTest {
    private static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 7};

    @TempDir
    Path root;

    private final String key = PackKeys.serverKey("host:1");

    @AfterEach
    void clearStore() {
        ServerPackStore.clear();
    }

    private static ServerPack packFor(Map<String, byte[]> contents, List<String> phrases) {
        List<ServerPack.FileEntry> files = new ArrayList<>();
        contents.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                files.add(new ServerPack.FileEntry(entry.getKey(),
                        ServerPack.sha256Hex(entry.getValue()), entry.getValue().length)));
        return new ServerPack(ServerPack.hashOf(ServerPack.canonical(files, phrases, "My Server", ICON)),
                files, phrases, "My Server", ICON);
    }

    private void install(Map<String, byte[]> contents, List<String> phrases) throws Exception {
        ServerPack pack = packFor(contents, phrases);
        PackManifestFile.install(root, key, pack, contents);
    }

    private static Map<String, byte[]> emotes(String... names) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (String name : names) {
            map.put(name, ("bytes of " + name).getBytes(StandardCharsets.UTF_8));
        }
        return map;
    }

    @Test
    void anInstalledPackIsVisible() throws Exception {
        install(emotes("cat.png", "dog.gif"), List.of("hi", "bye"));

        ServerPackStore.refresh(root, key);
        ServerPackStore store = ServerPackStore.current();

        assertTrue(store.isPresent());
        assertEquals("My Server", store.serverName());
        assertEquals(List.of("hi", "bye"), store.phrases());
        assertEquals(2, store.emoteCount());
        assertEquals("cat.png", store.emotes().get(0).getFileName().toString());
        assertEquals("dog.gif", store.emotes().get(1).getFileName().toString());
        assertTrue(store.emotesDir().endsWith("emotes"));
        assertEquals(64, store.packHash().length());
        assertTrue(store.icon() != null && store.icon().length > 0);
    }

    @Test
    void nothingInstalledReadsAsAbsent() {
        ServerPackStore.refresh(root, key);
        ServerPackStore store = ServerPackStore.current();

        assertFalse(store.isPresent());
        assertEquals(0, store.emoteCount());
        assertTrue(store.phrases().isEmpty());
        assertEquals("", store.serverName());
        assertEquals(null, store.emotesDir());
    }

    @Test
    void aBadOrMissingKeyReadsAsAbsentInsteadOfThrowing() {
        ServerPackStore.refresh(root, "../escape");
        assertFalse(ServerPackStore.current().isPresent());

        ServerPackStore.refresh(null, key);
        assertFalse(ServerPackStore.current().isPresent());

        ServerPackStore.refresh(root, null);
        assertFalse(ServerPackStore.current().isPresent());
    }

    @Test
    void serverEmotesAreCappedAtTheServerMaximum() throws Exception {
        Map<String, byte[]> many = new LinkedHashMap<>();
        for (int i = 0; i < ServerPackStore.MAX_SERVER_EMOTES + 8; i++) {
            String name = String.format("e%02d.png", i);
            many.put(name, ("bytes " + name).getBytes(StandardCharsets.UTF_8));
        }
        install(many, List.of());

        ServerPackStore.refresh(root, key);

        assertEquals(ServerPackStore.MAX_SERVER_EMOTES, ServerPackStore.current().emoteCount());
    }

    @Test
    void aFileDeletedBehindOurBackDisappearsFromTheList() throws Exception {
        install(emotes("cat.png", "dog.gif"), List.of());
        ServerPackStore.refresh(root, key);
        assertEquals(2, ServerPackStore.current().emoteCount());

        Files.delete(root.resolve(key).resolve(PackManifestFile.EMOTES_DIR).resolve("cat.png"));
        ServerPackStore.refresh(root, key);

        assertEquals(1, ServerPackStore.current().emoteCount());
        assertEquals("dog.gif", ServerPackStore.current().emotes().get(0).getFileName().toString());
    }

    @Test
    void clearForgetsThePack() throws Exception {
        install(emotes("cat.png"), List.of("hi"));
        ServerPackStore.refresh(root, key);
        assertTrue(ServerPackStore.current().isPresent());

        ServerPackStore.clear();

        assertFalse(ServerPackStore.current().isPresent());
    }

    @Test
    void aPackWithNothingToShowIsTreatedAsNoDistribution() throws Exception {
        install(Map.of(), List.of());
        ServerPackStore.refresh(root, key);

        assertTrue(ServerPackStore.current().isPresent());
        assertFalse(ServerPackStore.current().hasContent(),
                "an empty pack must not make the panels grow server-only rows");
    }

    @Test
    void aPackWithEitherEmotesOrPhrasesHasContent() throws Exception {
        install(emotes("cat.png"), List.of());
        ServerPackStore.refresh(root, key);
        assertTrue(ServerPackStore.current().hasContent());

        install(Map.of(), List.of("hi"));
        ServerPackStore.refresh(root, key);
        assertTrue(ServerPackStore.current().hasContent());
    }

    @Test
    void aPackWithOnlyPhrasesStillCounts() throws Exception {
        install(Map.of(), List.of("hi"));

        ServerPackStore.refresh(root, key);

        assertTrue(ServerPackStore.current().isPresent());
        assertEquals(1, ServerPackStore.current().phrases().size());
        assertEquals(0, ServerPackStore.current().emoteCount());
    }
}
