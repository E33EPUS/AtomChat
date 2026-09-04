package com.atom.chat.image;

import com.atom.chat.AtomChat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves a remote image URL to a local path as raw bytes.
 *
 * <p>This deliberately does not round-trip through Skia/ImageIO: GIF animation,
 * WebP and transparent PNGs must stay byte-identical. The future-based API is
 * intentionally small so a future e33chat-style banner system can be wired to
 * {@link CompletableFuture#whenComplete} without changing callers.
 */
public final class ImageSaver {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "AtomChat-ImageSave");
        t.setDaemon(true);
        return t;
    });

    private ImageSaver() {
    }

    public static CompletableFuture<Path> save(String url, Path target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<byte[]> response = CLIENT.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(Duration.ofSeconds(30))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, response.body());
                return target;
            } catch (Exception e) {
                AtomChat.LOGGER.warn("Failed to save image {} to {}", url, target, e);
                throw new RuntimeException("Failed to save image", e);
            }
        }, EXECUTOR);
    }
}
