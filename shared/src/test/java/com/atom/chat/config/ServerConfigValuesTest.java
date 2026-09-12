package com.atom.chat.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The server re-checks every number a client sends; this is that rule set. */
class ServerConfigValuesTest {
    private static final String RANGE = ServerConfigValues.ERR_RANGE;

    private static ServerConfigValues v(boolean hosting, boolean packs, int fileKb, int totalMb,
                                        int avatarMb, int days, int cooldown, int packFiles,
                                        int packMb, String name, List<String> phrases) {
        return new ServerConfigValues(hosting, packs, fileKb, totalMb, avatarMb, days, cooldown,
                packFiles, packMb, name, phrases);
    }

    /** Everything at a sane default, so one field can be spoiled per assertion. */
    private static ServerConfigValues base() {
        return v(true, true, 2048, 512, 64, 7, 3000, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withKb(int fileKb) {
        return v(true, true, fileKb, 512, 64, 7, 3000, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withTotal(int totalMb) {
        return v(true, true, 2048, totalMb, 64, 7, 3000, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withAvatar(int avatarMb) {
        return v(true, true, 2048, 512, avatarMb, 7, 3000, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withDays(int days) {
        return v(true, true, 2048, 512, 64, days, 3000, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withCooldown(int cooldown) {
        return v(true, true, 2048, 512, 64, 7, cooldown, 32, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withPackFiles(int packFiles) {
        return v(true, true, 2048, 512, 64, 7, 3000, packFiles, 8, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withPackMb(int packMb) {
        return v(true, true, 2048, 512, 64, 7, 3000, 32, packMb, "My Server", List.of("hi"));
    }

    private static ServerConfigValues withName(String name) {
        return v(true, true, 2048, 512, 64, 7, 3000, 32, 8, name, List.of("hi"));
    }

    private static ServerConfigValues withPhrases(List<String> phrases) {
        return v(true, true, 2048, 512, 64, 7, 3000, 32, 8, "My Server", phrases);
    }

    @Test
    void sensibleValuesPass() {
        assertNull(base().validate());
    }

    @Test
    void everyNumericFieldIsRangeChecked() {
        assertEquals(RANGE, withKb(0).validate());
        assertEquals(RANGE, withTotal(0).validate());
        assertEquals(RANGE, withAvatar(0).validate());
        assertEquals(RANGE, withDays(-1).validate());
        assertEquals(RANGE, withCooldown(-1).validate());
        assertEquals(RANGE, withPackFiles(0).validate());
        assertEquals(RANGE, withPackMb(0).validate());

        assertNull(v(true, true, 1, 1, 1, 0, 0, 1, 1, "", List.of()).validate(),
                "zero retention and zero cooldown are legitimate settings");
    }

    @Test
    void absurdUpperBoundsAreRefused() {
        assertEquals(RANGE, withKb(ServerConfigValues.MAX_FILE_KB_LIMIT + 1).validate());
        assertEquals(RANGE, withTotal(ServerConfigValues.MAX_STORE_MB_LIMIT + 1).validate());
        assertEquals(RANGE, withAvatar(ServerConfigValues.MAX_STORE_MB_LIMIT + 1).validate());
        assertEquals(RANGE, withDays(ServerConfigValues.MAX_RETENTION_DAYS + 1).validate());
        assertEquals(RANGE, withCooldown(ServerConfigValues.MAX_COOLDOWN_MS + 1).validate());
        assertEquals(RANGE, withPackFiles(ServerConfigValues.MAX_PACK_FILES_LIMIT + 1).validate());
        assertEquals(RANGE, withPackMb(ServerConfigValues.MAX_PACK_MB_LIMIT + 1).validate());

        assertNull(withKb(ServerConfigValues.MAX_FILE_KB_LIMIT).validate());
        assertNull(withPackMb(ServerConfigValues.MAX_PACK_MB_LIMIT).validate());
    }

    @Test
    void tooManyPhrasesAreRefusedRatherThanTrimmedSilently() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= ServerConfigValues.MAX_PHRASES; i++) {
            many.add("phrase " + i);
        }

        assertEquals(ServerConfigValues.ERR_PHRASES, withPhrases(many).validate());

        // One over-long phrase is merely sloppy: sanitize truncates it, so the
        // save still goes through (the count is what gets refused).
        assertNull(withPhrases(List.of("x".repeat(ServerConfigValues.MAX_PHRASE_CHARS + 5)))
                .sanitize().validate());
        assertEquals(ServerConfigValues.MAX_PHRASE_CHARS,
                withPhrases(List.of("x".repeat(ServerConfigValues.MAX_PHRASE_CHARS + 5)))
                        .sanitize().phrases().get(0).length());
    }

    @Test
    void aPaddedNameIsAcceptedButNotAnOverlongOne() {
        assertNull(withName("   " + "n".repeat(ServerConfigValues.MAX_NAME_CHARS) + "   ").validate());
        assertEquals(ServerConfigValues.ERR_NAME,
                withName("n".repeat(ServerConfigValues.MAX_NAME_CHARS + 1)).validate());
    }

    @Test
    void sanitizeTrimsTheSloppyPartsAndKeepsTheRest() {
        List<String> messy = new ArrayList<>();
        messy.add("  hi  ");
        messy.add("");
        messy.add("   ");
        messy.add(null);
        messy.add("x".repeat(ServerConfigValues.MAX_PHRASE_CHARS + 9));
        ServerConfigValues clean = v(true, true, 2048, 512, 64, 7, 3000, 32, 8, "  My Server  ", messy);

        ServerConfigValues result = clean.sanitize();

        assertEquals("My Server", result.packName());
        assertEquals(2, result.phrases().size());
        assertEquals("hi", result.phrases().get(0));
        assertEquals(ServerConfigValues.MAX_PHRASE_CHARS, result.phrases().get(1).length());
        assertNull(result.validate());
    }

    @Test
    void configRoundTripsThroughTheRecord() {
        AtomChatServerConfig config = new AtomChatServerConfig();
        config.hostingEnabled = false;
        config.packEnabled = false;
        config.maxFileKb = 123;
        config.packName = "Live";
        config.phrases = new ArrayList<>(List.of("a", "b"));

        ServerConfigValues values = ServerConfigValues.of(config);
        values.applyTo(config);

        assertEquals(123, config.maxFileKb);
        assertEquals(false, config.hostingEnabled);
        assertEquals(false, config.packEnabled);
        assertEquals("Live", config.packName);
        assertEquals(List.of("a", "b"), config.phrases);
        assertNull(ServerConfigValues.of(config).validate());
    }
}
