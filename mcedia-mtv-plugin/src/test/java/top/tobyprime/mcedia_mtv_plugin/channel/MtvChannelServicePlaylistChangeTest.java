package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MtvChannelServicePlaylistChangeTest {
    @Test
    void playlistChangeIncludesItemsCursorAndPlayOrder() {
        var state = new ChannelRuntimeState("channel", MtvChannelType.SELF);
        state.getPlaylist().add(new ChannelPlaylistItem("a"));
        var originalItems = List.copyOf(state.getPlaylist());

        assertFalse(MtvChannelService.playlistMetadataChanged(state, originalItems, 0, ChannelPlayOrderMode.SEQUENTIAL));
        assertTrue(MtvChannelService.playlistMetadataChanged(state, List.of(new ChannelPlaylistItem("a"), new ChannelPlaylistItem("b")), 0, ChannelPlayOrderMode.SEQUENTIAL));
        assertTrue(MtvChannelService.playlistMetadataChanged(state, originalItems, 1, ChannelPlayOrderMode.SEQUENTIAL));
        assertTrue(MtvChannelService.playlistMetadataChanged(state, originalItems, 0, ChannelPlayOrderMode.SHUFFLE));
    }
}
