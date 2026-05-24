package top.tobyprime.mcedia_mtv.client.channel;

import java.util.UUID;

public record MtvAudienceHeartbeat(
        String channelId,
        UUID sessionId,
        long revision,
        String state,
        long clientMediaTimeUs,
        float clientPlaybackRate,
        String loadedMediaId,
        long durationUs
) {
}
