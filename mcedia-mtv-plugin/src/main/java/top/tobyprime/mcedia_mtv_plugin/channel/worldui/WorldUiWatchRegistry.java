package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps a bounded one-target watch selection per connected player. */
public final class WorldUiWatchRegistry {
    private final Map<UUID, UUID> watchedByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> watchersByMtv = new ConcurrentHashMap<>();

    public void watch(UUID playerId, UUID mtvId) {
        if (playerId == null || mtvId == null) {
            return;
        }
        UUID previous = watchedByPlayer.put(playerId, mtvId);
        if (previous != null && !previous.equals(mtvId)) {
            removeWatcher(previous, playerId);
        }
        watchersByMtv.computeIfAbsent(mtvId, ignored -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public void unwatch(UUID playerId) {
        if (playerId == null) {
            return;
        }
        UUID previous = watchedByPlayer.remove(playerId);
        if (previous != null) {
            removeWatcher(previous, playerId);
        }
    }

    public Set<UUID> watchers(UUID mtvId) {
        Set<UUID> watchers = watchersByMtv.get(mtvId);
        return watchers == null ? Set.of() : Set.copyOf(watchers);
    }

    public void clear() {
        watchedByPlayer.clear();
        watchersByMtv.clear();
    }

    private void removeWatcher(UUID mtvId, UUID playerId) {
        watchersByMtv.computeIfPresent(mtvId, (ignored, watchers) -> {
            watchers.remove(playerId);
            return watchers.isEmpty() ? null : watchers;
        });
    }
}
