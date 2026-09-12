package com.atom.chat.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packaging rules: what the server offers, and what it silently refuses to. */
class PackBuilderTest {
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @TempDir
    Path dir;

    /** PNG magic plus padding, enough for the sniffing code. */
    private static byte[] png(int size) {
        byte[] out = new byte[Math.max(size, PNG_MAGIC.length)];
        System.arraycopy(PNG_MAGIC, 0, out, 0, PNG_MAGIC.length);
        return out;
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private ServerPack build(List<String> phrases) {
        return PackBuilder.build(dir, phrases, null, null, null, 32, 8L * 1024L * 1024L);
    }

    private static List<String> names(ServerPack pack) {
        List<String> out = new ArrayList<>();
        for (ServerPack.FileEntry file : pack.files()) {
            out.add(file.name());
        }
        return out;
    }

    @Test
    void sameDirectoryAlwaysHashesTheSame() throws Exception {
        write("a.png", png(64));
        write("b.gif", png(32));

        String first = build(List.of("hi")).packHash();
        String second = build(List.of("hi")).packHash();

        assertEquals(first, second);
        assertEquals(first, first.toLowerCase());
        assertEquals(64, first.length());
    }

    @Test
    void editingOneFileChangesTheHash() throws Exception {
        write("a.png", png(64));
        String before = build(List.of()).packHash();

        write("a.png", png(65));

        assertNotEquals(before, build(List.of()).packHash());
    }

    @Test
    void manifestOrderDoesNotMatter() {
        ServerPack.FileEntry one = new ServerPack.FileEntry("a.png", "0".repeat(64), 1);
        ServerPack.FileEntry two = new ServerPack.FileEntry("b.png", "1".repeat(64), 2);

        String forward = ServerPack.canonical(List.of(one, two), List.of("p"), "srv", null);
        String reversed = ServerPack.canonical(List.of(two, one), List.of("p"), "srv", null);

        assertEquals(forward, reversed);
        assertEquals(ServerPack.hashOf(forward), ServerPack.hashOf(reversed));
    }

    @Test
    void unsafeNamesAreRejected() {
        assertTrue(ServerPack.isSafeName("cat.png"));
        assertTrue(ServerPack.isSafeName("my_cat-01.gif"));
        assertTrue(ServerPack.isSafeName("a.jpeg"));

        assertFalse(ServerPack.isSafeName("cat.PNG"), "the wire contract is lowercase only");
        assertFalse(ServerPack.isSafeName("../evil.png"));
        assertFalse(ServerPack.isSafeName("sub/cat.png"));
        assertFalse(ServerPack.isSafeName(".hidden.png"));
        assertFalse(ServerPack.isSafeName("-dash.png"));
        assertFalse(ServerPack.isSafeName("cat.exe"));
        assertFalse(ServerPack.isSafeName("cat"));
        assertFalse(ServerPack.isSafeName("a".repeat(66) + ".png"));
        assertFalse(ServerPack.isSafeName(null));
    }

    @Test
    void builderKeepsOnlySafeLowercaseNames() throws Exception {
        write("Cat.png", png(16));
        write("cat.png", png(32));
        write("evil.exe", png(16));
        write(".hidden.png", png(16));
        write("ok.gif", png(16));

        List<String> names = names(build(List.of()));

        // "Cat.png" and "cat.png" collapse to one entry; the rest never ship.
        assertEquals(List.of("cat.png", "ok.gif"), names);
    }

    @Test
    void fileCapKeepsTheFirstNamesAlphabetically() throws Exception {
        for (String name : List.of("e.png", "d.png", "c.png", "b.png", "a.png")) {
            write(name, png(16));
        }

        ServerPack pack = PackBuilder.build(dir, List.of(), null, null, null, 3, 1_000_000L);

        assertEquals(List.of("a.png", "b.png", "c.png"), names(pack));
    }

    @Test
    void sizeCapNeverOverflows() throws Exception {
        write("a.png", png(100));
        write("b.png", png(100));
        write("c.png", png(100));

        ServerPack pack = PackBuilder.build(dir, List.of(), null, null, null, 32, 250L);

        assertEquals(List.of("a.png", "b.png"), names(pack));
        assertEquals(200L, pack.totalBytes());
        assertTrue(pack.totalBytes() <= 250L);
    }

    @Test
    void aFileLargerThanTheBudgetIsSkippedInsteadOfEaten() throws Exception {
        write("big.png", png(400));
        write("small.png", png(16));

        ServerPack pack = PackBuilder.build(dir, List.of(), null, null, null, 32, 250L);

        assertEquals(List.of("small.png"), names(pack));
    }

    @Test
    void phrasesAreStrippedTruncatedAndLimited() {
        List<String> raw = new ArrayList<>();
        raw.add("  hello  ");
        raw.add("");
        raw.add("   ");
        raw.add(null);
        raw.add("x".repeat(ServerPack.MAX_PHRASE_CHARS + 50));
        for (int i = 0; i < ServerPack.MAX_PHRASES + 5; i++) {
            raw.add("phrase " + i);
        }

        ServerPack pack = build(raw);

        assertEquals(ServerPack.MAX_PHRASES, pack.phrases().size());
        assertEquals("hello", pack.phrases().get(0));
        assertEquals(ServerPack.MAX_PHRASE_CHARS, pack.phrases().get(1).length());
        assertEquals("phrase 0", pack.phrases().get(2));
    }

    @Test
    void serverNamePrefersTheOverrideThenTheCleanedMotd() {
        ServerPack fromMotd = PackBuilder.build(null, List.of(), null,
                "\u00A7cAtom \u00A7fServer\u00A7r\nsecond line", null, 32, 1000L);
        assertEquals("Atom Server", fromMotd.serverName());

        ServerPack overridden = PackBuilder.build(null, List.of(), "My Server",
                "Atom Server", null, 32, 1000L);
        assertEquals("My Server", overridden.serverName());

        ServerPack blankOverride = PackBuilder.build(null, List.of(), "   ",
                "Atom Server", null, 32, 1000L);
        assertEquals("Atom Server", blankOverride.serverName());

        assertEquals("", ServerPack.cleanServerName(null));
        assertEquals("", ServerPack.cleanServerName("\u00A7c \n "));
        assertEquals(ServerPack.MAX_SERVER_NAME_CHARS,
                ServerPack.cleanServerName("n".repeat(ServerPack.MAX_SERVER_NAME_CHARS + 10)).length());
    }

    @Test
    void iconsNeedPngMagicAndMustFitTheBudget() {
        ServerPack kept = PackBuilder.build(null, List.of(), null, null, png(1024), 32, 1000L);
        assertTrue(kept.hasIcon());

        assertFalse(PackBuilder.build(null, List.of(), null, null,
                "not a png".getBytes(StandardCharsets.US_ASCII), 32, 1000L).hasIcon());
        assertFalse(PackBuilder.build(null, List.of(), null, null,
                png(ServerPack.MAX_ICON_BYTES + 1), 32, 1000L).hasIcon());
        assertFalse(PackBuilder.build(null, List.of(), null, null, null, 32, 1000L).hasIcon());
        assertNull(PackBuilder.build(null, List.of(), null, null, null, 32, 1000L).icon());
    }

    @Test
    void lineBreaksCannotForgeAManifest() {
        ServerPack.FileEntry file = new ServerPack.FileEntry("a.png", "2".repeat(64), 1);
        String onePhrase = ServerPack.canonical(List.of(file), List.of("a\nb"), "srv", null);
        String twoPhrases = ServerPack.canonical(List.of(file), List.of("a", "b"), "srv", null);

        assertNotEquals(onePhrase, twoPhrases);
        assertNotEquals(ServerPack.hashOf(onePhrase), ServerPack.hashOf(twoPhrases));
    }

    @Test
    void anEmptySourceStillBuildsAUsablePack() {
        ServerPack pack = PackBuilder.build(dir.resolve("missing"), List.of("hi"), "srv",
                null, null, 32, 1000L);

        assertTrue(pack.files().isEmpty());
        assertFalse(pack.isEmpty(), "phrases alone still make the pack worth sending");
        assertEquals(1, pack.phrases().size());
        assertEquals("srv", pack.serverName());
        assertEquals(64, pack.packHash().length());
        assertEquals(0L, pack.totalBytes());
    }
}
