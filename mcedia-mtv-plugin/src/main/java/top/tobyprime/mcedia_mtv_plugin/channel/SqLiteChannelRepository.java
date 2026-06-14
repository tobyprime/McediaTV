package top.tobyprime.mcedia_mtv_plugin.channel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class SqLiteChannelRepository implements ChannelRepository, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SqLiteChannelRepository.class);
    private static final Gson GSON = new Gson();
    private static final Type PLAYLIST_TYPE = new TypeToken<List<ChannelPlaylistItem>>() {}.getType();

    private final Connection connection;

    SqLiteChannelRepository(JavaPlugin plugin) throws SQLException {
        Path dbPath = plugin.getServer().getWorldContainer().toPath()
                .resolve("plugins")
                .resolve("mtv")
                .resolve("channels.db");
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new SQLException("Failed to create directory for SQLite database", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
        createTable();
    }

    private void createTable() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS channel_states (
                        channel_id          TEXT PRIMARY KEY,
                        channel_type        TEXT NOT NULL DEFAULT 'BROADCAST',
                        revision            INTEGER NOT NULL DEFAULT 0,
                        duration_ms         INTEGER NOT NULL DEFAULT 0,
                        playlist_cursor     INTEGER NOT NULL DEFAULT 0,
                        play_order_mode     TEXT NOT NULL DEFAULT 'SEQUENTIAL',
                        creator_name        TEXT NOT NULL DEFAULT '',
                        creator_uuid        TEXT NOT NULL DEFAULT '',
                        channel_name        TEXT NOT NULL DEFAULT '',
                        description         TEXT NOT NULL DEFAULT '',
                        discoverable        INTEGER NOT NULL DEFAULT 0,
                        public_control      INTEGER NOT NULL DEFAULT 1,
                        created_at_ms       INTEGER NOT NULL DEFAULT 0,
                        updated_at_ms       INTEGER NOT NULL DEFAULT 0,
                        play_state          TEXT NOT NULL DEFAULT '{}',
                        playlist            TEXT NOT NULL DEFAULT '[]'
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_channel_type ON channel_states(channel_type)");
        }
    }

    @Override
    public synchronized ChannelRuntimeState load(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        var sql = "SELECT * FROM channel_states WHERE channel_id = ?";
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to load MTV channel state: channel={}", channelId, e);
        }
        return null;
    }

    @Override
    public synchronized void save(ChannelRuntimeState state) {
        if (state == null || state.getChannelId() == null || state.getChannelId().isBlank()) {
            return;
        }
        var sql = """
                INSERT OR REPLACE INTO channel_states
                    (channel_id, channel_type, revision, duration_ms, playlist_cursor,
                     play_order_mode, creator_name, creator_uuid, channel_name, description,
                     discoverable, public_control, created_at_ms, updated_at_ms,
                     play_state, playlist)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, state.getChannelId());
            stmt.setString(2, state.getChannelType().name());
            stmt.setLong(3, state.getRevision());
            stmt.setLong(4, state.getDurationMs());
            stmt.setInt(5, state.getPlaylistCursor());
            stmt.setString(6, state.getPlayOrderMode().name());
            stmt.setString(7, state.getCreatorName());
            stmt.setString(8, state.getCreatorUuid());
            stmt.setString(9, state.getChannelName());
            stmt.setString(10, state.getDescription());
            stmt.setInt(11, state.isDiscoverable() ? 1 : 0);
            stmt.setInt(12, state.isPublicControl() ? 1 : 0);
            stmt.setLong(13, state.getCreatedAtMs());
            stmt.setLong(14, state.getUpdatedAtMs());
            stmt.setString(15, GSON.toJson(state.getPlayState()));
            stmt.setString(16, GSON.toJson(state.getPlaylist()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Failed to save MTV channel state: channel={}", state.getChannelId(), e);
        }
    }

    @Override
    public synchronized void delete(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var sql = "DELETE FROM channel_states WHERE channel_id = ?";
        try (var stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Failed to delete MTV channel state: channel={}", channelId, e);
        }
    }

    @Override
    public synchronized Collection<ChannelRuntimeState> list() {
        var states = new ArrayList<ChannelRuntimeState>();
        var sql = "SELECT * FROM channel_states";
        try (var stmt = connection.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                var state = mapRow(rs);
                if (state != null) {
                    states.add(state);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to list MTV channel states", e);
        }
        return states;
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private ChannelRuntimeState mapRow(ResultSet rs) throws SQLException {
        var channelId = rs.getString("channel_id");
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        var channelType = parseChannelType(rs.getString("channel_type"), MtvChannelType.BROADCAST);
        var state = new ChannelRuntimeState(channelId, channelType);
        state.setRevision(rs.getLong("revision"));
        state.setDurationMs(rs.getLong("duration_ms"));
        state.setPlaylistCursor(rs.getInt("playlist_cursor"));
        state.setPlayOrderMode(parsePlayOrderMode(rs.getString("play_order_mode")));
        state.setCreatorName(rs.getString("creator_name"));
        state.setCreatorUuid(rs.getString("creator_uuid"));
        state.setChannelName(rs.getString("channel_name"));
        state.setDescription(rs.getString("description"));
        state.setDiscoverable(rs.getInt("discoverable") != 0);
        state.setPublicControl(rs.getInt("public_control") != 0);
        state.setCreatedAtMs(rs.getLong("created_at_ms"));

        // Deserialize play_state
        var playStateJson = rs.getString("play_state");
        if (playStateJson != null && !playStateJson.isBlank()) {
            try {
                var playState = GSON.fromJson(playStateJson, ChannelPlayState.class);
                if (playState != null) {
                    state.getPlayState().setMediaUrl(playState.getMediaUrl());
                    state.getPlayState().setState(playState.getState());
                    state.getPlayState().setSpeed(playState.getSpeed());
                    state.getPlayState().setMediaTimeMs(playState.getMediaTimeMs());
                    state.getPlayState().setPlayTimeMs(playState.getPlayTimeMs());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse play_state JSON for channel: {}", channelId, e);
            }
        }

        // Deserialize playlist
        var playlistJson = rs.getString("playlist");
        if (playlistJson != null && !playlistJson.isBlank()) {
            try {
                List<ChannelPlaylistItem> items = GSON.fromJson(playlistJson, PLAYLIST_TYPE);
                if (items != null) {
                    state.getPlaylist().clear();
                    state.getPlaylist().addAll(items);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse playlist JSON for channel: {}", channelId, e);
            }
        }

        return state;
    }

    private static MtvChannelType parseChannelType(String value, MtvChannelType fallback) {
        try {
            return MtvChannelType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static ChannelPlayOrderMode parsePlayOrderMode(String value) {
        try {
            return ChannelPlayOrderMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ChannelPlayOrderMode.SEQUENTIAL;
        }
    }
}
