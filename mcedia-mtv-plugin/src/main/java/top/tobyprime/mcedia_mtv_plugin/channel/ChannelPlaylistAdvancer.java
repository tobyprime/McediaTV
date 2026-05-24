package top.tobyprime.mcedia_mtv_plugin.channel;

public final class ChannelPlaylistAdvancer {
    private ChannelPlaylistAdvancer() {
    }

    public static boolean advanceOrPause(ChannelRuntimeState runtimeState, long nowMs) {
        if (runtimeState == null) {
            return false;
        }
        if (runtimeState.getPlaylistCursor() + 1 < runtimeState.getPlaylist().size()) {
            int nextIndex = runtimeState.getPlaylistCursor() + 1;
            var next = runtimeState.getPlaylist().get(nextIndex);
            runtimeState.setPlaylistCursor(nextIndex);
            runtimeState.setDurationMs(0L);
            ChannelTimelineCalculator.setMedia(runtimeState.getPlayState(), next.mediaUrl(), 0L, nowMs, ChannelPlaybackStatus.LOADING);
            return true;
        }
        runtimeState.getPlayState().setMediaTimeMs(0L);
        runtimeState.getPlayState().setPlayTimeMs(nowMs);
        runtimeState.getPlayState().setState(ChannelPlaybackStatus.PAUSED);
        return true;
    }
}
