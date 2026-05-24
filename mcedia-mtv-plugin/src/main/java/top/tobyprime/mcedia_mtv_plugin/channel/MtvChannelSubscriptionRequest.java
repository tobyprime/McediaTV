package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.UUID;

public record MtvChannelSubscriptionRequest(
        String channelId,
        UUID sessionId
) {
}
