package top.tobyprime.mcedia_mtv.client.metadata;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia.api.media.Media;
import top.tobyprime.mcedia.api.media.MediaInfo;
import top.tobyprime.mcedia.api.media.MediaPlayInfo;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

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
