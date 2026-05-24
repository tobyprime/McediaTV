package top.tobyprime.mcedia_mtv_plugin.channel;

public enum ChannelPlaybackStatus {
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED;

    public boolean isPlaying() {
        return this == PLAYING;
    }

    public boolean isPausedLike() {
        return this == PAUSED || this == STOPPED || this == LOADING;
    }
}
