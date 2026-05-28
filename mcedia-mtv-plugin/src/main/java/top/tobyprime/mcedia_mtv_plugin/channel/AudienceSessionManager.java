package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AudienceSessionManager {
    private volatile long activeTimeoutMs = 30_000L;

    private final Map<String, Map<UUID, AudienceSession>> sessionsByChannel = new ConcurrentHashMap<>();

    public record AudienceTouch(boolean stateChanged, boolean durationChanged) {
    }

    public record AudienceSummary(long resolvedDurationMs, boolean completed, boolean majorityLoaded) {
    }

    public void setActiveTimeoutMs(long activeTimeoutMs) {
        this.activeTimeoutMs = Math.max(0L, activeTimeoutMs);
    }

    public void subscribe(UUID playerUuid, String channelId, long nowMs) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var sessions = sessionsByChannel.computeIfAbsent(channelId, key -> new ConcurrentHashMap<>());
        var session = sessions.computeIfAbsent(playerUuid, key -> new AudienceSession(playerUuid, channelId, nowMs));
        session.setLastHeartbeatAtMs(nowMs);
    }

    public void unsubscribe(UUID playerUuid, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var sessions = sessionsByChannel.get(channelId);
        if (sessions == null) {
            return;
        }
        sessions.remove(playerUuid);
        if (sessions.isEmpty()) {
            sessionsByChannel.remove(channelId, sessions);
        }
    }

    public boolean isSubscribed(UUID playerUuid, String channelId) {
        var sessions = sessionsByChannel.get(channelId);
        return sessions != null && sessions.containsKey(playerUuid);
    }

    public boolean isActive(AudienceSession session, long nowMs) {
        if (session == null) {
            return false;
        }
        return nowMs - session.getLastHeartbeatAtMs() <= activeTimeoutMs && isSubscribed(session.getPlayerUuid(), session.getChannelId());
    }

    public AudienceTouch touch(UUID playerUuid, String channelId, long revision, boolean loaded, boolean completed, boolean error, long durationMs, long nowMs) {
        var sessions = sessionsByChannel.computeIfAbsent(channelId, key -> new ConcurrentHashMap<>());
        var session = sessions.computeIfAbsent(playerUuid, key -> new AudienceSession(playerUuid, channelId, nowMs));
        long nextDurationMs = Math.max(0L, durationMs);
        boolean stateChanged = session.getLastRevision() != revision
                || session.isLoaded() != loaded
                || session.isCompleted() != completed
                || session.isError() != error;
        boolean durationChanged = session.getDurationMs() != nextDurationMs;
        session.setLastHeartbeatAtMs(nowMs);
        session.setLastRevision(revision);
        session.setLoaded(loaded);
        session.setCompleted(completed);
        session.setError(error);
        session.setDurationMs(nextDurationMs);
        return new AudienceTouch(stateChanged || durationChanged, durationChanged);
    }

    public List<AudienceSession> getSessions(String channelId) {
        var sessions = sessionsByChannel.get(channelId);
        return sessions == null ? List.of() : new ArrayList<>(sessions.values());
    }

    public List<AudienceSession> getActiveSessions(String channelId, long nowMs) {
        var sessions = getSessions(channelId);
        if (sessions.isEmpty()) {
            return List.of();
        }
        var activeSessions = new ArrayList<AudienceSession>();
        for (var session : sessions) {
            if (isActive(session, nowMs)) {
                activeSessions.add(session);
            }
        }
        return activeSessions;
    }

    public AudienceSummary summarize(String channelId, long revision, long nowMs) {
        var sessions = getActiveSessions(channelId, nowMs);
        if (sessions.isEmpty()) {
            return new AudienceSummary(0L, false, false);
        }
        long resolvedDurationMs = 0L;
        int matched = 0;
        int loaded = 0;
        int completed = 0;
        for (var session : sessions) {
            if (session.isError()) {
                continue;
            }
            if (session.getLastRevision() != revision) {
                continue;
            }
            matched++;
            resolvedDurationMs = Math.max(resolvedDurationMs, session.getDurationMs());
            if (session.isLoaded()) {
                loaded++;
            }
            if (session.isCompleted()) {
                completed++;
            }
        }
        return new AudienceSummary(
                resolvedDurationMs,
                matched > 0 && completed * 2 > matched,
                matched > 0 && loaded * 2 > matched
        );
    }

    public int countAudience(String channelId) {
        return getActiveSessions(channelId, System.currentTimeMillis()).size();
    }

    public void unregisterClient(UUID playerUuid) {
        for (var sessions : sessionsByChannel.values()) {
            sessions.remove(playerUuid);
        }
        sessionsByChannel.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public List<String> pruneExpired(long nowMs) {
        var emptiedChannels = new ArrayList<String>();
        for (var entry : sessionsByChannel.entrySet()) {
            var sessions = entry.getValue();
            sessions.entrySet().removeIf(sessionEntry -> nowMs - sessionEntry.getValue().getLastHeartbeatAtMs() > activeTimeoutMs);
            if (sessions.isEmpty() && sessionsByChannel.remove(entry.getKey(), sessions)) {
                emptiedChannels.add(entry.getKey());
            }
        }
        return emptiedChannels;
    }

    public void invalidateChannel(String channelId) {
        sessionsByChannel.remove(channelId);
    }

    public void clear() {
        sessionsByChannel.clear();
    }
}
