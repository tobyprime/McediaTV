package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.UUID;

public final class AudienceSession {
    private final UUID sessionId;
    private final UUID playerUuid;
    private final String channelId;
    private long lastHeartbeatAtMs;
    private long lastRevision;
    private AudienceObservedState observedState = AudienceObservedState.LOADING;
    private String loadedMediaId = "";
    private long durationMs;

    public AudienceSession(UUID sessionId, UUID playerUuid, String channelId, long nowMs) {
        this.sessionId = sessionId;
        this.playerUuid = playerUuid;
        this.channelId = channelId;
        this.lastHeartbeatAtMs = nowMs;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getChannelId() {
        return channelId;
    }

    public long getLastHeartbeatAtMs() {
        return lastHeartbeatAtMs;
    }

    public void setLastHeartbeatAtMs(long lastHeartbeatAtMs) {
        this.lastHeartbeatAtMs = lastHeartbeatAtMs;
    }

    public long getLastRevision() {
        return lastRevision;
    }

    public void setLastRevision(long lastRevision) {
        this.lastRevision = lastRevision;
    }

    public AudienceObservedState getObservedState() {
        return observedState;
    }

    public void setObservedState(AudienceObservedState observedState) {
        this.observedState = observedState == null ? AudienceObservedState.LOADING : observedState;
    }

    public String getLoadedMediaId() {
        return loadedMediaId;
    }

    public void setLoadedMediaId(String loadedMediaId) {
        this.loadedMediaId = loadedMediaId == null ? "" : loadedMediaId;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }
}
