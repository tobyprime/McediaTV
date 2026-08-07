package top.tobyprime.mcedia_mtv.client.channel.worldui;

public record WorldUiCapabilities(
        int protocolVersion,
        int maxPageItems,
        long featureFlags
) {
}
