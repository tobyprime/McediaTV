package top.tobyprime.mcedia_mtv.client.channel.worldui;

public record WorldUiPlaylistManifest(
        String channelId,
        long revision,
        int itemCount,
        int cursor,
        String playOrderMode
) {
}
