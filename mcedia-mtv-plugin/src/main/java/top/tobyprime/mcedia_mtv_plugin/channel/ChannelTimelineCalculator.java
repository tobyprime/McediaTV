package top.tobyprime.mcedia_mtv_plugin.channel;

public final class ChannelTimelineCalculator {
    private ChannelTimelineCalculator() {
    }

    public static long computeCurrentMediaTimeMs(ChannelPlayState playState, long nowMs) {
        if (playState == null) {
            return 0L;
        }
        if (playState.getState() != ChannelPlaybackStatus.PLAYING) {
            return Math.max(0L, playState.getMediaTimeMs());
        }
        long elapsedMs = Math.max(0L, nowMs - playState.getPlayTimeMs());
        return Math.max(0L, playState.getMediaTimeMs() + (long) (elapsedMs * playState.getSpeed()));
    }

    public static void play(ChannelPlayState playState, long nowMs) {
        playState.setPlayTimeMs(nowMs);
        playState.setState(ChannelPlaybackStatus.PLAYING);
    }

    public static void pause(ChannelPlayState playState, long nowMs) {
        playState.setMediaTimeMs(computeCurrentMediaTimeMs(playState, nowMs));
        playState.setPlayTimeMs(nowMs);
        playState.setState(ChannelPlaybackStatus.PAUSED);
    }

    public static void stop(ChannelPlayState playState, long nowMs) {
        playState.setMediaTimeMs(0L);
        playState.setPlayTimeMs(nowMs);
        playState.setState(ChannelPlaybackStatus.STOPPED);
    }

    public static void seek(ChannelPlayState playState, long targetMediaTimeMs, long nowMs) {
        playState.setMediaTimeMs(Math.max(0L, targetMediaTimeMs));
        playState.setPlayTimeMs(nowMs);
    }

    public static void setSpeed(ChannelPlayState playState, double speed, long nowMs) {
        playState.setMediaTimeMs(computeCurrentMediaTimeMs(playState, nowMs));
        playState.setSpeed(speed);
        playState.setPlayTimeMs(nowMs);
    }

    public static void setMedia(ChannelPlayState playState, String mediaUrl, long mediaTimeMs, long nowMs, ChannelPlaybackStatus state) {
        playState.setMediaUrl(mediaUrl);
        playState.setMediaTimeMs(Math.max(0L, mediaTimeMs));
        playState.setPlayTimeMs(nowMs);
        playState.setState(state);
    }
}
