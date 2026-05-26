package top.tobyprime.mcedia_mtv_plugin.channel;

public final class ChannelPlaylistAdvancer {
    private ChannelPlaylistAdvancer() {
    }

    public static boolean advanceOrPause(ChannelRuntimeState runtimeState, long nowMs) {
        if (runtimeState == null) {
            return false;
        }
        var playlist = runtimeState.getPlaylist();
        int size = playlist.size();
        if (size == 0) {
            ChannelTimelineCalculator.stop(runtimeState.getPlayState(), nowMs);
            return true;
        }

        int currentIndex = runtimeState.getNormalizedPlaylistCursor();
        int nextIndex = switch (runtimeState.getPlayOrderMode()) {
            case SHUFFLE -> (int) (Math.random() * size);
            case LOOP_ALL -> (currentIndex + 1) % size;
            case LOOP_ONE -> currentIndex;
            case CURRENT_ONLY -> -1;
            case SEQUENTIAL -> currentIndex + 1 < size ? currentIndex + 1 : -1;
        };

        if (nextIndex < 0) {
            runtimeState.getPlayState().setMediaTimeMs(0L);
            runtimeState.getPlayState().setPlayTimeMs(nowMs);
            runtimeState.getPlayState().setState(ChannelPlaybackStatus.PAUSED);
            return true;
        }

        var next = playlist.get(nextIndex);
        runtimeState.setPlaylistCursor(nextIndex);
        runtimeState.setDurationMs(0L);
        ChannelTimelineCalculator.setMedia(runtimeState.getPlayState(), next.mediaUrl(), 0L, nowMs, ChannelPlaybackStatus.LOADING);
        return true;
    }
}
