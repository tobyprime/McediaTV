package top.tobyprime.mcedia_mtv.client.channel;

import java.util.UUID;

public record MtvMediaInfoReport(
        String channelId,
        UUID sessionId,
        long revision,
        String loadedMediaId,
        long durationUs
) {
}
