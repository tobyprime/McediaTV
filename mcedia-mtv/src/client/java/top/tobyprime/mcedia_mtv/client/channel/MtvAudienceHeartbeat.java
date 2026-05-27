package top.tobyprime.mcedia_mtv.client.channel;

public record MtvAudienceHeartbeat(
        String channelId,
        long revision,
        boolean loaded,
        boolean completed,
        long durationUs,
        boolean error
) {
}
