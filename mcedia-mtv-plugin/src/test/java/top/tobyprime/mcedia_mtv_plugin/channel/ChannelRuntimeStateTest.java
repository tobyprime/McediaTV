package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelRuntimeStateTest {

    @Test
    void constructorSetsFields() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        assertEquals("ch:1", state.getChannelId());
        assertEquals(MtvChannelType.BROADCAST, state.getChannelType());
    }

    @Test
    void defaultValues() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.SELF);
        assertEquals(0L, state.getRevision());
        assertEquals(0L, state.getDurationMs());
        assertEquals(0, state.getPlaylistCursor());
        assertEquals(ChannelPlayOrderMode.SEQUENTIAL, state.getPlayOrderMode());
        assertEquals("", state.getCreatorName());
        assertEquals("", state.getCreatorUuid());
        assertEquals("", state.getChannelName());
        assertEquals("", state.getDescription());
        assertFalse(state.isDiscoverable());
        assertTrue(state.isPublicControl());
        assertTrue(state.isPaused());
        assertFalse(state.isPublicChannel());
    }

    @Test
    void settersClampNegativeValues() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.setRevision(-1);
        state.setDurationMs(-100);
        state.setPlaylistCursor(-5);
        state.setCreatedAtMs(-1);

        assertEquals(0L, state.getRevision());
        assertEquals(0L, state.getDurationMs());
        assertEquals(0, state.getPlaylistCursor());
        assertEquals(0L, state.getCreatedAtMs());
    }

    @Test
    void setNullPlayOrderModeDefaultsToSequential() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.setPlayOrderMode(null);
        assertEquals(ChannelPlayOrderMode.SEQUENTIAL, state.getPlayOrderMode());
    }

    @Test
    void setNullStringsDefaultToEmpty() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.setCreatorName(null);
        state.setCreatorUuid(null);
        state.setChannelName(null);
        state.setDescription(null);

        assertEquals("", state.getCreatorName());
        assertEquals("", state.getCreatorUuid());
        assertEquals("", state.getChannelName());
        assertEquals("", state.getDescription());
    }

    @Test
    void touchIncrementsRevisionAndUpdatedAt() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        long beforeRev = state.getRevision();
        long beforeUpdated = state.getUpdatedAtMs();

        sleepOneMs();
        state.touch();

        assertEquals(beforeRev + 1, state.getRevision());
        assertTrue(state.getUpdatedAtMs() >= beforeUpdated);
    }

    @Test
    void isPausedReturnsTrueWhenNotPlaying() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        assertTrue(state.isPaused()); // default state is STOPPED
    }

    @Test
    void isPausedReturnsFalseWhenPlaying() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.getPlayState().setState(ChannelPlaybackStatus.PLAYING);
        assertFalse(state.isPaused());
    }

    @Test
    void isPublicChannelRequiresBroadcastAndDiscoverable() {
        var broadcast = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        assertFalse(broadcast.isPublicChannel());

        broadcast.setDiscoverable(true);
        assertTrue(broadcast.isPublicChannel());

        var self = new ChannelRuntimeState("ch:2", MtvChannelType.SELF);
        self.setDiscoverable(true);
        assertFalse(self.isPublicChannel());
    }

    @Test
    void normalizedPlaylistCursorWhenEmpty() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        assertEquals(0, state.getNormalizedPlaylistCursor());
    }

    @Test
    void normalizedPlaylistCursorClampsToBounds() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.getPlaylist().add(new ChannelPlaylistItem("a.mp4"));

        state.setPlaylistCursor(-1);
        assertEquals(0, state.getNormalizedPlaylistCursor());

        state.setPlaylistCursor(5);
        assertEquals(0, state.getNormalizedPlaylistCursor());

        state.setPlaylistCursor(0);
        assertEquals(0, state.getNormalizedPlaylistCursor());
    }

    @Test
    void toSnapshotWithPlayingState() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.setRevision(3);
        state.getPlayState().setMediaUrl("http://example.com/v.mp4");
        state.getPlayState().setSpeed(1.5f);
        state.getPlayState().setMediaTimeMs(10_000L);
        state.getPlayState().setPlayTimeMs(0L);
        state.getPlayState().setState(ChannelPlaybackStatus.PLAYING);

        var snapshot = state.toSnapshot(5_000L, 120_000_000L, false, false);

        assertEquals("ch:1", snapshot.channelId());
        assertEquals(3, snapshot.revision());
        assertEquals("http://example.com/v.mp4", snapshot.mediaUrl());
        assertEquals(1.5f, snapshot.speed(), 1e-9);
        // anchorMediaTimeUs = 10000 * 1000 = 10_000_000
        assertEquals(10_000_000L, snapshot.anchorMediaTimeUs());
        // elapsedTimeMs = 5000 - 0 = 5000
        assertEquals(5_000L, snapshot.elapsedTimeMs());
        assertEquals("PLAYING", snapshot.state());
        assertFalse(snapshot.paused());
        assertEquals(120_000_000L, snapshot.resolvedDurationUs());
        assertFalse(snapshot.completed());
        assertFalse(snapshot.audienceSuspended());
    }

    @Test
    void toSnapshotWithStoppedState() {
        var state = new ChannelRuntimeState("ch:1", MtvChannelType.BROADCAST);
        state.getPlayState().setState(ChannelPlaybackStatus.STOPPED);
        state.getPlayState().setMediaTimeMs(20_000L);

        var snapshot = state.toSnapshot(100_000L, 0L, true, true);

        assertEquals("STOPPED", snapshot.state());
        assertTrue(snapshot.paused());
        assertEquals(0L, snapshot.elapsedTimeMs()); // not playing -> 0
        assertTrue(snapshot.completed());
        assertTrue(snapshot.audienceSuspended());
    }

    private static void sleepOneMs() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
