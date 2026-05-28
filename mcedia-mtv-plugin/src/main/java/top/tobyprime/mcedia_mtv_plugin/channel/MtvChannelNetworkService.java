package top.tobyprime.mcedia_mtv_plugin.channel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.BiConsumer;

public final class MtvChannelNetworkService implements PluginMessageListener, Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelNetworkService.class);

    private final Plugin plugin;
    private final MtvChannelService channelService;
    private volatile boolean closed;

    public MtvChannelNetworkService(Plugin plugin, MtvChannelService channelService) {
        this.plugin = plugin;
        this.channelService = channelService;
        registerChannels();
    }

    private void registerChannels() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SYNC);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_REMOVE);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SUBSCRIBE, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_UNSUBSCRIBE, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HEARTBEAT, this);
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
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SYNC);
        messenger.unregisterOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_REMOVE);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (closed) {
            return;
        }
        runOnPlayer(player, () -> {
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
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterClient(event.getPlayer());
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
        LOGGER.debug("Published MTV channel snapshot to player: player={}, channel={}, revision={}, mediaUrl={}, paused={}, completed={}",
                player.getName(), snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.paused(), snapshot.completed());
    }

    public void invalidateChannel(String channelId) {
        if (closed || channelId == null || channelId.isBlank()) {
            return;
        }
        int recipients = broadcastRemove(channelId);
        LOGGER.debug("Invalidated MTV channel: channel={}, recipients={}", channelId, recipients);
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
        channelService.getAudienceSessionManager().subscribe(player.getUniqueId(), request.channelId(), System.currentTimeMillis());
        publishSnapshotTo(player, request.channelId());
    }

    private void handleUnsubscribe(Player player, byte[] message) {
        MtvChannelSubscriptionRequest request;
        try {
            request = MtvChannelProtocol.decodeSubscription(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV unsubscribe from {}", player.getName(), e);
            return;
        }
        channelService.getAudienceSessionManager().unsubscribe(player.getUniqueId(), request.channelId());
    }

    private void handleHeartbeat(Player player, byte[] message) {
        MtvAudienceHeartbeat heartbeat;
        try {
            heartbeat = MtvChannelProtocol.decodeHeartbeat(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV heartbeat from {}", player.getName(), e);
            return;
        }
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
    }

    private AudienceSessionManager.AudienceSummary summarizeAudience(ChannelRuntimeState state, long nowMs) {
        return channelService.getAudienceSessionManager().summarize(state.getChannelId(), state.getRevision(), nowMs);
    }

    private ChannelSnapshot toSnapshot(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience, long nowMs) {
        return state.toSnapshot(nowMs, Math.max(0L, audience.resolvedDurationMs() * 1000L), audience.completed());
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
        publish(state, audience, this::sendSnapshot, "snapshot");
    }

    private void publishSync(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience) {
        publish(state, audience, this::sendSync, "sync");
    }

    private void publish(ChannelRuntimeState state, AudienceSessionManager.AudienceSummary audience, BiConsumer<Player, ChannelSnapshot> sender, String kind) {
        long nowMs = System.currentTimeMillis();
        var snapshot = toSnapshot(state, audience, nowMs);
        int recipients = broadcast(snapshot, sender);
        LOGGER.debug("Published MTV channel {}: channel={}, revision={}, mediaUrl={}, paused={}, completed={}, recipients={}",
                kind, snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.paused(), snapshot.completed(), recipients);
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
        runOnPlayer(player, () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT, MtvChannelProtocol.encodeSnapshot(snapshot)));
    }

    private void sendSync(Player player, ChannelSnapshot snapshot) {
        runOnPlayer(player, () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_SYNC, MtvChannelProtocol.encodeSnapshot(snapshot)));
    }

    private void sendRemove(Player player, String channelId) {
        runOnPlayer(player, () -> player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_REMOVE, MtvChannelProtocol.encodeRemove(channelId)));
    }

    private void runOnPlayer(Player player, Runnable action) {
        if (closed || player == null || action == null) {
            return;
        }
        player.getScheduler().run(plugin, task -> {
            if (closed) {
                return;
            }
            action.run();
        }, null);
    }

    private void unregisterClient(Player player) {
        channelService.getAudienceSessionManager().unregisterClient(player.getUniqueId());
        LOGGER.debug("Unregistered MTV client: player={}", player.getName());
    }
}
