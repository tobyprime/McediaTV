package top.tobyprime.mcedia_mtv_plugin.channel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class FileSystemChannelRepository implements ChannelRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemChannelRepository.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDirectory;

    FileSystemChannelRepository(JavaPlugin plugin) {
        this.rootDirectory = plugin.getServer().getWorldContainer().toPath()
                .resolve("plugins")
                .resolve("mtv")
                .resolve("channels");
    }

    @Override
    public synchronized ChannelRuntimeState load(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        var bucket = readBucket(resolveFile(channelId));
        return bucket.get(channelId);
    }

    @Override
    public synchronized ChannelRuntimeState load(MtvChannelBinding binding, ManagedMtvPlayer player) {
        var runtimeBinding = binding.resolveRuntime(player.getUuid());
        var loaded = load(runtimeBinding.channelId());
        if (loaded != null) {
            return loaded;
        }
        return createFromPlayer(runtimeBinding, player);
    }

    @Override
    public synchronized void save(ChannelRuntimeState state) {
        if (state == null || state.getChannelId() == null || state.getChannelId().isBlank()) {
            return;
        }
        try {
            Files.createDirectories(rootDirectory);
            var file = resolveFile(state.getChannelId());
            var bucket = readBucket(file);
            bucket.put(state.getChannelId(), state);
            writeBucket(file, bucket);
        } catch (IOException e) {
            LOGGER.warn("Failed to persist MTV channel state: channel={}", state.getChannelId(), e);
        }
    }

    @Override
    public synchronized void delete(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        try {
            var file = resolveFile(channelId);
            var bucket = readBucket(file);
            if (bucket.remove(channelId) == null) {
                return;
            }
            if (bucket.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            writeBucket(file, bucket);
        } catch (IOException e) {
            LOGGER.warn("Failed to delete MTV channel state: channel={}", channelId, e);
        }
    }

    @Override
    public synchronized Collection<ChannelRuntimeState> list() {
        var states = new LinkedHashMap<String, ChannelRuntimeState>();
        if (!Files.isDirectory(rootDirectory)) {
            return states.values();
        }
        try (var files = Files.list(rootDirectory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readBucket(path).values().forEach(state -> states.put(state.getChannelId(), state)));
        } catch (IOException e) {
            LOGGER.warn("Failed to list MTV channel states from {}", rootDirectory, e);
        }
        return new ArrayList<>(states.values());
    }

    private ChannelRuntimeState createFromPlayer(MtvChannelBinding binding, ManagedMtvPlayer player) {
        var runtimeBinding = binding.resolveRuntime(player.getUuid());
        var state = new ChannelRuntimeState(runtimeBinding.channelId(), runtimeBinding.type());
        state.getPlayState().setMediaUrl(player.getMediaUrl());
        state.getPlayState().setSpeed(player.getSpeed());
        state.getPlayState().setMediaTimeMs(Math.max(0L, player.getBaseOffset() / 1000L));
        state.getPlayState().setPlayTimeMs(Math.max(0L, player.getBaseTime()));
        state.getPlayState().setState(player.isPaused()
                ? ChannelPlaybackStatus.PAUSED
                : (player.getMediaUrl().isBlank() ? ChannelPlaybackStatus.STOPPED : ChannelPlaybackStatus.PLAYING));
        if (!player.getMediaUrl().isBlank()) {
            state.getPlaylist().add(new ChannelPlaylistItem(player.getMediaUrl()));
        }
        return state;
    }

    private Path resolveFile(String channelId) {
        return rootDirectory.resolve(shardKey(channelId) + ".json");
    }

    private static String shardKey(String channelId) {
        var value = channelId;
        var colonIndex = value.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < value.length()) {
            value = value.substring(colonIndex + 1);
        }
        var dashIndex = value.indexOf('-');
        if (dashIndex >= 0) {
            value = value.substring(0, dashIndex);
        }
        value = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return value.isBlank() ? "default" : value.toLowerCase(Locale.ROOT);
    }

    private Map<String, ChannelRuntimeState> readBucket(Path file) {
        var bucket = new LinkedHashMap<String, ChannelRuntimeState>();
        if (!Files.isRegularFile(file)) {
            return bucket;
        }
        try {
            var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            var channels = getObject(root, "channels");
            if (channels == null) {
                return bucket;
            }
            for (var entry : channels.entrySet()) {
                var state = fromJson(entry.getKey(), entry.getValue());
                if (state != null) {
                    bucket.put(state.getChannelId(), state);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read MTV channel bucket: file={}", file, e);
        }
        return bucket;
    }

    private void writeBucket(Path file, Map<String, ChannelRuntimeState> bucket) throws IOException {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        var channels = new JsonObject();
        for (var entry : bucket.entrySet()) {
            channels.add(entry.getKey(), toJson(entry.getValue()));
        }
        root.add("channels", channels);
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
    }

    private JsonObject toJson(ChannelRuntimeState state) {
        var root = new JsonObject();
        root.addProperty("channelId", state.getChannelId());
        root.addProperty("channelType", state.getChannelType().name());
        root.addProperty("revision", state.getRevision());
        root.addProperty("durationMs", state.getDurationMs());
        root.addProperty("playlistCursor", state.getPlaylistCursor());

        var playState = new JsonObject();
        playState.addProperty("mediaUrl", state.getPlayState().getMediaUrl());
        playState.addProperty("state", state.getPlayState().getState().name());
        playState.addProperty("speed", state.getPlayState().getSpeed());
        playState.addProperty("mediaTimeMs", state.getPlayState().getMediaTimeMs());
        playState.addProperty("playTimeMs", state.getPlayState().getPlayTimeMs());
        root.add("playState", playState);

        var playlist = new JsonArray();
        for (var item : state.getPlaylist()) {
            var itemJson = new JsonObject();
            itemJson.addProperty("mediaUrl", item.mediaUrl());
            playlist.add(itemJson);
        }
        root.add("playlist", playlist);
        return root;
    }

    private ChannelRuntimeState fromJson(String fallbackChannelId, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        var root = element.getAsJsonObject();
        var channelId = getString(root, "channelId", fallbackChannelId);
        if (channelId.isBlank()) {
            return null;
        }
        var channelType = parseChannelType(getString(root, "channelType", MtvChannelType.BROADCAST.name()), MtvChannelType.BROADCAST);
        var state = new ChannelRuntimeState(channelId, channelType);
        state.setRevision(getLong(root, "revision", 0L));
        state.setDurationMs(getLong(root, "durationMs", 0L));
        state.setPlaylistCursor((int) getLong(root, "playlistCursor", 0L));

        var playState = getObject(root, "playState");
        if (playState != null) {
            state.getPlayState().setMediaUrl(getString(playState, "mediaUrl", ""));
            state.getPlayState().setState(parsePlaybackStatus(getString(playState, "state", ChannelPlaybackStatus.STOPPED.name())));
            state.getPlayState().setSpeed(getDouble(playState, "speed", 1.0D));
            state.getPlayState().setMediaTimeMs(getLong(playState, "mediaTimeMs", 0L));
            state.getPlayState().setPlayTimeMs(getLong(playState, "playTimeMs", System.currentTimeMillis()));
        }

        var playlist = getArray(root, "playlist");
        if (playlist != null) {
            for (var item : playlist) {
                if (item == null || item.isJsonNull()) {
                    continue;
                }
                if (item.isJsonPrimitive()) {
                    var mediaUrl = item.getAsString();
                    if (!mediaUrl.isBlank()) {
                        state.getPlaylist().add(new ChannelPlaylistItem(mediaUrl));
                    }
                    continue;
                }
                if (!item.isJsonObject()) {
                    continue;
                }
                var itemJson = item.getAsJsonObject();
                var mediaUrl = getString(itemJson, "mediaUrl", "");
                if (!mediaUrl.isBlank()) {
                    state.getPlaylist().add(new ChannelPlaylistItem(mediaUrl));
                }
            }
        }
        return state;
    }

    private static JsonObject getObject(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject(key);
    }

    private static JsonArray getArray(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) {
            return null;
        }
        return root.getAsJsonArray(key);
    }

    private static String getString(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        var element = root.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static long getLong(JsonObject root, String key, long fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        var element = root.get(key);
        return element.isJsonPrimitive() ? element.getAsLong() : fallback;
    }

    private static double getDouble(JsonObject root, String key, double fallback) {
        if (root == null || !root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }
        var element = root.get(key);
        return element.isJsonPrimitive() ? element.getAsDouble() : fallback;
    }

    private static MtvChannelType parseChannelType(String value, MtvChannelType fallback) {
        try {
            return MtvChannelType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static ChannelPlaybackStatus parsePlaybackStatus(String value) {
        try {
            return ChannelPlaybackStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return ChannelPlaybackStatus.STOPPED;
        }
    }
}
