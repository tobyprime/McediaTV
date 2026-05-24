package top.tobyprime.mcedia_mtv_plugin.channel;

public record ChannelSnapshot(
        String channelId,
        long revision,
        String mediaUrl,
        float speed,
        long startAt,
        long baseTime,
        long baseOffset,
        boolean paused,
        long resolvedDurationUs,
        boolean completed
) {
}
