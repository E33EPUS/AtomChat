package com.atom.chat.image;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Async image loader/cache for chat images (CICode/URL). Hardened after the
 * 0.1.10 grill — the original fetched every URL the moment a message arrived,
 * kept full-resolution bitmaps in an unbounded map and had no disk layer:
 * a busy image chat could pin hundreds of MB of RAM and burst-download a
 * whole scrollback.
 *
 * <p>Four layers now:
 * <ol>
 *   <li><b>Viewport gating</b> — downloads only start for images actually
 *       drawn ({@code get(url, true)}); the message list already skips
 *       off-screen messages, so scrolling fast no longer queues the entire
 *       history.</li>
 *   <li><b>Downsampling</b> — decoded bitmaps are scaled once to a
 *       {@value #MAX_DIM}px long edge (a 20 MP photo drops from ~80 MB to
 *       ~2 MB, far sharper than the bubble ever displays).</li>
 *   <li><b>Bounded memory</b> — an access-order LRU of {@value #MAX_CACHED}
 *       images; evicted entries rely on Skija's managed finalisation rather
 *       than an eager {@code close()} so a bitmap drawn in the current frame
 *       can never be pulled from under the renderer.</li>
 *   <li><b>Disk cache</b> — raw bytes under {@code config/atomchat/image-cache/}
 *       (SHA-256 of the URL), so re-entering a world does not re-download.</li>
 * </ol>
 *
 * <p>Failed fetches enter a short negative cache so a dead URL is not
 * retried every frame.
 */
public final class ImageLoader {
    /** LRU capacity in decoded images. */
    public static final int MAX_CACHED = 48;
    /** Longest allowed side of a cached bitmap, in pixels. */
    public static final int MAX_DIM = 768;
    /** How long a failed URL stays blacklisted. */
    public static final long FAILURE_TTL_MS = 60_000L;

    /** Byte source for a URL; HTTP by default, injected in tests. */
    interface Fetcher {
        byte[] fetch(String url) throws Exception;
    }

    private static final ImageLoader INSTANCE = new ImageLoader(
            ImageLoader::httpFetch, System::currentTimeMillis,
            Executors.newFixedThreadPool(3));

    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final Executor executor;

    private final LinkedHashMap<String, Image> cache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Image> eldest) {
            // No eager close(): the renderer may still hold this Image for the
            // current frame; Skija finalises the native memory instead.
            return size() > MAX_CACHED;
        }
    };
    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> failedUntil = new ConcurrentHashMap<>();
    private volatile Path diskDir;

    private ImageLoader(Fetcher fetcher, LongSupplier clock, Executor executor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.executor = executor;
    }

    public static ImageLoader get() {
        return INSTANCE;
    }

    /** Enables the disk layer; called once by the client entrypoint. */
    public void init(Path diskDir) {
        this.diskDir = diskDir;
    }

    private static byte[] httpFetch(String url) throws Exception {
        HttpResponse<byte[]> response = HttpClientHolder.CLIENT.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Static holder keeps the HttpClient lazy: tests never build one. */
    private static final class HttpClientHolder {
        static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Memory/disk hit only; never triggers a download. */
    public Image get(String url) {
        return get(url, false);
    }

    /**
     * Returns the decoded image, or null while it loads. Only a
     * {@code visible} call may start a fetch — the message list calls this
     * from its draw path, which already covers the viewport (+80 px preload
     * margin).
     */
    public Image get(String url, boolean visible) {
        if (url == null || url.isBlank()) {
            return null;
        }
        synchronized (cache) {
            Image cached = cache.get(url);
            if (cached != null) {
                return cached;
            }
        }
        Long failed = failedUntil.get(url);
        if (failed != null && clock.getAsLong() < failed) {
            return null;
        }
        Path disk = diskFile(url);
        boolean isHttp = url.startsWith("http://") || url.startsWith("https://");
        if (visible && (disk != null || isHttp)
                && pending.putIfAbsent(url, Boolean.TRUE) == null) {
            executor.execute(() -> load(url, disk));
            // The executor may be direct (tests) and complete synchronously;
            // re-check before reporting a miss so the first visible call can
            // already serve the image.
            synchronized (cache) {
                Image loaded = cache.get(url);
                if (loaded != null) {
                    return loaded;
                }
            }
        }
        return null;
    }

    private void load(String url, Path disk) {
        try {
            byte[] bytes;
            if (disk != null && Files.exists(disk)) {
                bytes = Files.readAllBytes(disk);
            } else {
                bytes = fetcher.fetch(url);
                if (disk != null) {
                    try {
                        Files.createDirectories(disk.getParent());
                        Files.write(disk, bytes);
                    } catch (Exception e) {
                        AtomChat.LOGGER.warn("Failed to write image cache for {}", url, e);
                    }
                }
            }
            Image image = downscale(Image.makeFromEncoded(bytes));
            if (image != null) {
                synchronized (cache) {
                    cache.put(url, image);
                }
                failedUntil.remove(url);
            } else {
                fail(url);
            }
        } catch (Exception e) {
            AtomChat.LOGGER.warn("Failed to load chat image {}", url, e);
            fail(url);
        } finally {
            pending.remove(url);
        }
    }

    private void fail(String url) {
        failedUntil.put(url, clock.getAsLong() + FAILURE_TTL_MS);
    }

    /** Test seam: injectable byte source, clock and executor (direct = sync). */
    static ImageLoader forTest(Fetcher fetcher, LongSupplier clock, Executor executor) {
        return new ImageLoader(fetcher, clock, executor);
    }

    /** Test seam: drops all memory/negative state (disk cache untouched). */
    void resetMemory() {
        synchronized (cache) {
            cache.clear();
        }
        failedUntil.clear();
        pending.clear();
    }

    private Path diskFile(String url) {
        Path dir = diskDir;
        if (dir == null) {
            return null;
        }
        return dir.resolve(sha256Hex(url) + ".bin");
    }

    /** Fits the image inside {@link #MAX_DIM} without ever upscaling it. */
    static Image downscale(Image source) {
        if (source == null) {
            return null;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        float scale = Math.min(1.0F, Math.min((float) MAX_DIM / w, (float) MAX_DIM / h));
        if (scale >= 0.999F) {
            return source;
        }
        int tw = Math.max(1, Math.round(w * scale));
        int th = Math.max(1, Math.round(h * scale));
        try (Surface surface = Surface.makeRasterN32Premul(tw, th)) {
            Canvas canvas = surface.getCanvas();
            try (Paint paint = new Paint().setAntiAlias(true)) {
                canvas.drawImageRect(source,
                        Rect.makeXYWH(0, 0, w, h),
                        Rect.makeXYWH(0, 0, tw, th),
                        SamplingMode.LINEAR, paint, false);
            }
            source.close();
            return surface.makeImageSnapshot();
        }
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is mandatory on every JVM; unreachable.
            throw new IllegalStateException(e);
        }
    }
}
