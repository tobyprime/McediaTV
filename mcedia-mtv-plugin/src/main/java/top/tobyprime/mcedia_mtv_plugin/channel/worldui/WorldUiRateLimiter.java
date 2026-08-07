package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Fixed one-second request windows, keyed per player and request family. */
public final class WorldUiRateLimiter {
    public enum RequestType {
        CONTROL,
        PAGE,
        WATCH
    }

    private final Limits limits;
    private final LongSupplier clockMillis;
    private final Map<UUID, EnumMap<RequestType, Window>> windows = new ConcurrentHashMap<>();

    public WorldUiRateLimiter() {
        this(Limits.defaults(), System::currentTimeMillis);
    }

    public WorldUiRateLimiter(LongSupplier clockMillis) {
        this(Limits.defaults(), clockMillis);
    }

    public WorldUiRateLimiter(Limits limits) {
        this(limits, System::currentTimeMillis);
    }

    public WorldUiRateLimiter(Limits limits, LongSupplier clockMillis) {
        this.limits = limits == null ? Limits.defaults() : limits;
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
            if (window.count >= limits.forType(type)) {
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

    /** Independently configurable per-player one-second request budgets. */
    public record Limits(int controlPerSecond, int pagePerSecond, int watchPerSecond) {
        public Limits {
            if (controlPerSecond <= 0 || pagePerSecond <= 0 || watchPerSecond <= 0) {
                throw new IllegalArgumentException("world UI rate limits must be positive");
            }
        }

        public static Limits defaults() {
            return new Limits(10, 4, 2);
        }

        private int forType(RequestType type) {
            return switch (type) {
                case CONTROL -> controlPerSecond;
                case PAGE -> pagePerSecond;
                case WATCH -> watchPerSecond;
            };
        }
    }
}
