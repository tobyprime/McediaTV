package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AudienceSessionManager {
    private final Map<UUID, UUID> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, AudienceSession>> sessionsByChannel = new ConcurrentHashMap<>();

    public void registerClient(UUID playerUuid, UUID sessionId) {
        playerSessions.put(playerUuid, sessionId);
    }

    public UUID getSessionId(UUID playerUuid) {
        return playerSessions.get(playerUuid);
    }

    public void subscribe(UUID playerUuid, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            playerSubscriptions.remove(playerUuid);
            return;
        }
        playerSubscriptions.computeIfAbsent(playerUuid, key -> ConcurrentHashMap.newKeySet()).add(channelId);
    }

    public boolean isSubscribed(UUID playerUuid, String channelId) {
        var subscriptions = playerSubscriptions.get(playerUuid);
        return subscriptions != null && subscriptions.contains(channelId);
    }

    public AudienceSession touch(UUID playerUuid, UUID sessionId, String channelId, long revision, AudienceObservedState observedState, String loadedMediaId, long durationMs, long nowMs) {
        playerSessions.put(playerUuid, sessionId);
        var sessions = sessionsByChannel.computeIfAbsent(channelId, key -> new ConcurrentHashMap<>());
        var session = sessions.computeIfAbsent(sessionId, key -> new AudienceSession(sessionId, playerUuid, channelId, nowMs));
        session.setLastHeartbeatAtMs(nowMs);
        session.setLastRevision(revision);
        session.setObservedState(observedState);
        session.setLoadedMediaId(loadedMediaId);
        session.setDurationMs(durationMs);
        return session;
    }

    public List<AudienceSession> getSessions(String channelId) {
        var sessions = sessionsByChannel.get(channelId);
        return sessions == null ? List.of() : new ArrayList<>(sessions.values());
    }

    public long resolveDurationMs(String channelId, long revision) {
        long resolved = 0L;
        for (var session : getSessions(channelId)) {
            if (session.getLastRevision() != revision) {
                continue;
            }
            resolved = Math.max(resolved, session.getDurationMs());
        }
        return resolved;
    }

    public boolean isCompleted(String channelId, long revision) {
        var sessions = getSessions(channelId);
        if (sessions.isEmpty()) {
            return false;
        }
        int matched = 0;
        int completed = 0;
        for (var session : sessions) {
            if (session.getLastRevision() != revision) {
                continue;
            }
            matched++;
            if (session.getObservedState() == AudienceObservedState.ENDED) {
                completed++;
            }
        }
        return matched > 0 && completed * 2 > matched;
    }

    public void unregisterClient(UUID playerUuid) {
        var sessionId = playerSessions.remove(playerUuid);
        playerSubscriptions.remove(playerUuid);
        if (sessionId == null) {
            return;
        }
        for (var sessions : sessionsByChannel.values()) {
            sessions.remove(sessionId);
        }
        sessionsByChannel.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public List<String> pruneExpired(long nowMs, long timeoutMs) {
        var expiredSessionIds = ConcurrentHashMap.<UUID>newKeySet();
        var emptiedChannels = new ArrayList<String>();
        for (var entry : sessionsByChannel.entrySet()) {
            var sessions = entry.getValue();
            sessions.entrySet().removeIf(sessionEntry -> {
                boolean expired = nowMs - sessionEntry.getValue().getLastHeartbeatAtMs() > timeoutMs;
                if (expired) {
                    expiredSessionIds.add(sessionEntry.getKey());
                }
                return expired;
            });
            if (sessions.isEmpty() && sessionsByChannel.remove(entry.getKey(), sessions)) {
                emptiedChannels.add(entry.getKey());
            }
        }
        if (!expiredSessionIds.isEmpty()) {
            var expiredPlayers = ConcurrentHashMap.<UUID>newKeySet();
            playerSessions.entrySet().removeIf(entry -> {
                boolean expired = expiredSessionIds.contains(entry.getValue());
                if (expired) {
                    expiredPlayers.add(entry.getKey());
                }
                return expired;
            });
            if (!expiredPlayers.isEmpty()) {
                playerSubscriptions.keySet().removeIf(expiredPlayers::contains);
            }
        }
        return emptiedChannels;
    }

    public void invalidateChannel(String channelId) {
        sessionsByChannel.remove(channelId);
    }
}
