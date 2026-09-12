package com.atom.chat.pack;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-file diffing: what the client must fetch, and what it must drop. */
class PackDiffTest {
    private static ServerPack.FileEntry entry(String name, String content) {
        return new ServerPack.FileEntry(name, ServerPack.sha256Hex(content.getBytes()), content.length());
    }

    private static ServerPack manifest(ServerPack.FileEntry... entries) {
        return new ServerPack("0".repeat(64), List.of(entries), List.of("hi"), "srv", null);
    }

    @Test
    void missingAndChangedFilesAreFetched() {
        ServerPack.FileEntry a = entry("a.png", "alpha");
        ServerPack.FileEntry b = entry("b.png", "bravo");
        Map<String, String> local = new LinkedHashMap<>();
        local.put("a.png", a.sha256Hex());
        local.put("b.png", ServerPack.sha256Hex("stale".getBytes()));

        PackDiff.Plan plan = PackDiff.plan(manifest(a, b), local);

        assertEquals(List.of("b.png"), plan.fetch().stream().map(ServerPack.FileEntry::name).toList());
        assertEquals(0, plan.remove().size());
        assertFalse(plan.isUpToDate());
    }

    @Test
    void matchingFilesAreLeftAlone() {
        ServerPack.FileEntry a = entry("a.png", "alpha");
        Map<String, String> local = Map.of("a.png", a.sha256Hex().toUpperCase());

        PackDiff.Plan plan = PackDiff.plan(manifest(a), local);

        assertTrue(plan.isUpToDate(), "hash comparison ignores case");
        assertEquals(0L, plan.fetchBytes());
    }

    @Test
    void aDeletedFileIsTheOnlyThingFetchedAgain() {
        ServerPack.FileEntry a = entry("a.png", "alpha");
        ServerPack.FileEntry b = entry("b.png", "bravo");
        ServerPack.FileEntry c = entry("c.png", "charlie");
        Map<String, String> local = new LinkedHashMap<>();
        local.put("a.png", a.sha256Hex());
        local.put("c.png", c.sha256Hex());

        PackDiff.Plan plan = PackDiff.plan(manifest(a, b, c), local);

        assertEquals(List.of("b.png"), plan.fetch().stream().map(ServerPack.FileEntry::name).toList());
        assertEquals(b.size(), plan.fetchBytes());
    }

    @Test
    void leftoversAreMarkedForRemoval() {
        ServerPack.FileEntry a = entry("a.png", "alpha");
        Map<String, String> local = new LinkedHashMap<>();
        local.put("a.png", a.sha256Hex());
        local.put("gone.png", ServerPack.sha256Hex("old".getBytes()));
        local.put("also-gone.gif", ServerPack.sha256Hex("older".getBytes()));

        PackDiff.Plan plan = PackDiff.plan(manifest(a), local);

        assertEquals(List.of("gone.png", "also-gone.gif"), plan.remove());
        assertFalse(plan.isUpToDate());
    }

    @Test
    void anEmptyClientFetchesEverythingInManifestOrder() {
        PackDiff.Plan plan = PackDiff.plan(
                manifest(entry("a.png", "1"), entry("b.png", "22"), entry("c.png", "333")), Map.of());

        assertEquals(List.of("a.png", "b.png", "c.png"),
                plan.fetch().stream().map(ServerPack.FileEntry::name).toList());
        assertEquals(6L, plan.fetchBytes());
    }

    @Test
    void anEmptyManifestClearsTheClient() {
        PackDiff.Plan plan = PackDiff.plan(manifest(), Map.of("a.png", "0".repeat(64)));

        assertEquals(List.of("a.png"), plan.remove());
        assertTrue(plan.fetch().isEmpty());
    }
}
