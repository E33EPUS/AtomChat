package com.atom.chat.image;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The save action has to pick the right transport for a url. Regression cover
 * for the report where saving a server-hosted image failed with
 * "invalid URI scheme atomchat-media": that url has no HTTP transport at all.
 */
class ImageSaverTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearCompanion() {
        ImageLoader.setMediaFetcher(null);
    }

    @Test
    void hostedMediaIsFetchedThroughTheCompanionAndWrittenVerbatim() throws Exception {
        byte[] payload = "hosted-media-bytes".getBytes(StandardCharsets.UTF_8);
        ImageLoader.setMediaFetcher(url -> payload);
        Path target = tempDir.resolve("saved.png");

        Path saved = ImageSaver.save("atomchat-media:abc123.png", target).get(10, TimeUnit.SECONDS);

        assertEquals(target, saved);
        assertArrayEquals(payload, Files.readAllBytes(target));
    }

    @Test
    void aFailedCompanionFetchLeavesNothingBehind() {
        ImageLoader.setMediaFetcher(url -> {
            throw new IOException("companion offline");
        });
        Path target = tempDir.resolve("never-written.png");

        assertThrows(ExecutionException.class,
                () -> ImageSaver.save("atomchat-media:abc123.png", target).get(10, TimeUnit.SECONDS));
        assertFalse(Files.exists(target), "a failed save must not leave a partial file");
    }

    @Test
    void withoutACompanionTheMediaUrlFailsInsteadOfReachingTheNetwork() {
        Path target = tempDir.resolve("no-companion.png");

        assertThrows(ExecutionException.class,
                () -> ImageSaver.save("atomchat-media:abc123.png", target).get(10, TimeUnit.SECONDS));
        assertFalse(Files.exists(target));
    }
}
