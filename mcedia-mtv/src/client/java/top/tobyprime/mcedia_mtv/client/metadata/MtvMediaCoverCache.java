package top.tobyprime.mcedia_mtv.client.metadata;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded, client-only Cover downloader. It never sends cover bytes or metadata to the server. */
public final class MtvMediaCoverCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvMediaCoverCache.class);
    private static final int DEFAULT_CAPACITY = 64;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Covers are drawn on small world-UI quads, so 1024px is more than enough and
     *  keeps the re-encoded PNG and the uploaded texture small. */
    private static final int MAX_DIMENSION = 1024;
    private static final int MAX_CONCURRENT = 2;
    private static final Executor DEFAULT_EXECUTOR = Executors.newFixedThreadPool(MAX_CONCURRENT, runnable -> {
        var thread = new Thread(runnable, "mtv-cover-loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final MtvMediaCoverCache INSTANCE = new MtvMediaCoverCache(DEFAULT_CAPACITY, DEFAULT_EXECUTOR);

    private final int capacity;
    private final Executor executor;
    private final Object lock = new Object();
    private final LinkedHashMap<String, MtvMediaCover> entries = new LinkedHashMap<>(16, .75F, true);
    private final Map<String, CompletableFuture<MtvMediaCover>> inFlight = new LinkedHashMap<>();

    public MtvMediaCoverCache(int capacity, Executor executor) {
        if (capacity <= 0) throw new IllegalArgumentException("cover cache capacity must be positive");
        this.capacity = capacity;
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static MtvMediaCoverCache getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<MtvMediaCover> loadAsync(String rawUrl) {
        String url = normalize(rawUrl);
        if (url.isBlank()) return CompletableFuture.completedFuture(MtvMediaCover.failed(url, "cover URL is blank"));
        synchronized (lock) {
            var cached = entries.get(url);
            if (cached != null) return CompletableFuture.completedFuture(cached);
            var pending = inFlight.get(url);
            if (pending != null) return pending;
            // The fixed-size executor already queues excess work; do NOT reject here.
            // A rejected cover would never be retried (metadata resolution caches the
            // result), leaving the world UI permanently without that thumbnail.
            var future = new CompletableFuture<MtvMediaCover>();
            inFlight.put(url, future);
            executor.execute(() -> {
                MtvMediaCover result;
                try { result = download(url); }
                catch (Exception e) { result = MtvMediaCover.failed(url, message(e)); }
                if (result.status() == MtvMediaCover.Status.RESOLVED) {
                    LOGGER.debug("MTV cover downloaded: url={}, bytes={}, size={}x{}",
                            url, result.bytes().length, result.width(), result.height());
                } else {
                    LOGGER.info("MTV cover download failed: url={}, reason={}", url, result.errorReason());
                }
                synchronized (lock) {
                    inFlight.remove(url);
                    entries.put(url, result);
                    while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
                }
                future.complete(result);
            });
            return future;
        }
    }

    public MtvMediaCover cached(String rawUrl) {
        String url = normalize(rawUrl);
        synchronized (lock) { return entries.get(url); }
    }

    public String statusLabel(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "fallback";
        MtvMediaCover cover = cached(rawUrl);
        if (cover == null) return "loading";
        return cover.status() == MtvMediaCover.Status.RESOLVED ? "loaded" : "fallback";
    }

    public void clear() {
        synchronized (lock) { entries.clear(); inFlight.clear(); }
    }

    static MtvMediaCover decode(String url, byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (image == null) throw new IllegalArgumentException("cover is not a supported image");
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("cover dimensions are invalid");
        }
        image = fitWithin(image, MAX_DIMENSION);
        // Minecraft's NativeImage only decodes PNG, so re-encode any source format
        // (bilibili covers are JPEG) to PNG; otherwise the world UI can never upload it.
        var pngOut = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", pngOut)) {
            throw new IllegalArgumentException("cover could not be re-encoded to PNG");
        }
        return new MtvMediaCover(url, pngOut.toByteArray(), image.getWidth(), image.getHeight(), MtvMediaCover.Status.RESOLVED, "");
    }

    private static BufferedImage fitWithin(BufferedImage image, int maxDimension) {
        int width = image.getWidth(), height = image.getHeight();
        if (width <= maxDimension && height <= maxDimension) {
            return image;
        }
        double scale = (double) maxDimension / Math.max(width, height);
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        var scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static MtvMediaCover download(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("cover scheme is unsupported");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(3_000);
        connection.setReadTimeout(5_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "image/*");
        try {
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IllegalArgumentException("cover HTTP status " + connection.getResponseCode());
            }
            long length = connection.getContentLengthLong();
            if (length > MAX_BYTES) throw new IllegalArgumentException("cover response exceeds byte limit");
            try (InputStream input = connection.getInputStream(); var output = new ByteArrayOutputStream((int) Math.max(0, Math.min(MAX_BYTES, length)))) {
                byte[] buffer = new byte[8192];
                int total = 0, read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) throw new IllegalArgumentException("cover response exceeds byte limit");
                    output.write(buffer, 0, read);
                }
                return decode(url, output.toByteArray());
            }
        } finally { connection.disconnect(); }
    }

    private static String normalize(String rawUrl) { return rawUrl == null ? "" : rawUrl.trim(); }
    private static String message(Throwable error) { return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage(); }
}
