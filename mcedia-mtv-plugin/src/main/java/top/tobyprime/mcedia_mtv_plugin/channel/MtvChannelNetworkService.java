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
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.UUID;

public final class MtvChannelNetworkService implements PluginMessageListener, Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelNetworkService.class);

    private final Plugin plugin;
    private final MtvChannelService channelService;

    public MtvChannelNetworkService(Plugin plugin, MtvChannelService channelService) {
        this.plugin = plugin;
        this.channelService = channelService;
        registerChannels();
    }

    private void registerChannels() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        messenger.registerOutgoingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_REMOVE);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HELLO, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_SUBSCRIBE, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_HEARTBEAT, this);
        messenger.registerIncomingPluginChannel(plugin, MtvChannelProtocol.CHANNEL_MEDIA_INFO, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (MtvChannelProtocol.CHANNEL_HELLO.equals(channel)) {
            handleHello(player, message);
            return;
        }
        if (MtvChannelProtocol.CHANNEL_SUBSCRIBE.equals(channel)) {
            handleSubscribe(player, message);
            return;
        }
        if (MtvChannelProtocol.CHANNEL_HEARTBEAT.equals(channel)) {
            handleHeartbeat(player, message);
            return;
        }
        if (MtvChannelProtocol.CHANNEL_MEDIA_INFO.equals(channel)) {
            handleMediaInfo(player, message);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterClient(event.getPlayer());
    }

    public void publishSnapshot(ManagedMtvPlayer player) {
        if (player == null) {
            return;
        }
        var binding = channelService.resolveBinding(player);
        publishSnapshot(binding.channelId());
    }

    public void publishSnapshot(String channelId) {
        var state = channelService.getChannelState(channelId);
        if (state == null) {
            return;
        }
        var snapshot = state.toSnapshot(resolveDuration(channelId), isCompleted(channelId));
        int recipients = broadcastSnapshot(snapshot);
        LOGGER.info("Published MTV channel snapshot: channel={}, revision={}, mediaUrl={}, paused={}, completed={}, recipients={}",
                snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.paused(), snapshot.completed(), recipients);
    }

    public void publishSnapshotTo(Player player, String channelId) {
        if (player == null || channelId == null || channelId.isBlank()) {
            return;
        }
        var state = channelService.ensureChannelState(channelId, () -> channelService.getManager().findPlayerByChannelId(channelId));
        if (state == null) {
            LOGGER.debug("Skip MTV channel snapshot publish: missing channel state for channel={}, player={}", channelId, player.getName());
            return;
        }
        var snapshot = state.toSnapshot(resolveDuration(channelId), isCompleted(channelId));
        sendSnapshot(player, snapshot);
        LOGGER.info("Published MTV channel snapshot to player: player={}, channel={}, revision={}, mediaUrl={}, paused={}, completed={}",
                player.getName(), snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.paused(), snapshot.completed());
    }

    public void publishAllTo(Player player) {
        if (player == null) {
            return;
        }
        int sent = 0;
        for (var state : channelService.getChannelStates()) {
            sendSnapshot(player, state.toSnapshot(resolveDuration(state.getChannelId()), isCompleted(state.getChannelId())));
            sent++;
        }
        LOGGER.info("Published all MTV channel snapshots to player: player={}, count={}", player.getName(), sent);
    }

    public void invalidateChannel(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        int recipients = broadcastRemove(channelId);
        LOGGER.info("Invalidated MTV channel: channel={}, recipients={}", channelId, recipients);
        channelService.getAudienceSessionManager().invalidateChannel(channelId);
    }

    private void handleHello(Player player, byte[] message) {
        UUID sessionId;
        try {
            sessionId = MtvChannelProtocol.decodeHello(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV hello from {}", player.getName(), e);
            return;
        }

        channelService.getAudienceSessionManager().registerClient(player.getUniqueId(), sessionId);
        channelService.getAudienceSessionManager().subscribe(player.getUniqueId(), null);
        LOGGER.info("Registered MTV client: player={}, session={}", player.getName(), sessionId);
    }

    private void handleSubscribe(Player player, byte[] message) {
        MtvChannelSubscriptionRequest request;
        try {
            request = MtvChannelProtocol.decodeSubscription(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV subscribe from {}", player.getName(), e);
            return;
        }
        var expectedSessionId = channelService.getAudienceSessionManager().getSessionId(player.getUniqueId());
        if (expectedSessionId == null || !expectedSessionId.equals(request.sessionId())) {
            LOGGER.debug("Ignoring MTV subscribe from unregistered or stale client session: player={}, expectedSession={}, requestSession={}", player.getName(), expectedSessionId, request.sessionId());
            return;
        }
        channelService.getAudienceSessionManager().subscribe(player.getUniqueId(), request.channelId());
        publishSnapshotTo(player, request.channelId());
    }

    private void handleHeartbeat(Player player, byte[] message) {
        MtvAudienceHeartbeat heartbeat;
        try {
            heartbeat = MtvChannelProtocol.decodeHeartbeat(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV heartbeat from {}", player.getName(), e);
            return;
        }
        var expectedSessionId = channelService.getAudienceSessionManager().getSessionId(player.getUniqueId());
        if (expectedSessionId == null || !expectedSessionId.equals(heartbeat.sessionId())) {
            LOGGER.debug("Ignoring MTV heartbeat from unregistered or stale client session: player={}, expectedSession={}, heartbeatSession={}", player.getName(), expectedSessionId, heartbeat.sessionId());
            return;
        }
        var observedState = parseObservedState(heartbeat.state());
        channelService.getAudienceSessionManager().touch(
                player.getUniqueId(),
                heartbeat.sessionId(),
                heartbeat.channelId(),
                heartbeat.revision(),
                observedState,
                heartbeat.loadedMediaId(),
                Math.max(0L, heartbeat.durationUs() / 1000L),
                System.currentTimeMillis()
        );
        if (observedState == AudienceObservedState.ENDED) {
            maybePauseCompletedChannel(heartbeat.channelId());
        }
    }

    private void handleMediaInfo(Player player, byte[] message) {
        MtvMediaInfoReport report;
        try {
            report = MtvChannelProtocol.decodeMediaInfo(message);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode MTV media info report from {}", player.getName(), e);
            return;
        }
        var expectedSessionId = channelService.getAudienceSessionManager().getSessionId(player.getUniqueId());
        if (expectedSessionId == null || !expectedSessionId.equals(report.sessionId())) {
            LOGGER.debug("Ignoring MTV media info from unregistered or stale client session: player={}, expectedSession={}, reportSession={}", player.getName(), expectedSessionId, report.sessionId());
            return;
        }
        channelService.getAudienceSessionManager().touch(
                player.getUniqueId(),
                report.sessionId(),
                report.channelId(),
                report.revision(),
                AudienceObservedState.PLAYING,
                report.loadedMediaId(),
                Math.max(0L, report.durationUs() / 1000L),
                System.currentTimeMillis()
        );
        publishSnapshot(report.channelId());
    }

    private AudienceObservedState parseObservedState(String value) {
        return switch (value) {
            case "LOADING" -> AudienceObservedState.LOADING;
            case "ENDED" -> AudienceObservedState.ENDED;
            case "PAUSED" -> AudienceObservedState.PAUSED;
            default -> AudienceObservedState.PLAYING;
        };
    }

    private long resolveDuration(String channelId) {
        var state = channelService.getChannelState(channelId);
        if (state == null) {
            return 0L;
        }
        return Math.max(0L, channelService.getAudienceSessionManager().resolveDurationMs(channelId, state.getRevision()) * 1000L);
    }

    private boolean isCompleted(String channelId) {
        var state = channelService.getChannelState(channelId);
        return state != null && channelService.getAudienceSessionManager().isCompleted(channelId, state.getRevision());
    }

    private void maybePauseCompletedChannel(String channelId) {
        if (!isCompleted(channelId)) {
            return;
        }
        var state = channelService.getChannelState(channelId);
        if (state == null || state.isPaused()) {
            return;
        }
        long resolvedDurationUs = resolveDuration(channelId);
        state.setDurationMs(Math.max(0L, resolvedDurationUs / 1000L));
        ChannelPlaylistAdvancer.advanceOrPause(state, System.currentTimeMillis());
        state.touch();
        channelService.persistState(state);
        channelService.onChannelChanged(channelId);
        LOGGER.info("Advanced or paused completed MTV channel after majority ended: channel={}, revision={}, resolvedDurationUs={}, playlistCursor={}, paused={}",
                channelId, state.getRevision(), resolvedDurationUs, state.getPlaylistCursor(), state.isPaused());
    }

    private int broadcastSnapshot(ChannelSnapshot snapshot) {
        int recipients = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (channelService.getAudienceSessionManager().getSessionId(player.getUniqueId()) == null) {
                continue;
            }
            if (!channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), snapshot.channelId())) {
                continue;
            }
            sendSnapshot(player, snapshot);
            recipients++;
        }
        return recipients;
    }

    private int broadcastRemove(String channelId) {
        int recipients = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (channelService.getAudienceSessionManager().getSessionId(player.getUniqueId()) == null) {
                continue;
            }
            player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_REMOVE, MtvChannelProtocol.encodeRemove(channelId));
            recipients++;
        }
        return recipients;
    }

    private void sendSnapshot(Player player, ChannelSnapshot snapshot) {
        player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_SNAPSHOT, MtvChannelProtocol.encodeSnapshot(snapshot));
    }

    private void unregisterClient(Player player) {
        UUID sessionId = channelService.getAudienceSessionManager().getSessionId(player.getUniqueId());
        if (sessionId == null) {
            return;
        }
        channelService.getAudienceSessionManager().unregisterClient(player.getUniqueId());
        LOGGER.debug("Unregistered MTV client: player={}, session={}", player.getName(), sessionId);
    }
}
