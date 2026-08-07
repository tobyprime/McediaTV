package top.tobyprime.mcedia_mtv.client.metadata;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtvMediaMetadataCacheTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void repeatedUrlIsResolvedOnceAndDescriptionIsLocalMetadata() {
        var resolveCount = new AtomicInteger();
        var cache = new MtvMediaMetadataCache(4, DIRECT_EXECUTOR, url -> {
            resolveCount.incrementAndGet();
            return media(new MediaInfo("Title", "Artist", "https://img.test/cover", "test",
                    Map.of("description", "Description")));
        });

        var first = cache.resolveAsync(" https://example.test/media ").join();
        var second = cache.resolveAsync("https://example.test/media").join();

        assertEquals(1, resolveCount.get());
        assertEquals(first, second);
        assertEquals("Description", first.description());
        assertEquals("https://img.test/cover", first.coverUrl());
        assertEquals(MtvMediaMetadata.Status.RESOLVED, first.status());
    }

    @Test
    void unsupportedMediaIsCachedAsFailureWithoutThrowingToTheUi() {
        var resolveCount = new AtomicInteger();
        var cache = new MtvMediaMetadataCache(4, DIRECT_EXECUTOR, url -> {
            resolveCount.incrementAndGet();
            throw new IllegalArgumentException("unsupported");
        });

        var result = cache.resolveAsync("https://unsupported.test/media").join();
        var repeated = cache.resolveAsync("https://unsupported.test/media").join();

        assertEquals(1, resolveCount.get());
        assertEquals(MtvMediaMetadata.Status.FAILED, result.status());
        assertTrue(result.errorReason().contains("unsupported"));
        assertEquals(result, repeated);
    }

    @Test
    void resolvedMediaPrefetchesItsCoverLocally() {
        var prefetchedCover = new AtomicReference<String>();
        var cache = new MtvMediaMetadataCache(4, DIRECT_EXECUTOR,
                url -> media(new MediaInfo("Title", "Artist", "https://img.test/cover", "test", Map.of())),
                prefetchedCover::set);

        cache.resolveAsync("https://example.test/media").join();

        assertEquals("https://img.test/cover", prefetchedCover.get());
    }

    @Test
    void failedOrCoverlessMediaDoesNotPrefetch() {
        var prefetchCount = new AtomicInteger();
        Consumer<String> prefetcher = ignored -> prefetchCount.incrementAndGet();
        var coverless = new MtvMediaMetadataCache(4, DIRECT_EXECUTOR,
                url -> media(new MediaInfo("Title", "Artist", "", "test", Map.of())), prefetcher);
        var failing = new MtvMediaMetadataCache(4, DIRECT_EXECUTOR,
                url -> { throw new IllegalArgumentException("unsupported"); }, prefetcher);

        coverless.resolveAsync("https://example.test/coverless").join();
        failing.resolveAsync("https://example.test/failing").join();

        assertEquals(0, prefetchCount.get());
    }

    private static Media media(MediaInfo info) {
        return new Media() {
            @Override
            public MediaPlayInfo getPlayInfo() {
                return new MediaPlayInfo("https://example.test/stream");
            }

            @Override
            public MediaInfo getInfo() {
                return info;
            }
        };
    }
}
