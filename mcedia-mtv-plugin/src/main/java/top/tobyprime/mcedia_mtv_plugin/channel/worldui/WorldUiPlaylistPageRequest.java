package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

public record WorldUiPlaylistPageRequest(
        String channelId,
        long knownRevision,
        int offset
) {
}
