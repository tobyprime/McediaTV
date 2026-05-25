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
    private String creatorName = "";
    private String creatorUuid = "";
    private String channelName = "";
    private String description = "";
    private boolean discoverable;
    private long createdAtMs = System.currentTimeMillis();
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

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName == null ? "" : creatorName;
    }

    public String getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(String creatorUuid) {
        this.creatorUuid = creatorUuid == null ? "" : creatorUuid;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName == null ? "" : channelName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }

    public void setDiscoverable(boolean discoverable) {
        this.discoverable = discoverable;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public void setCreatedAtMs(long createdAtMs) {
        this.createdAtMs = Math.max(0L, createdAtMs);
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

    public boolean isPublicChannel() {
        return channelType == MtvChannelType.BROADCAST && discoverable;
    }
}
