package top.tobyprime.mcedia_mtv_plugin.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.util.MediaUrlNormalizer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MtvChannelService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelService.class);

    private final MtvPlayerManager manager;
    private final Map<String, ChannelRuntimeState> channelStates = new ConcurrentHashMap<>();
    private final ChannelBindingRegistry bindingRegistry = new ChannelBindingRegistry();
    private final AudienceSessionManager audienceSessionManager = new AudienceSessionManager();
    private final ChannelRepository repository;
    private volatile Consumer<String> changeListener = channelId -> {};
    private volatile Consumer<String> removeListener = channelId -> {};

    public MtvChannelService(MtvPlayerManager manager) {
        this.manager = manager;
        this.repository = new FileSystemChannelRepository(manager.getPlugin());
    }

    public void loadPersistedStates() {
        int loaded = 0;
        for (var state : repository.list()) {
            if (channelStates.putIfAbsent(state.getChannelId(), state) == null) {
                loaded++;
            }
        }
        LOGGER.info("Loaded MTV channel states from filesystem: count={}", loaded);
    }

    public void setChangeListener(Consumer<String> changeListener) {
        this.changeListener = changeListener == null ? channelId -> {} : changeListener;
    }

    public void setRemoveListener(Consumer<String> removeListener) {
        this.removeListener = removeListener == null ? channelId -> {} : removeListener;
    }

    public MtvChannelBinding resolveBinding(ManagedMtvPlayer player) {
        var binding = player.getChannelBinding();
        if (binding == null) {
            binding = MtvChannelBinding.self();
        }
        return binding.resolveRuntime(player.getUuid());
    }

    public ChannelRuntimeState syncSnapshot(ManagedMtvPlayer player) {
        var runtimeBinding = bindRuntime(player);
        var state = loadBoundState(runtimeBinding, player);
        persistState(state);
        applyRuntimeStateToPlayer(player, state);
        LOGGER.info("Synced MTV channel snapshot to player: channel={}, type={}, entity={}, revision={}, mediaUrl={}, paused={}",
                runtimeBinding.channelId(), runtimeBinding.type(), player.getUuid(), state.getRevision(), state.getPlayState().getMediaUrl(), state.getPlayState().getState() != ChannelPlaybackStatus.PLAYING);
        return state;
    }

    public ChannelRuntimeState previewState(ManagedMtvPlayer player) {
        var runtimeBinding = bindRuntime(player);
        var state = loadBoundState(runtimeBinding, player);
        applyRuntimeStateToPlayer(player, state);
        return state;
    }

    public boolean mutatePlayback(ManagedMtvPlayer player, Function<ChannelRuntimeState, Boolean> mutation) {
        var runtimeBinding = bindRuntime(player);
        var state = loadBoundState(runtimeBinding, player);
        boolean changed = Boolean.TRUE.equals(mutation.apply(state));
        if (!changed) {
            applyRuntimeStateToPlayer(player, state);
            return false;
        }

        state.touch();
        persistState(state);
        applyRuntimeStateToPlayer(player, state);
        LOGGER.info("Mutated MTV channel playback: channel={}, type={}, entity={}, revision={}, mediaUrl={}, speed={}, mediaTimeMs={}, playTimeMs={}, state={}",
                runtimeBinding.channelId(), runtimeBinding.type(), player.getUuid(), state.getRevision(), state.getPlayState().getMediaUrl(),
                state.getPlayState().getSpeed(), state.getPlayState().getMediaTimeMs(), state.getPlayState().getPlayTimeMs(), state.getPlayState().getState());
        onChannelChanged(runtimeBinding.channelId());
        return true;
    }

    public boolean updateMediaUrl(ManagedMtvPlayer player, String mediaUrl) {
        return mutatePlayback(player, state -> {
            var normalized = MediaUrlNormalizer.normalize(mediaUrl);
            if (normalized.equals(state.getPlayState().getMediaUrl())) {
                return false;
            }
            ChannelTimelineCalculator.setMedia(state.getPlayState(), normalized, state.getPlayState().getMediaTimeMs(), System.currentTimeMillis(), normalized.isBlank() ? ChannelPlaybackStatus.STOPPED : ChannelPlaybackStatus.LOADING);
            state.getPlaylist().clear();
            if (!normalized.isBlank()) {
                state.getPlaylist().add(new ChannelPlaylistItem(normalized));
            }
            state.setPlaylistCursor(0);
            state.setDurationMs(0L);
            return true;
        });
    }

    public boolean updateSpeed(ManagedMtvPlayer player, float speed) {
        return mutatePlayback(player, state -> {
            float normalized = Math.max(0.25F, Math.min(4.0F, speed));
            if (Double.compare(normalized, state.getPlayState().getSpeed()) == 0) {
                return false;
            }
            ChannelTimelineCalculator.setSpeed(state.getPlayState(), normalized, System.currentTimeMillis());
            return true;
        });
    }

    public boolean updateStartAt(ManagedMtvPlayer player, long startAt) {
        return mutatePlayback(player, state -> {
            long normalized = Math.max(0L, startAt);
            long normalizedMs = Math.max(0L, normalized / 1000L);
            if (normalizedMs == state.getPlayState().getMediaTimeMs() && !state.getPlayState().getMediaUrl().isBlank()) {
                return false;
            }
            ChannelTimelineCalculator.seek(state.getPlayState(), normalizedMs, System.currentTimeMillis());
            return true;
        });
    }

    public boolean seekRelative(ManagedMtvPlayer player, long deltaUs) {
        return mutatePlayback(player, state -> {
            if (state.getPlayState().getMediaUrl().isBlank()) {
                return false;
            }
            long newPosMs = Math.max(0L, state.computeCurrentMediaTimeMs(System.currentTimeMillis()) + (deltaUs / 1000L));
            ChannelTimelineCalculator.seek(state.getPlayState(), newPosMs, System.currentTimeMillis());
            return true;
        });
    }

    public boolean togglePause(ManagedMtvPlayer player) {
        return mutatePlayback(player, state -> {
            if (state.getPlayState().getMediaUrl().isBlank()) {
                return false;
            }
            if (state.getPlayState().getState() == ChannelPlaybackStatus.PLAYING) {
                ChannelTimelineCalculator.pause(state.getPlayState(), System.currentTimeMillis());
            } else {
                ChannelTimelineCalculator.play(state.getPlayState(), System.currentTimeMillis());
            }
            return true;
        });
    }

    public boolean playPlaylistIndex(ManagedMtvPlayer player, int index) {
        return mutatePlayback(player, state -> selectPlaylistIndex(state, index, System.currentTimeMillis()));
    }

    public boolean playNextManual(ManagedMtvPlayer player) {
        return mutatePlayback(player, state -> {
            long nowMs = System.currentTimeMillis();
            return selectPlaylistIndex(state, state.getNormalizedPlaylistCursor() + 1, nowMs);
        });
    }

    public boolean playPreviousManual(ManagedMtvPlayer player) {
        return mutatePlayback(player, state -> {
            long nowMs = System.currentTimeMillis();
            return selectPlaylistIndex(state, state.getNormalizedPlaylistCursor() - 1, nowMs);
        });
    }

    public boolean appendPlaylistItem(ManagedMtvPlayer player, String mediaUrl) {
        return mutatePlayback(player, state -> addPlaylistItem(state, mediaUrl, false));
    }

    public boolean prependPlaylistItem(ManagedMtvPlayer player, String mediaUrl) {
        return mutatePlayback(player, state -> addPlaylistItem(state, mediaUrl, true));
    }

    public boolean removePlaylistItem(ManagedMtvPlayer player, int index) {
        return mutatePlayback(player, state -> {
            if (index < 0 || index >= state.getPlaylist().size()) {
                return false;
            }
            long nowMs = System.currentTimeMillis();
            int cursor = state.getNormalizedPlaylistCursor();
            state.getPlaylist().remove(index);
            if (state.getPlaylist().isEmpty()) {
                state.setPlaylistCursor(0);
                state.setDurationMs(0L);
                ChannelTimelineCalculator.stop(state.getPlayState(), nowMs);
                state.getPlayState().setMediaUrl("");
                return true;
            }
            if (index < cursor) {
                state.setPlaylistCursor(cursor - 1);
            } else if (index == cursor) {
                int nextCursor = Math.min(cursor, state.getPlaylist().size() - 1);
                return selectPlaylistIndex(state, nextCursor, nowMs);
            }
            return true;
        });
    }

    public boolean movePlaylistItemToFront(ManagedMtvPlayer player, int index) {
        return mutatePlayback(player, state -> movePlaylistItem(state, index, 0));
    }

    public boolean movePlaylistItemToBack(ManagedMtvPlayer player, int index) {
        return mutatePlayback(player, state -> movePlaylistItem(state, index, state.getPlaylist().size() - 1));
    }

    public boolean cyclePlayOrderMode(ManagedMtvPlayer player) {
        return mutatePlayback(player, state -> {
            state.setPlayOrderMode(state.getPlayOrderMode().next());
            return true;
        });
    }

    private boolean selectPlaylistIndex(ChannelRuntimeState state, int index, long nowMs) {
        if (index < 0 || index >= state.getPlaylist().size()) {
            return false;
        }
        var item = state.getPlaylist().get(index);
        state.setPlaylistCursor(index);
        state.setDurationMs(0L);
        ChannelTimelineCalculator.setMedia(state.getPlayState(), item.mediaUrl(), 0L, nowMs, ChannelPlaybackStatus.LOADING);
        return true;
    }

    private boolean addPlaylistItem(ChannelRuntimeState state, String mediaUrl, boolean prepend) {
        var normalized = MediaUrlNormalizer.normalize(mediaUrl);
        if (normalized.isBlank()) {
            return false;
        }
        long nowMs = System.currentTimeMillis();
        var item = new ChannelPlaylistItem(normalized);
        if (prepend) {
            state.getPlaylist().add(0, item);
            if (state.getPlaylist().size() > 1) {
                state.setPlaylistCursor(state.getNormalizedPlaylistCursor() + 1);
            }
        } else {
            state.getPlaylist().add(item);
        }
        if (state.getPlaylist().size() == 1 || state.getPlayState().getMediaUrl().isBlank()) {
            return selectPlaylistIndex(state, 0, nowMs);
        }
        return true;
    }

    private boolean movePlaylistItem(ChannelRuntimeState state, int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= state.getPlaylist().size() || toIndex < 0 || toIndex >= state.getPlaylist().size() || fromIndex == toIndex) {
            return false;
        }
        var item = state.getPlaylist().remove(fromIndex);
        state.getPlaylist().add(toIndex, item);
        int cursor = state.getNormalizedPlaylistCursor();
        if (cursor == fromIndex) {
            state.setPlaylistCursor(toIndex);
        } else if (fromIndex < cursor && toIndex >= cursor) {
            state.setPlaylistCursor(cursor - 1);
        } else if (fromIndex > cursor && toIndex <= cursor) {
            state.setPlaylistCursor(cursor + 1);
        }
        return true;
    }

    private MtvChannelBinding bindRuntime(ManagedMtvPlayer player) {
        var configuredBinding = player.getChannelBinding();
        if (configuredBinding == null) {
            configuredBinding = MtvChannelBinding.self();
        }
        var runtimeBinding = configuredBinding.resolveRuntime(player.getUuid());
        player.setChannelBinding(configuredBinding);
        bindingRegistry.bind(player.getUuid(), runtimeBinding);
        return runtimeBinding;
    }

    private ChannelRuntimeState loadBoundState(MtvChannelBinding runtimeBinding, ManagedMtvPlayer player) {
        return channelStates.computeIfAbsent(runtimeBinding.channelId(), key -> {
            var loaded = repository.load(runtimeBinding, player);
            LOGGER.info("Created MTV channel state: channel={}, type={}, entity={}", runtimeBinding.channelId(), runtimeBinding.type(), player.getUuid());
            return loaded;
        });
    }

    private void applyRuntimeStateToPlayer(ManagedMtvPlayer player, ChannelRuntimeState state) {
        player.setMediaUrl(state.getPlayState().getMediaUrl());
        player.setSpeed((float) state.getPlayState().getSpeed());
        player.setStartAt(Math.max(0L, state.getPlayState().getMediaTimeMs() * 1000L));
        player.setBaseTime(state.getPlayState().getPlayTimeMs());
        player.setBaseOffset(Math.max(0L, state.getPlayState().getMediaTimeMs() * 1000L));
        player.setPaused(state.getPlayState().getState() != ChannelPlaybackStatus.PLAYING);
    }

    public void persistState(ChannelRuntimeState state) {
        repository.save(state);
    }

    public void onChannelChanged(String channelId) {
        changeListener.accept(channelId);
    }

    public ChannelRuntimeState getChannelState(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        return channelStates.get(channelId);
    }

    public ChannelRuntimeState ensureChannelState(String channelId, Supplier<ManagedMtvPlayer> loader) {
        var state = getChannelState(channelId);
        if (state != null) {
            return state;
        }
        var persisted = repository.load(channelId);
        if (persisted != null) {
            channelStates.put(channelId, persisted);
            return persisted;
        }
        if (loader == null) {
            return null;
        }
        var player = loader.get();
        if (player == null) {
            return null;
        }
        var binding = resolveBinding(player);
        if (!channelId.equals(binding.channelId())) {
            return null;
        }
        return syncSnapshot(player);
    }

    public Collection<ChannelRuntimeState> getChannelStates() {
        return List.copyOf(channelStates.values());
    }

    public MtvPlayerManager getManager() {
        return manager;
    }

    public AudienceSessionManager getAudienceSessionManager() {
        return audienceSessionManager;
    }

    public ChannelRepository getRepository() {
        return repository;
    }

    public void unregister(UUID entityUuid) {
        var binding = bindingRegistry.get(entityUuid);
        if (binding == null) {
            return;
        }
        bindingRegistry.unbind(entityUuid);
        if (!bindingRegistry.hasMembers(binding.channelId()) && binding.isStandalone()) {
            channelStates.remove(binding.channelId());
            removeListener.accept(binding.channelId());
            audienceSessionManager.invalidateChannel(binding.channelId());
        }
    }

    public void debugDump() {
        LOGGER.debug("MTV channel states={}", channelStates.size());
    }
}
