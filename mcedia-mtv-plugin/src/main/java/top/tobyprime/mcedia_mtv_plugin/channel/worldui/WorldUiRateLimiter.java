package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Fixed one-second request windows, keyed per player and request family. */
public final class WorldUiRateLimiter {
    public enum RequestType {
        CONTROL(10),
        PAGE(4),
        WATCH(2);

        private final int limitPerSecond;

        RequestType(int limitPerSecond) {
            this.limitPerSecond = limitPerSecond;
        }
    }

    private final LongSupplier clockMillis;
    private final Map<UUID, EnumMap<RequestType, Window>> windows = new ConcurrentHashMap<>();

    public WorldUiRateLimiter() {
        this(System::currentTimeMillis);
    }

    public WorldUiRateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis == null ? System::currentTimeMillis : clockMillis;
    }

    public boolean tryAcquire(UUID playerId, RequestType type) {
        if (playerId == null || type == null) {
            return false;
        }
        long now = clockMillis.getAsLong();
        var playerWindows = windows.computeIfAbsent(playerId, ignored -> new EnumMap<>(RequestType.class));
        synchronized (playerWindows) {
            Window window = playerWindows.get(type);
            if (window == null || now - window.startedAtMillis >= 1_000L || now < window.startedAtMillis) {
                playerWindows.put(type, new Window(now, 1));
                return true;
            }
            if (window.count >= type.limitPerSecond) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            windows.remove(playerId);
        }
    }

    public void clear() {
        windows.clear();
    }

    private static final class Window {
        private final long startedAtMillis;
        private int count;

        private Window(long startedAtMillis, int count) {
            this.startedAtMillis = startedAtMillis;
            this.count = count;
        }
    }
}
