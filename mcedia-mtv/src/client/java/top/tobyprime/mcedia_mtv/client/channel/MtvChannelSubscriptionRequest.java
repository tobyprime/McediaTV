package top.tobyprime.mcedia_mtv.client.channel;

import java.util.UUID;

public record MtvChannelSubscriptionRequest(
        String channelId,
        UUID sessionId
) {
}
