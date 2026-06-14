package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelTimelineCalculatorTest {

    @Test
    void computeCurrentMediaTimeReturnsMediaTimeWhenNotPlaying() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(50_000L);
        playState.setState(ChannelPlaybackStatus.PAUSED);

        var result = ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, 100_000L);
        assertEquals(50_000L, result);
    }

    @Test
    void computeCurrentMediaTimeReturnsMediaTimeWhenStopped() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(30_000L);
        playState.setState(ChannelPlaybackStatus.STOPPED);

        var result = ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, 100_000L);
        assertEquals(30_000L, result);
    }

    @Test
    void computeCurrentMediaTimeReturnsZeroWhenNullPlayState() {
        assertEquals(0L, ChannelTimelineCalculator.computeCurrentMediaTimeMs(null, 100_000L));
    }

    @Test
    void computeCurrentMediaTimeAdvancesWhenPlaying() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(10_000L);
        playState.setPlayTimeMs(0L);
        playState.setState(ChannelPlaybackStatus.PLAYING);
        playState.setSpeed(1.0);

        // nowMs = 5000 => elapsed = 5000, so mediaTime = 10000 + 5000 = 15000
        var result = ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, 5_000L);
        assertEquals(15_000L, result);
    }

    @Test
    void computeCurrentMediaTimeRespectsSpeed() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(0L);
        playState.setPlayTimeMs(0L);
        playState.setState(ChannelPlaybackStatus.PLAYING);
        playState.setSpeed(2.0);

        // elapsed = 10000, scaled = 20000
        var result = ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, 10_000L);
        assertEquals(20_000L, result);
    }

    @Test
    void computeCurrentMediaTimeDoesNotGoBelowZero() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(1_000L);
        playState.setPlayTimeMs(10_000L); // playTime in the future
        playState.setState(ChannelPlaybackStatus.PLAYING);

        // nowMs = 5000 => elapsed = 0 (clamped), mediaTime = 1000
        var result = ChannelTimelineCalculator.computeCurrentMediaTimeMs(playState, 5_000L);
        assertEquals(1_000L, result);
    }

    @Test
    void playSetsStateAndPlayTime() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(10_000L);

        ChannelTimelineCalculator.play(playState, 50_000L);

        assertEquals(ChannelPlaybackStatus.PLAYING, playState.getState());
        assertEquals(50_000L, playState.getPlayTimeMs());
        // mediaTimeMs should be unchanged — we keep the existing position
        assertEquals(10_000L, playState.getMediaTimeMs());
    }

    @Test
    void pauseCapturesCurrentTime() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(10_000L);
        playState.setPlayTimeMs(0L);
        playState.setState(ChannelPlaybackStatus.PLAYING);
        playState.setSpeed(1.0);

        ChannelTimelineCalculator.pause(playState, 5_000L);

        assertEquals(ChannelPlaybackStatus.PAUSED, playState.getState());
        // mediaTimeMs should be frozen at the computed value: 10000 + (5000 - 0) * 1.0 = 15000
        assertEquals(15_000L, playState.getMediaTimeMs());
        assertEquals(5_000L, playState.getPlayTimeMs());
    }

    @Test
    void stopResetsMediaTime() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(50_000L);

        ChannelTimelineCalculator.stop(playState, 100_000L);

        assertEquals(ChannelPlaybackStatus.STOPPED, playState.getState());
        assertEquals(0L, playState.getMediaTimeMs());
        assertEquals(100_000L, playState.getPlayTimeMs());
    }

    @Test
    void seekSetsTargetMediaTime() {
        var playState = new ChannelPlayState();

        ChannelTimelineCalculator.seek(playState, 75_000L, 200_000L);

        assertEquals(75_000L, playState.getMediaTimeMs());
        assertEquals(200_000L, playState.getPlayTimeMs());
    }

    @Test
    void seekClampsToZero() {
        var playState = new ChannelPlayState();

        ChannelTimelineCalculator.seek(playState, -100L, 200_000L);

        assertEquals(0L, playState.getMediaTimeMs());
    }

    @Test
    void setSpeedCapturesCurrentTimeAndUpdatesSpeed() {
        var playState = new ChannelPlayState();
        playState.setMediaTimeMs(10_000L);
        playState.setPlayTimeMs(0L);
        playState.setState(ChannelPlaybackStatus.PLAYING);
        playState.setSpeed(1.0);

        ChannelTimelineCalculator.setSpeed(playState, 2.0, 5_000L);

        // mediaTimeMs frozen at computed value: 10000 + 5000 = 15000
        assertEquals(15_000L, playState.getMediaTimeMs());
        assertEquals(2.0, playState.getSpeed(), 1e-9);
        assertEquals(5_000L, playState.getPlayTimeMs());
    }

    @Test
    void setMediaSetsAllFields() {
        var playState = new ChannelPlayState();

        ChannelTimelineCalculator.setMedia(playState, "http://example.com/new", 30_000L, 100_000L, ChannelPlaybackStatus.LOADING);

        assertEquals("http://example.com/new", playState.getMediaUrl());
        assertEquals(30_000L, playState.getMediaTimeMs());
        assertEquals(100_000L, playState.getPlayTimeMs());
        assertEquals(ChannelPlaybackStatus.LOADING, playState.getState());
    }

    @Test
    void setMediaClampsNegativeTimeToZero() {
        var playState = new ChannelPlayState();

        ChannelTimelineCalculator.setMedia(playState, "url", -1L, 100_000L, ChannelPlaybackStatus.PLAYING);

        assertEquals(0L, playState.getMediaTimeMs());
    }
}
