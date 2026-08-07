package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

public record WorldUiPlaylistManifest(
        String channelId,
        long revision,
        int itemCount,
        int cursor,
        String playOrderMode
) {
}
