package top.tobyprime.mcedia_mtv_plugin.channel;

public record MtvAudienceHeartbeat(
        String channelId,
        long revision,
        boolean loaded,
        boolean completed,
        long durationUs,
        boolean error,
        boolean suspended
) {
}
