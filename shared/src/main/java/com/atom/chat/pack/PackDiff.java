package com.atom.chat.pack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a client has to change to match a manifest: the files whose content is
 * missing or different, plus the leftovers the server no longer offers.
 *
 * <p>Per file rather than whole-pack (0.2.9 decision 21): deleting one emote
 * costs one file to re-download, not the whole pack. Nothing here touches the
 * disk — the caller supplies the hashes it found, so the rule is testable.
 */
public final class PackDiff {
    /**
     * @param fetch  manifest entries to download, in manifest order
     * @param remove local names that are not in the manifest any more
     */
    public record Plan(List<ServerPack.FileEntry> fetch, List<String> remove) {
        public Plan {
            fetch = List.copyOf(fetch);
            remove = List.copyOf(remove);
        }

        public boolean isUpToDate() {
            return fetch.isEmpty() && remove.isEmpty();
        }

        /** Bytes the plan would download; used for the panel's progress line. */
        public long fetchBytes() {
            long total = 0L;
            for (ServerPack.FileEntry entry : fetch) {
                total += entry.size();
            }
            return total;
        }
    }

    private PackDiff() {
    }

    /** @param localHashes name to sha256 for every file the client currently holds */
    public static Plan plan(ServerPack manifest, Map<String, String> localHashes) {
        Map<String, String> local = localHashes == null ? Map.of() : localHashes;
        List<ServerPack.FileEntry> fetch = new ArrayList<>();
        Set<String> wanted = new LinkedHashSet<>();
        for (ServerPack.FileEntry entry : manifest.files()) {
            wanted.add(entry.name());
            String have = local.get(entry.name());
            if (have == null || !have.equalsIgnoreCase(entry.sha256Hex())) {
                fetch.add(entry);
            }
        }
        List<String> remove = new ArrayList<>();
        for (String name : local.keySet()) {
            if (!wanted.contains(name)) {
                remove.add(name);
            }
        }
        return new Plan(fetch, remove);
    }
}
