package top.tobyprime.mcedia_mtv.client.channel.worldui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiPlaylistCacheTest {
    @Test
    void newRevisionDropsPagesAndRequestsVisiblePagesOnlyOnce() {
        var cache = new WorldUiPlaylistCache();
        cache.applyManifest(new WorldUiPlaylistManifest("channel", 3L, 96, 0, "SEQUENTIAL"));

        assertEquals(List.of(0, 32), cache.missingOffsetsForVisibleRange("channel", 0, 40));
        assertTrue(cache.missingOffsetsForVisibleRange("channel", 0, 40).isEmpty());

        cache.applyPage(new WorldUiPlaylistPage("channel", 3L, 96, 0, "SEQUENTIAL", 0, List.of("a")));
        cache.failPageRequest("channel", 32);
        assertEquals(List.of(32), cache.missingOffsetsForVisibleRange("channel", 0, 40));

        cache.applyManifest(new WorldUiPlaylistManifest("channel", 4L, 96, 0, "SEQUENTIAL"));
        assertTrue(cache.pageAt("channel", 0).isEmpty());
        assertEquals(List.of(0), cache.missingOffsetsForVisibleRange("channel", 0, 1));
    }

    @Test
    void stalePageDoesNotReplaceCurrentRevision() {
        var cache = new WorldUiPlaylistCache();
        cache.applyManifest(new WorldUiPlaylistManifest("channel", 4L, 1, 0, "SEQUENTIAL"));

        cache.applyPage(new WorldUiPlaylistPage("channel", 3L, 1, 0, "SEQUENTIAL", 0, List.of("stale")));

        assertTrue(cache.pageAt("channel", 0).isEmpty());
    }

    @Test
    void pendingPageCanBeRetriedAfterItsLocalTimeout() {
        var clock = new AtomicLong(1L);
        var cache = new WorldUiPlaylistCache(clock::get);
        cache.applyManifest(new WorldUiPlaylistManifest("channel", 3L, 64, 0, "SEQUENTIAL"));

        assertEquals(List.of(0), cache.missingOffsetsForVisibleRange("channel", 0, 1));
        assertTrue(cache.missingOffsetsForVisibleRange("channel", 0, 1).isEmpty());

        clock.addAndGet(3_000_000_000L);

        assertEquals(List.of(0), cache.missingOffsetsForVisibleRange("channel", 0, 1));
    }
}
