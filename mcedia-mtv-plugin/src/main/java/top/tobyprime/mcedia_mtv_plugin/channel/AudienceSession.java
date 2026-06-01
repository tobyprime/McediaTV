package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.UUID;

public final class AudienceSession {
    private final UUID playerUuid;
    private final String channelId;
    private long lastHeartbeatAtMs;
    private long lastRevision;
    private boolean loaded;
    private boolean completed;
    private boolean error;
    private boolean suspended;
    private long durationMs;

    public AudienceSession(UUID playerUuid, String channelId, long nowMs) {
        this.playerUuid = playerUuid;
        this.channelId = channelId;
        this.lastHeartbeatAtMs = nowMs;
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

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }
}
