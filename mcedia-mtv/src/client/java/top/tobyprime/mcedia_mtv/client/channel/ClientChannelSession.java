package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia.api.config.DecoderConfiguration;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;
import top.tobyprime.mcedia.player.core.SingleMediaPlayer;
import top.tobyprime.mcedia_core.client.player.MediaPlayerHostManager;
import top.tobyprime.mcedia_core.client.player.PlayerHost;

public final class ClientChannelSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientChannelSession.class);
    private static final long POSITION_SYNC_THRESHOLD_US = 10_000_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;

    private final String channelId;
    private final PlayerHost host;
    private ClientChannelPlaybackSnapshot snapshot = ClientChannelPlaybackSnapshot.EMPTY;
    private String playingUrl;
    private double lastAppliedSpeed = Double.NaN;
    private int attachments;
    private long lastHeartbeatAtMs;

    public ClientChannelSession(String channelId) {
        this.channelId = channelId;
        var handle = MediaPlayerHostManager.get().createHostAndGetId(new DecoderConfiguration.Builder().build());
        this.host = handle.host();
    }

    public String getChannelId() {
        return channelId;
    }

    public PlayerHost getHost() {
        return host;
    }

    public void attach() {
        attachments++;
    }

    public void detach() {
        attachments = Math.max(0, attachments - 1);
    }

    public boolean isUnused() {
        return attachments == 0;
    }

    public void updateSnapshot(ClientChannelPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.snapshot = snapshot;
    }

    public void tick() {
        if (attachments == 0) {
            stopMediaIfIdle();
            return;
        }
        if (!snapshot.hasMedia()) {
            stopMediaIfIdle();
            return;
        }
        applySnapshot(snapshot);
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatAtMs >= HEARTBEAT_INTERVAL_MS) {
            reportPlayback(snapshot);
            lastHeartbeatAtMs = now;
        }
    }

    public void destroy() {
        stopMedia();
        MediaPlayerHostManager.get().requestDestroy(host);
    }

    private void stopMediaIfIdle() {
        if (playingUrl == null) {
            return;
        }
        LOGGER.info("Stop MTV media because channel has no active snapshot: channel={}, playingUrl={}", channelId, playingUrl);
        playingUrl = null;
        lastAppliedSpeed = Double.NaN;
        stopMedia();
    }

    private void applySnapshot(ClientChannelPlaybackSnapshot snapshot) {
        if (!snapshot.hasMedia()) {
            stopMediaIfIdle();
            return;
        }
        if (Double.compare(lastAppliedSpeed, snapshot.speed()) != 0) {
            lastAppliedSpeed = snapshot.speed();
        }
        if (!snapshot.mediaUrl().equals(playingUrl)) {
            LOGGER.info("Load MTV media from snapshot: channel={}, revision={}, mediaUrl={}", snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl());
            playingUrl = snapshot.mediaUrl();
            loadMediaFromChannel(snapshot);
            return;
        }

        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            return;
        }

        long target = computeTargetPositionUs(snapshot);
        long localPos = singlePlayer.getMedia() == null ? -1L : singlePlayer.getMedia().getEstimatedTime();
        if (localPos >= 0 && Math.abs(target - localPos) > POSITION_SYNC_THRESHOLD_US) {
            singlePlayer.seekAsync(target);
        }
        if (snapshot.paused() != singlePlayer.isPaused()) {
            singlePlayer.setPaused(snapshot.paused());
        }
        singlePlayer.setSpeed(snapshot.speed());
    }

    private void loadMediaFromChannel(ClientChannelPlaybackSnapshot snapshot) {
        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            LOGGER.warn("Host player is not SingleMediaPlayer for channel={}", snapshot.channelId());
            return;
        }
        singlePlayer.playAsync(() -> MediaResolvers.resolve(snapshot.mediaUrl()))
                .thenAccept(mediaPlay -> {
                    long target = computeTargetPositionUs(snapshot);
                    if (target > 0) {
                        var duration = mediaPlay.getDuration();
                        long seekTarget = duration > 0 ? Math.min(target, duration) : target;
                        singlePlayer.seekAsync(seekTarget);
                    }
                    singlePlayer.setSpeed(snapshot.speed());
                    singlePlayer.setPaused(snapshot.paused());
                })
                .exceptionally(throwable -> {
                    LOGGER.error("Failed to load media from channel: {}", snapshot.mediaUrl(), throwable);
                    return null;
                });
    }

    private long computeTargetPositionUs(ClientChannelPlaybackSnapshot snapshot) {
        long target = snapshot.baseOffset();
        if (!snapshot.paused()) {
            long elapsedMs = Math.max(0L, System.currentTimeMillis() - snapshot.baseTime());
            target += (long) (elapsedMs * 1000L * snapshot.speed());
        }
        if (snapshot.resolvedDurationUs() > 0L) {
            target = Math.min(target, snapshot.resolvedDurationUs());
        }
        return Math.max(0L, target);
    }

    private void reportPlayback(ClientChannelPlaybackSnapshot snapshot) {
        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            return;
        }
        var media = singlePlayer.getMedia();
        if (media == null) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        long localPositionUs = media.getEstimatedTime();
        if (localPositionUs < 0) {
            return;
        }
        long resolvedDurationUs = media.getDuration();
        boolean completed = resolvedDurationUs > 0 && localPositionUs >= resolvedDurationUs;
        String observedState = completed ? "ENDED" : ("LOADING".equals(snapshot.state()) ? "LOADING" : (singlePlayer.isPaused() ? "PAUSED" : "PLAYING"));
        LOGGER.info("Report MTV playback state: player={}, channel={}, revision={}, localPositionUs={}, resolvedDurationUs={}, state={}, paused={}, completed={}",
                mc.player.getUUID(), snapshot.channelId(), snapshot.revision(), localPositionUs, resolvedDurationUs, observedState, singlePlayer.isPaused(), completed);
        var sessionId = MtvClientNetworkInitializer.getSessionId();
        if (sessionId == null) {
            return;
        }
        MtvChannelHeartbeatSender.send(new MtvAudienceHeartbeat(
                snapshot.channelId(),
                sessionId,
                snapshot.revision(),
                observedState,
                localPositionUs,
                (float) snapshot.speed(),
                snapshot.channelId() + ":" + snapshot.revision(),
                resolvedDurationUs
        ));
        if (resolvedDurationUs > 0L) {
            MtvChannelMediaInfoSender.send(new MtvMediaInfoReport(
                    snapshot.channelId(),
                    sessionId,
                    snapshot.revision(),
                    snapshot.channelId() + ":" + snapshot.revision(),
                    resolvedDurationUs
            ));
        }
    }

    private void stopMedia() {
        var player = host.getPlayer();
        if (player instanceof SingleMediaPlayer singlePlayer) {
            singlePlayer.close();
        }
    }
}
