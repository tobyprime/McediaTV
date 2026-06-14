package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPlaybackStatusTest {

    @Test
    void isPlayingReturnsTrueOnlyForPlaying() {
        assertTrue(ChannelPlaybackStatus.PLAYING.isPlaying());
        assertFalse(ChannelPlaybackStatus.LOADING.isPlaying());
        assertFalse(ChannelPlaybackStatus.PAUSED.isPlaying());
        assertFalse(ChannelPlaybackStatus.STOPPED.isPlaying());
    }

    @Test
    void isPausedLikeReturnsTrueForNonPlaying() {
        assertTrue(ChannelPlaybackStatus.LOADING.isPausedLike());
        assertTrue(ChannelPlaybackStatus.PAUSED.isPausedLike());
        assertTrue(ChannelPlaybackStatus.STOPPED.isPausedLike());
        assertFalse(ChannelPlaybackStatus.PLAYING.isPausedLike());
    }
}
