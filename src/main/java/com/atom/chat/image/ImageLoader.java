package com.atom.chat.image;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Image;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal async image loader/cache for chat images (CICode/URL).
 */
public final class ImageLoader {
    private static final ImageLoader INSTANCE = new ImageLoader();
    private final HttpClient client;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ConcurrentHashMap<String, Image> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();

    private ImageLoader() {
        this.client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    public Image get(String url) {
        Image cached = cache.get(url);
        if (cached != null) {
            return cached;
        }
        if ((url.startsWith("http://") || url.startsWith("https://")) && pending.putIfAbsent(url, Boolean.TRUE) == null) {
            loadAsync(url);
        }
        return null;
    }

    private void loadAsync(String url) {
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<byte[]> response = client.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );
                if (response.statusCode() == 200) {
                    Image image = Image.makeFromEncoded(response.body());
                    cache.put(url, image);
                }
            } catch (Exception e) {
                AtomChat.LOGGER.warn("Failed to load chat image {}", url, e);
            } finally {
                pending.remove(url);
            }
            return null;
        }, executor);
    }
}
