package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPlayStateTest {

    @Test
    void defaultValues() {
        var ps = new ChannelPlayState();
        assertEquals("", ps.getMediaUrl());
        assertEquals(ChannelPlaybackStatus.STOPPED, ps.getState());
        assertEquals(1.0, ps.getSpeed(), 1e-9);
        assertEquals(0L, ps.getMediaTimeMs());
        assertFalse(ps.hasMedia());
    }

    @Test
    void setNullMediaUrlDefaultsToEmpty() {
        var ps = new ChannelPlayState();
        ps.setMediaUrl(null);
        assertEquals("", ps.getMediaUrl());
    }

    @Test
    void setNullStateDefaultsToStopped() {
        var ps = new ChannelPlayState();
        ps.setState(null);
        assertEquals(ChannelPlaybackStatus.STOPPED, ps.getState());
    }

    @Test
    void setMediaTimeMsClampsToZero() {
        var ps = new ChannelPlayState();
        ps.setMediaTimeMs(-100);
        assertEquals(0L, ps.getMediaTimeMs());
    }

    @Test
    void setPlayTimeMsClampsToZero() {
        var ps = new ChannelPlayState();
        ps.setPlayTimeMs(-1);
        assertEquals(0L, ps.getPlayTimeMs());
    }

    @Test
    void hasMediaReturnsTrueWhenMediaUrlIsNotEmpty() {
        var ps = new ChannelPlayState();
        ps.setMediaUrl("http://example.com/v.mp4");
        assertTrue(ps.hasMedia());
    }
}
