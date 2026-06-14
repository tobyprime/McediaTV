package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqLiteChannelRepositoryTest {

    private Connection connection;
    private SqLiteChannelRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        repo = new SqLiteChannelRepository(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        repo.close();
    }

    // ==================== CRUD ====================

    @Test
    void saveAndLoad() {
        var state = createState("test:ch1", MtvChannelType.BROADCAST);
        state.setChannelName("Test Channel");
        state.setDescription("A test channel");
        state.setCreatorName("Tester");
        state.setCreatorUuid(UUID.randomUUID().toString());
        state.setDiscoverable(true);
        state.setPublicControl(false);
        state.setDurationMs(120_000L);
        state.setPlaylistCursor(2);
        state.setPlayOrderMode(ChannelPlayOrderMode.LOOP_ALL);

        state.getPlayState().setMediaUrl("http://example.com/video.mp4");
        state.getPlayState().setMediaTimeMs(15_000L);
        state.getPlayState().setSpeed(1.5);
        state.getPlayState().setState(ChannelPlaybackStatus.PLAYING);
        state.getPlaylist().add(new ChannelPlaylistItem("http://example.com/1.mp4"));
        state.getPlaylist().add(new ChannelPlaylistItem("http://example.com/2.mp4"));

        repo.save(state);

        var loaded = repo.load("test:ch1");
        assertNotNull(loaded);
        assertEquals("test:ch1", loaded.getChannelId());
        assertEquals(MtvChannelType.BROADCAST, loaded.getChannelType());
        assertEquals("Test Channel", loaded.getChannelName());
        assertEquals("A test channel", loaded.getDescription());
        assertEquals("Tester", loaded.getCreatorName());
        assertTrue(loaded.isDiscoverable());
        assertFalse(loaded.isPublicControl());
        assertEquals(120_000L, loaded.getDurationMs());
        assertEquals(2, loaded.getPlaylistCursor());
        assertEquals(ChannelPlayOrderMode.LOOP_ALL, loaded.getPlayOrderMode());
        assertEquals("http://example.com/video.mp4", loaded.getPlayState().getMediaUrl());
        assertEquals(15_000L, loaded.getPlayState().getMediaTimeMs());
        assertEquals(1.5, loaded.getPlayState().getSpeed());
        assertEquals(ChannelPlaybackStatus.PLAYING, loaded.getPlayState().getState());
        assertEquals(2, loaded.getPlaylist().size());
        assertEquals("http://example.com/1.mp4", loaded.getPlaylist().get(0).mediaUrl());
        assertEquals("http://example.com/2.mp4", loaded.getPlaylist().get(1).mediaUrl());
    }

    @Test
    void saveAndLoadSelfType() {
        var state = createState("self:test", MtvChannelType.SELF);
        state.setChannelName("My Channel");
        repo.save(state);

        var loaded = repo.load("self:test");
        assertNotNull(loaded);
        assertEquals(MtvChannelType.SELF, loaded.getChannelType());
    }

    @Test
    void saveOverwritesExisting() {
        var state = createState("ch:1", MtvChannelType.BROADCAST);
        state.setChannelName("v1");
        repo.save(state);

        state.setChannelName("v2");
        repo.save(state);

        var loaded = repo.load("ch:1");
        assertNotNull(loaded);
        assertEquals("v2", loaded.getChannelName());
    }

    @Test
    void deleteRemovesState() {
        var state = createState("ch:delete", MtvChannelType.BROADCAST);
        repo.save(state);
        assertNotNull(repo.load("ch:delete"));

        repo.delete("ch:delete");
        assertNull(repo.load("ch:delete"));
    }

    @Test
    void listReturnsAll() {
        repo.save(createState("ch:a", MtvChannelType.BROADCAST));
        repo.save(createState("ch:b", MtvChannelType.SELF));
        repo.save(createState("ch:c", MtvChannelType.BROADCAST));

        var all = repo.list();
        assertEquals(3, all.size());
    }

    @Test
    void listReturnsCopy() {
        repo.save(createState("ch:1", MtvChannelType.BROADCAST));
        var beforeDelete = repo.list();
        assertEquals(1, beforeDelete.size());

        repo.delete("ch:1");
        assertEquals(0, repo.list().size());
    }

    @Test
    void emptyListWhenNoData() {
        assertTrue(repo.list().isEmpty());
    }

    // ==================== Null / Blank guards ====================

    @Test
    void loadNullReturnsNull() {
        assertNull(repo.load(null));
    }

    @Test
    void loadBlankReturnsNull() {
        assertNull(repo.load(""));
        assertNull(repo.load("   "));
    }

    @Test
    void saveNullDoesNothing() {
        repo.save(null);
        assertTrue(repo.list().isEmpty());
    }

    @Test
    void saveStateWithNullIdDoesNothing() {
        var state = new ChannelRuntimeState(null, MtvChannelType.BROADCAST);
        repo.save(state);
        assertTrue(repo.list().isEmpty());
    }

    @Test
    void saveStateWithBlankIdDoesNothing() {
        var state = new ChannelRuntimeState("", MtvChannelType.BROADCAST);
        repo.save(state);
        assertTrue(repo.list().isEmpty());
    }

    @Test
    void deleteNullDoesNothing() {
        repo.delete(null);
        assertTrue(repo.list().isEmpty());
    }

    @Test
    void deleteBlankDoesNothing() {
        repo.delete("");
        assertTrue(repo.list().isEmpty());
    }

    // ==================== Play state serialization ====================

    @Test
    void roundTripPlayStatePlaying() {
        var state = createState("ps:1", MtvChannelType.BROADCAST);
        state.getPlayState().setMediaUrl("http://example.com/stream");
        state.getPlayState().setState(ChannelPlaybackStatus.PLAYING);
        state.getPlayState().setSpeed(2.0);
        state.getPlayState().setMediaTimeMs(99_000L);
        state.getPlayState().setPlayTimeMs(100_000L);
        repo.save(state);

        var loaded = repo.load("ps:1");
        assertNotNull(loaded);
        assertEquals("http://example.com/stream", loaded.getPlayState().getMediaUrl());
        assertEquals(ChannelPlaybackStatus.PLAYING, loaded.getPlayState().getState());
        assertEquals(2.0, loaded.getPlayState().getSpeed(), 1e-9);
        assertEquals(99_000L, loaded.getPlayState().getMediaTimeMs());
        assertEquals(100_000L, loaded.getPlayState().getPlayTimeMs());
    }

    @Test
    void roundTripPlayStatePaused() {
        var state = createState("ps:paused", MtvChannelType.BROADCAST);
        state.getPlayState().setState(ChannelPlaybackStatus.PAUSED);
        state.getPlayState().setMediaUrl("http://example.com/paused");
        repo.save(state);

        var loaded = repo.load("ps:paused");
        assertNotNull(loaded);
        assertEquals(ChannelPlaybackStatus.PAUSED, loaded.getPlayState().getState());
    }

    @Test
    void roundTripPlayStateStopped() {
        var state = createState("ps:stopped", MtvChannelType.BROADCAST);
        state.getPlayState().setState(ChannelPlaybackStatus.STOPPED);
        repo.save(state);

        var loaded = repo.load("ps:stopped");
        assertNotNull(loaded);
        assertEquals(ChannelPlaybackStatus.STOPPED, loaded.getPlayState().getState());
    }

    @Test
    void roundTripPlayStateLoading() {
        var state = createState("ps:loading", MtvChannelType.BROADCAST);
        state.getPlayState().setState(ChannelPlaybackStatus.LOADING);
        repo.save(state);

        var loaded = repo.load("ps:loading");
        assertNotNull(loaded);
        assertEquals(ChannelPlaybackStatus.LOADING, loaded.getPlayState().getState());
    }

    // ==================== Playlist serialization ====================

    @Test
    void roundTripEmptyPlaylist() {
        var state = createState("pl:empty", MtvChannelType.BROADCAST);
        repo.save(state);

        var loaded = repo.load("pl:empty");
        assertNotNull(loaded);
        assertTrue(loaded.getPlaylist().isEmpty());
    }

    @Test
    void roundTripMultiplePlaylistItems() {
        var state = createState("pl:multi", MtvChannelType.BROADCAST);
        state.getPlaylist().add(new ChannelPlaylistItem("http://example.com/a.mp4"));
        state.getPlaylist().add(new ChannelPlaylistItem("http://example.com/b.mp4"));
        state.getPlaylist().add(new ChannelPlaylistItem("http://example.com/c.mp4"));
        repo.save(state);

        var loaded = repo.load("pl:multi");
        assertNotNull(loaded);
        assertEquals(3, loaded.getPlaylist().size());
        assertEquals("http://example.com/a.mp4", loaded.getPlaylist().get(0).mediaUrl());
        assertEquals("http://example.com/b.mp4", loaded.getPlaylist().get(1).mediaUrl());
        assertEquals("http://example.com/c.mp4", loaded.getPlaylist().get(2).mediaUrl());
    }

    @Test
    void roundTripPlaylistWithSpecialCharacters() {
        var state = createState("pl:special", MtvChannelType.BROADCAST);
        state.getPlaylist().add(new ChannelPlaylistItem("https://example.com/空间/视频?t=1&q=测试"));
        repo.save(state);

        var loaded = repo.load("pl:special");
        assertNotNull(loaded);
        assertEquals(1, loaded.getPlaylist().size());
        assertEquals("https://example.com/空间/视频?t=1&q=测试", loaded.getPlaylist().get(0).mediaUrl());
    }

    // ==================== Numeric field edge cases ====================

    @Test
    void roundTripZeroValues() {
        var state = createState("num:0", MtvChannelType.BROADCAST);
        state.setRevision(0);
        state.setDurationMs(0);
        state.setPlaylistCursor(0);
        state.setCreatedAtMs(0);
        repo.save(state);

        var loaded = repo.load("num:0");
        assertNotNull(loaded);
        assertEquals(0L, loaded.getRevision());
        assertEquals(0L, loaded.getDurationMs());
        assertEquals(0, loaded.getPlaylistCursor());
        assertEquals(0L, loaded.getCreatedAtMs());
    }

    @Test
    void roundTripLargeValues() {
        var state = createState("num:large", MtvChannelType.BROADCAST);
        state.setRevision(Long.MAX_VALUE);
        state.setDurationMs(Long.MAX_VALUE);
        state.setPlaylistCursor(Integer.MAX_VALUE);
        state.setCreatedAtMs(Long.MAX_VALUE);
        repo.save(state);

        var loaded = repo.load("num:large");
        assertNotNull(loaded);
        assertEquals(Long.MAX_VALUE, loaded.getRevision());
        assertEquals(Long.MAX_VALUE, loaded.getDurationMs());
        assertEquals(Integer.MAX_VALUE, loaded.getPlaylistCursor());
        assertEquals(Long.MAX_VALUE, loaded.getCreatedAtMs());
    }

    // ==================== ID with special characters ====================

    @Test
    void idWithColon() {
        var state = createState("namespace:channel-id", MtvChannelType.BROADCAST);
        repo.save(state);
        assertNotNull(repo.load("namespace:channel-id"));
    }

    @Test
    void idWithDashes() {
        var state = createState("ch-uuid-1234-5678", MtvChannelType.BROADCAST);
        repo.save(state);
        assertNotNull(repo.load("ch-uuid-1234-5678"));
    }

    @Test
    void idWithUnderscores() {
        var state = createState("my_channel_1", MtvChannelType.BROADCAST);
        repo.save(state);
        assertNotNull(repo.load("my_channel_1"));
    }

    // ==================== Multiple channels ====================

    @Test
    void multipleChannelsIndependent() {
        var ch1 = createState("ch:1", MtvChannelType.BROADCAST);
        ch1.setChannelName("First");
        var ch2 = createState("ch:2", MtvChannelType.SELF);
        ch2.setChannelName("Second");

        repo.save(ch1);
        repo.save(ch2);

        assertEquals("First", repo.load("ch:1").getChannelName());
        assertEquals("Second", repo.load("ch:2").getChannelName());
        assertEquals(MtvChannelType.SELF, repo.load("ch:2").getChannelType());
    }

    @Test
    void deleteOneDoesNotAffectOthers() {
        repo.save(createState("ch:a", MtvChannelType.BROADCAST));
        repo.save(createState("ch:b", MtvChannelType.BROADCAST));
        repo.save(createState("ch:c", MtvChannelType.BROADCAST));

        repo.delete("ch:b");

        assertNotNull(repo.load("ch:a"));
        assertNull(repo.load("ch:b"));
        assertNotNull(repo.load("ch:c"));
        assertEquals(2, repo.list().size());
    }

    // ==================== Update and revision ====================

    @Test
    void saveUpdatesRevision() {
        var state = createState("ch:rev", MtvChannelType.BROADCAST);
        state.setRevision(5);
        repo.save(state);

        var loaded = repo.load("ch:rev");
        assertEquals(5, loaded.getRevision());

        loaded.setRevision(10);
        repo.save(loaded);

        var reloaded = repo.load("ch:rev");
        assertEquals(10, reloaded.getRevision());
    }

    // ==================== Constructor error handling ====================

    @Test
    void nullConnectionThrows() {
        assertThrows(NullPointerException.class, () -> {
            // The DriverManager.getConnection would throw if we pass an invalid URL,
            // but passing null to our constructor would NPE
            new SqLiteChannelRepository((Connection) null);
        });
    }

    private static ChannelRuntimeState createState(String id, MtvChannelType type) {
        var state = new ChannelRuntimeState(id, type);
        state.setCreatedAtMs(System.currentTimeMillis());
        return state;
    }
}
