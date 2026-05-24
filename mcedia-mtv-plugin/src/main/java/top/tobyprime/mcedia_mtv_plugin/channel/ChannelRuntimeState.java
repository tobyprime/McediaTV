package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.ArrayList;
import java.util.List;

public final class ChannelRuntimeState {
    private final String channelId;
    private final MtvChannelType channelType;
    private final ChannelPlayState playState = new ChannelPlayState();
    private final List<ChannelPlaylistItem> playlist = new ArrayList<>();
    private long revision;
    private long durationMs;
    private int playlistCursor;
    private ChannelPlayOrderMode playOrderMode = ChannelPlayOrderMode.SEQUENTIAL;
    private long updatedAtMs = System.currentTimeMillis();

    public ChannelRuntimeState(String channelId, MtvChannelType channelType) {
        this.channelId = channelId;
        this.channelType = channelType;
    }

    public String getChannelId() {
        return channelId;
    }

    public MtvChannelType getChannelType() {
        return channelType;
    }

    public ChannelPlayState getPlayState() {
        return playState;
    }

    public List<ChannelPlaylistItem> getPlaylist() {
        return playlist;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = Math.max(0L, revision);
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }

    public int getPlaylistCursor() {
        return playlistCursor;
    }

    public void setPlaylistCursor(int playlistCursor) {
        this.playlistCursor = Math.max(0, playlistCursor);
    }

    public ChannelPlayOrderMode getPlayOrderMode() {
        return playOrderMode;
    }

    public void setPlayOrderMode(ChannelPlayOrderMode playOrderMode) {
        this.playOrderMode = playOrderMode == null ? ChannelPlayOrderMode.SEQUENTIAL : playOrderMode;
    }

    public int getNormalizedPlaylistCursor() {
        if (playlist.isEmpty()) {
            return 0;
        }
        if (playlistCursor < 0) {
            return 0;
        }
        if (playlistCursor >= playlist.size()) {
            return playlist.size() - 1;
        }
        return playlistCursor;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void touch() {
        revision++;
        updatedAtMs = System.currentTimeMillis();
    }

    public long computeCurrentMediaTimeMs(long nowMs) {
        return ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, nowMs);
    }

    public ChannelSnapshot toSnapshot(long resolvedDurationUs, boolean completed) {
        long mediaTimeUs = Math.max(0L, playState.getMediaTimeMs() * 1000L);
        long playTimeMs = Math.max(0L, playState.getPlayTimeMs());
        return new ChannelSnapshot(
                channelId,
                revision,
                playState.getMediaUrl(),
                (float) playState.getSpeed(),
                mediaTimeUs,
                playTimeMs,
                mediaTimeUs,
                playState.getState().name(),
                playState.getState() != ChannelPlaybackStatus.PLAYING,
                resolvedDurationUs,
                completed
        );
    }

    public boolean isPaused() {
        return playState.getState() != ChannelPlaybackStatus.PLAYING;
    }
}
