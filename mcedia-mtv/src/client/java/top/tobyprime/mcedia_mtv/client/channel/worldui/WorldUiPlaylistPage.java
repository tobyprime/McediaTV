package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.List;

public record WorldUiPlaylistPage(
        String channelId,
        long revision,
        int itemCount,
        int cursor,
        String playOrderMode,
        int offset,
        List<String> mediaUrls
) {
    public WorldUiPlaylistPage {
        mediaUrls = List.copyOf(mediaUrls);
    }
}
