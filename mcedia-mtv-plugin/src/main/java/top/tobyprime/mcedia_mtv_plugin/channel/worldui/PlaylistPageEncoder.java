package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;

import java.util.ArrayList;
import java.util.List;

public final class PlaylistPageEncoder {
    private static final int PAGE_SIZE = MtvChannelProtocol.MAX_PLAYLIST_PAGE_ITEMS;

    private PlaylistPageEncoder() {
    }

    public static WorldUiPlaylistPage encodePage(ChannelRuntimeState state, int offset) {
        if (state == null || offset < 0 || offset % PAGE_SIZE != 0 || offset > state.getPlaylist().size()) {
            throw new IllegalArgumentException("playlist page offset is invalid");
        }

        int itemCount = state.getPlaylist().size();
        var mediaUrls = new ArrayList<String>();
        int end = Math.min(itemCount, offset + PAGE_SIZE);
        for (int index = offset; index < end; index++) {
            String mediaUrl = state.getPlaylist().get(index).mediaUrl();
            if (mediaUrl == null || mediaUrl.isBlank() || mediaUrl.length() > MtvChannelProtocol.MAX_MEDIA_URL_LENGTH) {
                throw new IllegalArgumentException("playlist contains an invalid media URL at index " + index);
            }
            mediaUrls.add(mediaUrl);
            var candidate = page(state, offset, itemCount, mediaUrls);
            try {
                MtvChannelProtocol.encodePlaylistPage(candidate);
            } catch (IllegalArgumentException e) {
                if (!e.getMessage().contains("encoded length exceeds")) {
                    throw e;
                }
                mediaUrls.remove(mediaUrls.size() - 1);
                if (mediaUrls.isEmpty()) {
                    throw new IllegalArgumentException("one playlist URL cannot fit in a page", e);
                }
                break;
            }
        }
        return page(state, offset, itemCount, mediaUrls);
    }

    private static WorldUiPlaylistPage page(ChannelRuntimeState state, int offset, int itemCount, List<String> mediaUrls) {
        return new WorldUiPlaylistPage(
                state.getChannelId(),
                state.getRevision(),
                itemCount,
                state.getNormalizedPlaylistCursor(),
                state.getPlayOrderMode().name(),
                offset,
                mediaUrls
        );
    }
}
