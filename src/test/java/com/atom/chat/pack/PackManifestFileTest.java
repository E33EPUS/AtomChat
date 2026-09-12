package com.atom.chat.pack;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The on-disk copy of a pack: verified writes, atomic swap, honest reads. */
class PackManifestFileTest {
    private static final byte[] PNG =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9};

    @TempDir
    Path root;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ServerPack.FileEntry entry(String name, byte[] data) {
        return new ServerPack.FileEntry(name, ServerPack.sha256Hex(data), data.length);
    }

    /** A two-file pack plus an icon, with the matching content map. */
    private ServerPack pack(Map<String, byte[]> contents) {
        List<ServerPack.FileEntry> files = contents.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> entry(e.getKey(), e.getValue()))
                .toList();
        return new ServerPack(
                ServerPack.hashOf(ServerPack.canonical(files, List.of("hi"), "My Server", PNG)),
                files, List.of("hi"), "My Server", PNG);
    }

    private Map<String, byte[]> contents() {
        Map<String, byte[]> map = new LinkedHashMap<>();
        map.put("a.png", bytes("alpha"));
        map.put("b.gif", bytes("bravo"));
        return map;
    }

    @Test
    void roundTripRestoresThePack() throws Exception {
        Map<String, byte[]> contents = contents();
        ServerPack original = pack(contents);
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);

        PackManifestFile.write(dir, original, contents);
        ServerPack loaded = PackManifestFile.read(dir);

        assertNotNull(loaded);
        assertEquals(original.packHash(), loaded.packHash());
        assertEquals("My Server", loaded.serverName());
        assertEquals(List.of("hi"), loaded.phrases());
        assertEquals(2, loaded.files().size());
        assertEquals("a.png", loaded.files().get(0).name());
        assertEquals(original.totalBytes(), loaded.totalBytes());
        assertTrue(loaded.hasIcon());
        assertArrayEquals(PNG, loaded.icon());
    }

    @Test
    void localHashesSeeExactlyWhatWasWritten() throws Exception {
        Map<String, byte[]> contents = contents();
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);
        PackManifestFile.write(dir, pack(contents), contents);

        Map<String, String> hashes = PackManifestFile.localHashes(dir);

        assertEquals(2, hashes.size());
        assertEquals(ServerPack.sha256Hex(contents.get("a.png")), hashes.get("a.png"));
        assertEquals(ServerPack.sha256Hex(contents.get("b.gif")), hashes.get("b.gif"));
        assertNull(hashes.get("manifest.json"));
    }

    @Test
    void deletingOneFileIsVisibleToTheNextDiff() throws Exception {
        Map<String, byte[]> contents = contents();
        ServerPack pack = pack(contents);
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);
        PackManifestFile.write(dir, pack, contents);

        Files.delete(dir.resolve(PackManifestFile.EMOTES_DIR).resolve("a.png"));

        Map<String, String> hashes = PackManifestFile.localHashes(dir);
        PackDiff.Plan plan = PackDiff.plan(pack, hashes);

        assertEquals(List.of("a.png"),
                plan.fetch().stream().map(ServerPack.FileEntry::name).toList());
        assertEquals(1, plan.fetch().size());
    }

    @Test
    void installSwapsTheDirectoryAndLeavesNoTemp() throws Exception {
        Path packs = root.resolve("packs");
        String key = PackKeys.serverKey("host:1");
        Map<String, byte[]> first = contents();
        PackManifestFile.install(packs, key, pack(first), first);

        Map<String, byte[]> second = new LinkedHashMap<>();
        second.put("c.png", bytes("charlie"));
        ServerPack secondPack = pack(second);
        PackManifestFile.install(packs, key, secondPack, second);

        ServerPack loaded = PackManifestFile.read(packs.resolve(key));
        assertNotNull(loaded);
        assertEquals(secondPack.packHash(), loaded.packHash());
        assertEquals("c.png", loaded.files().get(0).name());
        assertFalse(Files.exists(packs.resolve(key + ".tmp")), "the temp directory must be gone");
        assertEquals(1, PackManifestFile.localHashes(packs.resolve(key)).size());
    }

    @Test
    void aRefusedKeyNeverTouchesTheDisk() {
        Map<String, byte[]> contents = contents();
        boolean threw = false;
        try {
            PackManifestFile.install(root.resolve("packs"), "../escape", pack(contents), contents);
        } catch (Exception e) {
            threw = true;
        }
        assertTrue(threw);
        assertFalse(Files.exists(root.resolve("escape")));
    }

    @Test
    void aBrokenManifestReadsAsNoPack() throws Exception {
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(PackManifestFile.MANIFEST_NAME), "{not json");

        assertNull(PackManifestFile.read(dir));
        assertNull(PackManifestFile.read(root.resolve("never-written")));
    }

    @Test
    void anUnsafeNameInAHandEditedManifestIsRejected() throws Exception {
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);
        Gson gson = new Gson();
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", "../evil.png");
        file.put("sha256", "0".repeat(64));
        file.put("size", 1);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version", 1);
        json.put("packHash", "1".repeat(64));
        json.put("files", List.of(file));
        Files.writeString(dir.resolve(PackManifestFile.MANIFEST_NAME), gson.toJson(json));

        assertNull(PackManifestFile.read(dir));
    }

    @Test
    void staleFilesAreDeletedButUnsafeNamesAreNotTouched() throws Exception {
        Map<String, byte[]> contents = contents();
        Path dir = root.resolve("pack");
        Files.createDirectories(dir);
        PackManifestFile.write(dir, pack(contents), contents);

        PackManifestFile.deleteStale(dir, List.of("a.png", "../outside.png", "not a name"));

        assertFalse(Files.exists(dir.resolve(PackManifestFile.EMOTES_DIR).resolve("a.png")));
        assertTrue(Files.exists(dir.resolve(PackManifestFile.EMOTES_DIR).resolve("b.gif")));
    }
}
