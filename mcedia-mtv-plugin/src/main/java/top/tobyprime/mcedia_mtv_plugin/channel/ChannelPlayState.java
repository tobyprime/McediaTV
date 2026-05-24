package top.tobyprime.mcedia_mtv_plugin.channel;

public final class ChannelPlayState {
    private String mediaUrl = "";
    private ChannelPlaybackStatus state = ChannelPlaybackStatus.STOPPED;
    private double speed = 1.0D;
    private long mediaTimeMs = 0L;
    private long playTimeMs = System.currentTimeMillis();

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl == null ? "" : mediaUrl;
    }

    public ChannelPlaybackStatus getState() {
        return state;
    }

    public void setState(ChannelPlaybackStatus state) {
        this.state = state == null ? ChannelPlaybackStatus.STOPPED : state;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public long getMediaTimeMs() {
        return mediaTimeMs;
    }

    public void setMediaTimeMs(long mediaTimeMs) {
        this.mediaTimeMs = Math.max(0L, mediaTimeMs);
    }

    public long getPlayTimeMs() {
        return playTimeMs;
    }

    public void setPlayTimeMs(long playTimeMs) {
        this.playTimeMs = Math.max(0L, playTimeMs);
    }

    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isBlank();
    }
}
