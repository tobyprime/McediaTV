package top.tobyprime.mcedia_mtv.client.metadata;

import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class MtvMediaMetadataCache {
    private static final int DEFAULT_CAPACITY = 64;
    private static final Executor DEFAULT_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "mtv-metadata-resolver");
        thread.setDaemon(true);
        return thread;
    });
    private static final MtvMediaMetadataCache INSTANCE = new MtvMediaMetadataCache();

    private final int capacity;
    private final Executor executor;
    private final Function<String, Media> resolver;
    private final Object lock = new Object();
    private final LinkedHashMap<String, MtvMediaMetadata> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<String, CompletableFuture<MtvMediaMetadata>> inFlight = new LinkedHashMap<>();

    public MtvMediaMetadataCache() {
        this(DEFAULT_CAPACITY, DEFAULT_EXECUTOR, MediaResolvers::resolve);
    }

    public MtvMediaMetadataCache(int capacity, Executor executor, Function<String, Media> resolver) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("metadata cache capacity must be positive");
        }
        this.capacity = capacity;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static MtvMediaMetadataCache getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<MtvMediaMetadata> resolveAsync(String rawUrl) {
        String normalizedUrl;
        try {
            normalizedUrl = normalizeUrl(rawUrl);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(MtvMediaMetadata.failed(rawUrl == null ? "" : rawUrl.trim(), message(e)));
        }

        synchronized (lock) {
            MtvMediaMetadata cached = entries.get(normalizedUrl);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            CompletableFuture<MtvMediaMetadata> pending = inFlight.get(normalizedUrl);
            if (pending != null) {
                return pending;
            }
            if (inFlight.size() >= capacity) {
                return CompletableFuture.completedFuture(MtvMediaMetadata.failed(normalizedUrl, "metadata resolver is busy"));
            }

            var future = new CompletableFuture<MtvMediaMetadata>();
            inFlight.put(normalizedUrl, future);
            executor.execute(() -> {
                MtvMediaMetadata result;
                try {
                    result = resolve(normalizedUrl);
                } catch (Exception e) {
                    result = MtvMediaMetadata.failed(normalizedUrl, message(e));
                }
                synchronized (lock) {
                    inFlight.remove(normalizedUrl);
                    entries.put(normalizedUrl, result);
                    while (entries.size() > capacity) {
                        entries.remove(entries.keySet().iterator().next());
                    }
                }
                future.complete(result);
            });
            return future;
        }
    }

    public void clear() {
        synchronized (lock) {
            entries.clear();
            inFlight.clear();
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private MtvMediaMetadata resolve(String normalizedUrl) {
        Media media = Objects.requireNonNull(resolver.apply(normalizedUrl), "resolver returned null media");
        MediaInfo info = Objects.requireNonNull(media.getInfo(), "media info is missing");
        String description = info.getExtraMetadata().getOrDefault("description", "");
        return new MtvMediaMetadata(
                normalizedUrl,
                info.getTitle(),
                info.getArtist(),
                description,
                info.getPlatform(),
                info.getCoverUrl() == null ? "" : info.getCoverUrl(),
                MtvMediaMetadata.Status.RESOLVED,
                ""
        );
    }

    static String normalizeUrl(String rawUrl) {
        if (rawUrl == null) {
            throw new IllegalArgumentException("media URL is null");
        }
        String value = rawUrl.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("media URL is blank");
        }
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return value;
            }
            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException e) {
            return value;
        }
    }

    private static String message(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }
}
