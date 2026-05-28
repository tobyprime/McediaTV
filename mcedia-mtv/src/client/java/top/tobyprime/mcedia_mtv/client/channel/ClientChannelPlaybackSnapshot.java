package top.tobyprime.mcedia_mtv.client.channel;

public record ClientChannelPlaybackSnapshot(
        String channelId,
        long revision,
        String mediaUrl,
        float speed,
        long anchorMediaTimeUs,
        long elapsedTimeMs,
        String state,
        boolean paused,
        long resolvedDurationUs,
        boolean completed,
        long receivedAtMonotonicMs
) {
    public static final ClientChannelPlaybackSnapshot EMPTY = new ClientChannelPlaybackSnapshot("", 0L, "", 1.0F, 0L, 0L, "STOPPED", false, 0L, false, 0L);

    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isBlank();
    }

    public ClientChannelPlaybackSnapshot receivedNow(long receivedAtMonotonicMs) {
        return new ClientChannelPlaybackSnapshot(
                channelId,
                revision,
                mediaUrl,
                speed,
                anchorMediaTimeUs,
                elapsedTimeMs,
                state,
                paused,
                resolvedDurationUs,
                completed,
                receivedAtMonotonicMs
        );
    }
}
