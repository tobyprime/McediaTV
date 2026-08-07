package top.tobyprime.mcedia_mtv.client.channel.worldui;

public record WorldUiPlaylistPageRequest(
        String channelId,
        long knownRevision,
        int offset
) {
}
