package top.tobyprime.mcedia_mtv_plugin.channel;

public record ChannelSnapshot(
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
        boolean audienceSuspended
) {
}
