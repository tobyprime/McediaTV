package top.tobyprime.mcedia_mtv.client.channel;

public record ClientChannelPlaybackSnapshot(
        String channelId,
        long revision,
        String mediaUrl,
        float speed,
        long startAt,
        long baseTime,
        long baseOffset,
        String state,
        boolean paused,
        long resolvedDurationUs,
        boolean completed
) {
    public static final ClientChannelPlaybackSnapshot EMPTY = new ClientChannelPlaybackSnapshot("", 0L, "", 1.0F, 0L, 0L, 0L, "STOPPED", false, 0L, false);

    public boolean hasMedia() {
        return mediaUrl != null && !mediaUrl.isBlank();
    }
}
