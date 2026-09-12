package com.atom.chat.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Retention rules for the two server-hosted stores. The sweep takes the wall
 * clock as an argument so the age cutoff can be pinned instead of raced.
 */
class CompanionMaintenanceTest {
    private static final long DAY_MS = 86_400_000L;
    /** The default retention, in days. */
    private static final int WEEK = 7;

    @TempDir
    Path dir;

    /** Writes a file of {@code size} bytes stamped {@code ageDays} in the past. */
    private Path file(String name, int size, int ageDays, long now) throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, new byte[size]);
        Files.setLastModifiedTime(path, FileTime.fromMillis(now - ageDays * DAY_MS));
        return path;
    }

    @Test
    void filesOlderThanTheRetentionAreDeleted() throws Exception {
        long now = System.currentTimeMillis();
        Path stale = file("stale.png", 40, 8, now);
        Path fresh = file("fresh.png", 30, 6, now);

        long freed = CompanionMaintenance.sweep(dir, WEEK, 1_000L, now);

        assertFalse(Files.exists(stale), "an 8-day-old file must not survive a 7-day policy");
        assertTrue(Files.exists(fresh), "a 6-day-old file is still inside the policy");
        assertEquals(40L, freed);
    }

    @Test
    void zeroDaysDisablesTheAgeRule() throws Exception {
        long now = System.currentTimeMillis();
        Path ancient = file("ancient.gif", 12, 9_999, now);

        assertEquals(0L, CompanionMaintenance.sweep(dir, 0, 1_000L, now));

        assertTrue(Files.exists(ancient), "0 means keep forever, whatever the size cap says");
    }

    @Test
    void sizeCapDeletesTheOldestFirst() throws Exception {
        long now = System.currentTimeMillis();
        // The age rule is off here so only the byte cap can explain a deletion.
        Path oldest = file("a.bin", 100, 4, now);
        Path older = file("b.bin", 100, 3, now);
        Path newer = file("c.bin", 100, 2, now);
        Path newest = file("d.bin", 100, 1, now);

        long freed = CompanionMaintenance.sweep(dir, 0, 250L, now);

        assertFalse(Files.exists(oldest));
        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
        assertTrue(Files.exists(newest));
        assertEquals(200L, freed);
    }

    @Test
    void abandonedTempFilesAreSweptToo() throws Exception {
        long now = System.currentTimeMillis();
        // A write killed mid-move leaves a .tmp behind; both directories hold
        // only files this mod wrote, so nothing is exempt from the age rule.
        Path tmp = file("deadbeef.png.tmp", 8, 30, now);

        CompanionMaintenance.sweep(dir, WEEK, 1_000L, now);

        assertFalse(Files.exists(tmp));
    }

    @Test
    void aMissingDirectoryIsNotAnError() {
        assertEquals(0L, CompanionMaintenance.sweep(dir.resolve("never-created"), WEEK, 1L, 1L));
    }

    @Test
    void storedFilesInsideThePolicyAreLeftAlone() throws Exception {
        long now = System.currentTimeMillis();
        Path avatar = file("0000.png", 64, 1, now);
        Path media = file("beef.webp", 512, 6, now);

        assertEquals(0L, CompanionMaintenance.sweep(dir, WEEK, 100_000L, now));

        assertTrue(Files.exists(avatar));
        assertTrue(Files.exists(media));
    }
}
