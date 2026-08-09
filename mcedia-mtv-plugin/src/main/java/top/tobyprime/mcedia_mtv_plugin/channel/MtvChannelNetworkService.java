package top.tobyprime.mcedia_mtv_plugin.channel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiCapabilities;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlDispatcher;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlResult;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlState;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiPlaylistPageRequest;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiPlaylistPublisher;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiRateLimiter;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiWatchRegistry;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiWatchRequest;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

public final class MtvChannelNetworkService implements PluginMessageListener, Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelNetworkService.class);

    private final Plugin plugin;
    private final MtvChannelService channelService;
    private final WorldUiPlaylistPublisher playlistPublisher;
    private final WorldUiRateLimiter worldUiRateLimiter;
    private final WorldUiControlDispatcher worldUiControlDispatcher;
    private final WorldUiWatchRegistry worldUiWatchRegistry = new WorldUiWatchRegistry();
    private final Map<UUID, String> watchedChannelsByMtv = new ConcurrentHashMap<>();
    private final Map<String, WorldUiControlState> lastControlStates = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public MtvChannelNetworkService(Plugin plugin, MtvChannelService channelService) {
        this.plugin = plugin;
        this.channelService = channelService;
        var config = plugin.getConfig();
        this.worldUiRateLimiter = new WorldUiRateLimiter(new WorldUiRateLimiter.Limits(
                positiveConfigValue(config.getInt("world-ui.rate-limits.control-per-second", 10), 10),
                positiveConfigValue(config.getInt("world-ui.rate-limits.page-per-second", 4), 4),
                positiveConfigValue(config.getInt("world-ui.rate-limits.watch-per-second", 2), 2)
        ));
        this.playlistPublisher = new WorldUiPlaylistPublisher(plugin, channelService);
        this.worldUiControlDispatcher = new WorldUiControlDispatcher(channelService.getManager(), worldUiRateLimiter);
        registerChannels();
    }

    private void registerChannels() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SYNC);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_REMOVE);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HUD_BINDING);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.WORLD_UI_CAPABILITIES);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_MANIFEST);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_CONTROL_RESULT);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.WORLD_UI_CONTROL_STATE);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SUBSCRIBE, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_UNSUBSCRIBE, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HEARTBEAT, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE_REQUEST, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_CONTROL_REQUEST, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_WORLD_UI_WATCH, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_WORLD_UI_UNWATCH, this);
    }

    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SUBSCRIBE, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_UNSUBSCRIBE, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HEARTBEAT, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE_REQUEST, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_CONTROL_REQUEST, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_WORLD_UI_WATCH, this);
        messenger.unregisterIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_WORLD_UI_UNWATCH, this);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SYNC);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_REMOVE);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HUD_BINDING);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.WORLD_UI_CAPABILITIES);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_MANIFEST);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_CONTROL_RESULT);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.WORLD_UI_CONTROL_STATE);
        worldUiWatchRegistry.clear();
        watchedChannelsByMtv.clear();
        lastControlStates.clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (closed) {
            return;
        }
        runOnPlayer(player, "receive " + channel, () -> {
            if (MtvChannelProtocol.CHANNEL_SUBSCRIBE.equals(channel)) {
                handleSubscribe(player, message);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_UNSUBSCRIBE.equals(channel)) {
                handleUnsubscribe(player, message);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_HEARTBEAT.equals(channel)) {
                handleHeartbeat(player, message);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE_REQUEST.equals(channel)) {
                handlePlaylistPageRequest(player, message);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_CONTROL_REQUEST.equals(channel)) {
                handleControlRequest(player, message);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_WORLD_UI_WATCH.equals(channel)) {
                handleWorldUiWatch(player, message, true);
                return;
            }
            if (MtvChannelProtocol.CHANNEL_WORLD_UI_UNWATCH.equals(channel)) {
                handleWorldUiWatch(player, message, false);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterClient(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        worldUiWatchRegistry.unwatch(player.getUniqueId());
        removeEmptyWatchedChannels();
        lastControlStates.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        LOGGER.debug("Cleared MTV world UI watch after world change: player={}", player.getName());
    }

    public void publishSnapshot(String channelId) {
        if (closed) {
            return;
        }
        var state = channelService.getChannelState(channelId);
        if (state == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        publishSnapshot(state, summarizeAudience(state, nowMs));
    }

    public void publishPeriodicUpdates() {
        if (closed) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        for (var state : channelService.getChannelStates()) {
            if (!state.getPlayState().hasMedia()) {
                continue;
            }
            if (channelService.getAudienceSessionManager().getActiveSessions(state.getChannelId(), nowMs).isEmpty()) {
                continue;
            }
            publishSnapshot(state, summarizeAudience(state, nowMs));
        }
    }

    public void publishSnapshotTo(Player player, String channelId) {
        if (closed || player == null || channelId == null || channelId.isBlank()) {
            return;
        }
        var state = channelService.ensureChannelState(channelId);
        if (state == null) {
            LOGGER.debug("Skip MTV channel snapshot publish: missing channel state for channel={}, player={}", channelId, player.getName());
            return;
        }
        long nowMs = System.currentTimeMillis();
        var snapshot = toSnapshot(state, summarizeAudience(state, nowMs), nowMs);
        sendSnapshot(player, snapshot);
        sendSync(player, snapshot);
        LOGGER.debug("Published MTV channel snapshot to player: player={}, channel={}, revision={}, mediaUrl={}, paused={}, completed={}, audienceSuspended={}",
                player.getName(), snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.paused(), snapshot.completed(), snapshot.audienceSuspended());
    }

    public void invalidateChannel(String channelId) {
        if (closed || channelId == null || channelId.isBlank()) {
            return;
        }
        int recipients = broadcastRemove(channelId);
        LOGGER.debug("Invalidated MTV channel: channel={}, recipients={}", channelId, recipients);
        for (var entry : watchedChannelsByMtv.entrySet()) {
            if (channelId.equals(entry.getValue())) clearMtvWatch(entry.getKey());
        }
        channelService.getAudienceSessionManager().invalidateChannel(channelId);
    }

    private void handleSubscribe(Player player, byte[] message) {
        MtvChannelSubscriptionRequest request;
        try {
            request = MtvChannelProtocol.decodeSubscription(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV subscribe from {}", player.getName(), e);
            return;
        }
        try {
            channelService.getAudienceSessionManager().subscribe(player.getUniqueId(), request.channelId(), System.currentTimeMillis());
            sendCapabilities(player);
            publishSnapshotTo(player, request.channelId());
            playlistPublisher.publishManifest(request.channelId());
        } catch (Exception e) {
            LOGGER.warn("Failed to handle MTV subscribe: player={}, channel={}", player.getName(), request.channelId(), e);
        }
    }

    public void publishPlaylistManifest(String channelId) {
        playlistPublisher.publishManifest(channelId);
    }

    private void handlePlaylistPageRequest(Player player, byte[] message) {
        WorldUiPlaylistPageRequest request;
        try {
            request = MtvChannelProtocol.decodePlaylistPageRequest(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV playlist page request from {}", player.getName(), e);
            return;
        }
        if (!worldUiRateLimiter.tryAcquire(player.getUniqueId(), WorldUiRateLimiter.RequestType.PAGE)) {
            return;
        }
        if (!channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), request.channelId())) {
            return;
        }
        var state = channelService.ensureChannelState(request.channelId());
        if (state == null || state.getRevision() != request.knownRevision()) {
            playlistPublisher.publishManifest(request.channelId());
            return;
        }
        try {
            playlistPublisher.publishPage(player, request.channelId(), request.offset());
        } catch (Exception e) {
            LOGGER.warn("Failed to send MTV playlist page: player={}, channel={}, offset={}", player.getName(), request.channelId(), request.offset(), e);
        }
    }

    private void handleControlRequest(Player player, byte[] message) {
        WorldUiControlRequest request;
        try {
            request = MtvChannelProtocol.decodeControlRequest(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV world UI control request from {}", player.getName(), e);
            return;
        }
        worldUiControlDispatcher.dispatch(player, request, result -> {
            sendControlResult(player, result);
            if (!result.accepted() && result.error() == WorldUiControlError.STALE_REVISION) {
                publishPlaylistManifest(request.channelId());
            }
            if (result.accepted()) {
                publishControlState(request.targetMtvUuid(), request.screenId());
                // The requester is normally covered by publishControlState; force a
                // direct send only when the watch registry dropped it (a transient
                // entity read failure), otherwise their local UI never sees the change.
                if (!worldUiWatchRegistry.watchers(request.targetMtvUuid()).contains(player.getUniqueId())) {
                    sendControlStateTo(player, request.targetMtvUuid(), request.screenId());
                }
            }
        });
    }

    private void handleWorldUiWatch(Player player, byte[] message, boolean watch) {
        WorldUiWatchRequest request;
        try {
            request = MtvChannelProtocol.decodeWatchRequest(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV world UI watch request from {}", player.getName(), e);
            return;
        }
        if (!watch) {
            worldUiWatchRegistry.unwatch(player.getUniqueId());
            removeEmptyWatchedChannels();
            return;
        }
        if (!worldUiRateLimiter.tryAcquire(player.getUniqueId(), WorldUiRateLimiter.RequestType.WATCH)) {
            return;
        }
        channelService.getManager().withManagedPlayer(request.targetMtvUuid(), target -> {
            if (!target.isPowered()) {
                return Boolean.FALSE;
            }
            var binding = channelService.resolveBinding(target);
            if (player.getWorld() == null || !player.getWorld().getName().equals(target.getWorld())
                    || !WorldUiControlDispatcher.canWatchTarget(player, target, binding)) {
                return Boolean.FALSE;
            }
            worldUiWatchRegistry.watch(player.getUniqueId(), request.targetMtvUuid());
            removeEmptyWatchedChannels();
            watchedChannelsByMtv.put(request.targetMtvUuid(), binding.channelId());
            sendControlState(player, target, true, null);
            return Boolean.TRUE;
        }, ignored -> { });
    }

    /** Publishes only to players currently watching this MTV; callers invoke this after an actual state change. */
    public void publishControlState(UUID mtvUuid) {
        publishControlState(mtvUuid, null);
    }

    public void publishControlState(UUID mtvUuid, String screenId) {
        if (closed || mtvUuid == null) return;
        for (UUID playerId : worldUiWatchRegistry.watchers(mtvUuid)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            channelService.getManager().withManagedPlayer(mtvUuid, target -> {
                if (!target.isPowered()) {
                    return Boolean.FALSE;
                }
                sendControlState(player, target, false, screenId);
                return Boolean.TRUE;
            }, ignored -> { });
        }
    }

    /**
     * Always sends the current control state to one player (the requester of an
     * accepted control), bypassing the watch registry and dedup. The watch
     * registry can drop a player on a transient entity read failure; without
     * this the local UI would never observe its own accepted changes.
     */
    private void sendControlStateTo(Player player, UUID mtvUuid, String screenId) {
        if (closed || player == null || mtvUuid == null) return;
        channelService.getManager().withManagedPlayer(mtvUuid, target -> {
            if (!target.isPowered()) {
                return Boolean.FALSE;
            }
            sendControlState(player, target, true, screenId);
            return Boolean.TRUE;
        }, ignored -> { });
    }

    /** Clears world UI watchers and cached state when an MTV entity is deleted. */
    public void onMtvRemoved(UUID mtvUuid) {
        clearMtvWatch(mtvUuid);
    }

    private void sendControlState(Player player, top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer target, boolean force, String screenId) {
        if (player == null || target == null) return;
        var binding = channelService.resolveBinding(target);
        var channel = channelService.ensureChannelState(binding.channelId());
        if (channel == null) return;
        var screen = screenId == null ? null : target.findScreen(screenId);
        if (screen == null) screen = target.getScreen();
        var state = new WorldUiControlState(target.getUuid(), binding.channelId(), target.getMasterVolume(),
                MtvPlayerManager.canControlPlayer(player, target) && channelService.canControlChannelPlayback(player, channel), channel.getRevision(),
                screen.getId(), screen.getMinBrightness(), screen.isDanmakuVisible());
        // State is now per-screen (brightness/danmaku), so the dedup key must be too;
        // otherwise two equal states for different screens of the same entity collide.
        String key = player.getUniqueId() + ":" + target.getUuid() + ":" + screen.getId();
        if (!force && state.equals(lastControlStates.putIfAbsent(key, state))) return;
        lastControlStates.put(key, state);
        runOnPlayer(player, "send world UI control state", () -> player.sendPluginMessage(
                plugin, MtvChannelProtocol.WORLD_UI_CONTROL_STATE, MtvChannelProtocol.encodeWorldUiControlState(state)));
    }

    private void handleUnsubscribe(Player player, byte[] message) {
        MtvChannelSubscriptionRequest request;
        try {
            request = MtvChannelProtocol.decodeSubscription(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV unsubscribe from {}", player.getName(), e);
            return;
        }
        try {
            channelService.getAudienceSessionManager().unsubscribe(player.getUniqueId(), request.channelId());
        } catch (Exception e) {
            LOGGER.warn("Failed to handle MTV unsubscribe: player={}, channel={}", player.getName(), request.channelId(), e);
        }
    }

    private void handleHeartbeat(Player player, byte[] message) {
        MtvAudienceHeartbeat heartbeat;
        try {
            heartbeat = MtvChannelProtocol.decodeHeartbeat(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV heartbeat from {}", player.getName(), e);
            return;
        }
        try {
            var audienceSessionManager = channelService.getAudienceSessionManager();
            if (!audienceSessionManager.isSubscribed(player.getUniqueId(), heartbeat.channelId())) {
                LOGGER.debug("Ignoring MTV heartbeat for unsubscribed channel: player={}, channel={}", player.getName(), heartbeat.channelId());
                return;
            }
            long nowMs = System.currentTimeMillis();
            var touch = audienceSessionManager.touch(
                    player.getUniqueId(),
                    heartbeat.channelId(),
                    heartbeat.revision(),
                    heartbeat.loaded(),
                    heartbeat.completed(),
                    heartbeat.error(),
                    heartbeat.suspended(),
                    Math.max(0L, heartbeat.durationUs() / 1000L),
                    nowMs
            );
            if (!touch.stateChanged()) {
                return;
            }
            var state = channelService.getChannelState(heartbeat.channelId());
            if (state == null) {
                return;
            }
            var audience = summarizeAudience(state, nowMs);
            maybeStartLoadedChannel(state, nowMs, audience);
            if (touch.durationChanged()) {
                publishSnapshot(state, audience);
            }
            if (heartbeat.error()) {
                publishSync(state, audience);
            }
            if (heartbeat.completed()) {
                maybePauseCompletedChannel(state, nowMs, audience);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to handle MTV heartbeat: player={}, channel={}, revision={}", player.getName(), heartbeat.channelId(), heartbeat.revision(), e);
        }
    }

    private AudienceSessionManager.AudienceSummary summarizeAudience(ChannelRuntimeState state, long nowMs) {
        return channelService.getAudienceSessionManager().summarize(state.getChannelId(), state.getRevision(), nowMs);
    }

    private ChannelSnapshot toSnapshot(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience, long nowMs) {
        long audienceDurationUs = Math.max(0L, audience.resolvedDurationMs() * 1000L);
        // After a seek the channel revision bumps but audience sessions still carry the old
        // revision, so the majority resolution briefly reads 0. Keep the last known media
        // duration cached on the channel state instead of showing "--:--" in the client.
        long cachedDurationUs = Math.max(0L, state.getDurationMs()) * 1000L;
        long durationUs = audienceDurationUs > 0L ? audienceDurationUs : cachedDurationUs;
        if (audienceDurationUs > 0L) {
            state.setDurationMs(audienceDurationUs / 1000L);
        }
        return state.toSnapshot(nowMs, durationUs, audience.completed(), audience.majoritySuspended());
    }

    private void maybeStartLoadedChannel(ChannelRuntimeState state, long nowMs, AudienceSessionManager.AudienceSummary audience) {
        if (state.getPlayState().getState() != ChannelPlaybackStatus.LOADING || !audience.majorityLoaded()) {
            return;
        }
        ChannelTimelineCalculator.play(state.getPlayState(), nowMs);
        state.touch();
        channelService.persistState(state);
        channelService.onChannelChanged(state.getChannelId());
        LOGGER.debug("Started MTV channel after majority loaded current revision: channel={}, revision={}", state.getChannelId(), state.getRevision());
    }

    private void maybePauseCompletedChannel(ChannelRuntimeState state, long nowMs, AudienceSessionManager.AudienceSummary audience) {
        if (!audience.completed() || state.isPaused()) {
            return;
        }
        long resolvedDurationUs = Math.max(0L, audience.resolvedDurationMs() * 1000L);
        state.setDurationMs(Math.max(0L, resolvedDurationUs / 1000L));
        ChannelPlaylistAdvancer.advanceOrPause(state, nowMs);
        state.touch();
        channelService.persistState(state);
        channelService.onChannelChanged(state.getChannelId());
        LOGGER.debug("Advanced or paused completed MTV channel after majority ended: channel={}, revision={}, resolvedDurationUs={}, playlistCursor={}, paused={}",
                state.getChannelId(), state.getRevision(), resolvedDurationUs, state.getPlaylistCursor(), state.isPaused());
    }

    private void publishSnapshot(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience) {
        publish(state, audience, this::sendSnapshot);
    }

    private void publishSync(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience) {
        publish(state, audience, this::sendSync);
    }

    private void publish(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience, BiConsumer<Player, ChannelSnapshot> sender) {
        long nowMs = System.currentTimeMillis();
        var snapshot = toSnapshot(state, audience, nowMs);
        broadcast(snapshot, sender);
    }

    private int broadcastSnapshot(ChannelSnapshot snapshot) {
        return broadcast(snapshot, this::sendSnapshot);
    }

    private int broadcastRemove(String channelId) {
        int recipients = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), channelId)) {
                continue;
            }
            sendRemove(player, channelId);
            recipients++;
        }
        return recipients;
    }

    private int broadcastSync(ChannelSnapshot snapshot) {
        return broadcast(snapshot, this::sendSync);
    }

    private int broadcast(ChannelSnapshot snapshot, BiConsumer<Player, ChannelSnapshot> sender) {
        int recipients = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), snapshot.channelId())) {
                continue;
            }
            sender.accept(player, snapshot);
            recipients++;
        }
        return recipients;
    }

    private void sendSnapshot(Player player, ChannelSnapshot snapshot) {
        runOnPlayer(player, "send snapshot", () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT, MtvChannelProtocol.encodeSnapshot(snapshot)));
    }

    private void sendSync(Player player, ChannelSnapshot snapshot) {
        runOnPlayer(player, "send sync", () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_SYNC, MtvChannelProtocol.encodeSnapshot(snapshot)));
    }

    private void sendRemove(Player player, String channelId) {
        runOnPlayer(player, "send remove", () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_REMOVE, MtvChannelProtocol.encodeRemove(channelId)));
    }

    private void sendControlResult(Player player, WorldUiControlResult result) {
        runOnPlayer(player, "send world UI control result", () -> player.sendPluginMessage(
                plugin, MtvChannelProtocol.CHANNEL_CONTROL_RESULT, MtvChannelProtocol.encodeControlResult(result)));
    }

    private void sendCapabilities(Player player) {
        var capabilities = new WorldUiCapabilities(MtvChannelProtocol.WORLD_UI_PROTOCOL_VERSION, MtvChannelProtocol.MAX_PLAYLIST_PAGE_ITEMS, 0L);
        runOnPlayer(player, "send world UI capabilities", () -> player.sendPluginMessage(plugin, MtvChannelProtocol.WORLD_UI_CAPABILITIES, MtvChannelProtocol.encodeCapabilities(capabilities)));
    }

    private void runOnPlayer(Player player, String actionName, Runnable action) {
        if (closed || player == null || action == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (closed) {
                return;
            }
            try {
                action.run();
            } catch (Exception e) {
                LOGGER.warn("Failed to {} for player={}", actionName, player.getName(), e);
            }
        }, null);
    }

    private void unregisterClient(Player player) {
        channelService.getAudienceSessionManager().unregisterClient(player.getUniqueId());
        worldUiRateLimiter.clear(player.getUniqueId());
        worldUiWatchRegistry.unwatch(player.getUniqueId());
        removeEmptyWatchedChannels();
        lastControlStates.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        LOGGER.debug("Unregistered MTV client: player={}", player.getName());
    }

    private void clearMtvWatch(UUID mtvUuid) {
        if (mtvUuid == null) return;
        worldUiWatchRegistry.unwatchMtv(mtvUuid);
        watchedChannelsByMtv.remove(mtvUuid);
        lastControlStates.keySet().removeIf(key -> key.contains(":" + mtvUuid + ":"));
    }

    private void removeEmptyWatchedChannels() {
        watchedChannelsByMtv.entrySet().removeIf(entry -> worldUiWatchRegistry.watchers(entry.getKey()).isEmpty());
    }

    private static int positiveConfigValue(int configured, int defaultValue) {
        return configured > 0 ? configured : defaultValue;
    }
}
