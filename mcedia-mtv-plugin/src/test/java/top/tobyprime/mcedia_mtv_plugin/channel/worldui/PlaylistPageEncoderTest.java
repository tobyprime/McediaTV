package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaylistItem;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistPageEncoderTest {
    @Test
    void encoderNeverProducesMoreThanThirtyTwoEntriesOrTwentyFourKiB() {
        var state = stateWithUrls(80, 1_000);

        var page = PlaylistPageEncoder.encodePage(state, 0);

        assertTrue(page.mediaUrls().size() > 0);
        assertTrue(page.mediaUrls().size() <= 32);
        assertTrue(MtvChannelProtocol.encodePlaylistPage(page).length <= MtvChannelProtocol.MAX_PLAYLIST_PAGE_BYTES);
    }

    @Test
    void encoderSupportsTheLastPartialPage() {
        var state = stateWithUrls(65, 20);

        var page = PlaylistPageEncoder.encodePage(state, 64);

        assertEquals(1, page.mediaUrls().size());
        assertEquals("https://example.test/64", page.mediaUrls().get(0));
    }

    @Test
    void encoderRejectsUnalignedOffsetsAndOverlongUrls() {
        var state = stateWithUrls(2, 20);
        assertThrows(IllegalArgumentException.class, () -> PlaylistPageEncoder.encodePage(state, 1));

        state.getPlaylist().set(0, new ChannelPlaylistItem("https://example.test/" + "x".repeat(2_048)));
        assertThrows(IllegalArgumentException.class, () -> PlaylistPageEncoder.encodePage(state, 0));
    }

    private static ChannelRuntimeState stateWithUrls(int count, int suffixLength) {
        var state = new ChannelRuntimeState("channel", MtvChannelType.SELF);
        for (int index = 0; index < count; index++) {
            String suffix = suffixLength == 20 ? Integer.toString(index) : "x".repeat(suffixLength);
            state.getPlaylist().add(new ChannelPlaylistItem("https://example.test/" + suffix));
        }
        state.setPlaylistCursor(0);
        return state;
    }
}
