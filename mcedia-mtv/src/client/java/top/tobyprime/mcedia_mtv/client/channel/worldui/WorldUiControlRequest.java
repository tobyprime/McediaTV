package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.UUID;

public record WorldUiControlRequest(
        UUID targetMtvUuid,
        String screenId,
        String channelId,
        long requestId,
        long expectedRevision,
        float hitU,
        float hitV,
        WorldUiControlOperation operation,
        WorldUiControlArgument argument
) {
}
