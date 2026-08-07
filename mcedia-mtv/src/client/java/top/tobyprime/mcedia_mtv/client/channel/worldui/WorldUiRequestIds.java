package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.concurrent.atomic.AtomicLong;

/** Allocates one monotonically increasing request sequence for every MTV UI control. */
public final class WorldUiRequestIds {
    private static final AtomicLong NEXT = new AtomicLong();

    private WorldUiRequestIds() {
    }

    public static long next() {
        return NEXT.updateAndGet(current -> current == Long.MAX_VALUE ? 1L : current + 1L);
    }
}
