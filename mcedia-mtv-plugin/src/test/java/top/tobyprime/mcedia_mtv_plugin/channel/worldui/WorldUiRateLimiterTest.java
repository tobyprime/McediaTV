package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiRateLimiterTest {
    @Test
    void tenthControlIsAcceptedAndEleventhIsRateLimited() {
        var now = new AtomicLong(1_000L);
        var limiter = new WorldUiRateLimiter(now::get);
        UUID player = UUID.randomUUID();

        for (int index = 0; index < 10; index++) {
            assertTrue(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.CONTROL));
        }
        assertFalse(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.CONTROL));

        now.addAndGet(1_000L);
        assertTrue(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.CONTROL));
    }

    @Test
    void pageAndWatchBudgetsAreIndependent() {
        var limiter = new WorldUiRateLimiter(() -> 10_000L);
        UUID player = UUID.randomUUID();

        for (int index = 0; index < 4; index++) {
            assertTrue(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.PAGE));
        }
        assertFalse(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.PAGE));
        assertTrue(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.WATCH));
        assertTrue(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.WATCH));
        assertFalse(limiter.tryAcquire(player, WorldUiRateLimiter.RequestType.WATCH));
    }
}
