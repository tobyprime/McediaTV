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
    private static final long PERIODIC_SYNC_THRESHOLD_US = 3_000_000L;
    private static final long FORCED_SYNC_THRESHOLD_US = 1_000_000L;
    private static final long HEARTBEAT_INTERVAL_MS = 5_000L;

    private final String channelId;
    private final PlayerHost host;
    private ClientChannelPlaybackSnapshot snapshot = ClientChannelPlaybackSnapshot.EMPTY;
    private String playingUrl;
    private double lastAppliedSpeed = Double.NaN;
    private int attachments;
    private long lastHeartbeatAtMs;
    private boolean loadingMedia;
    private boolean errorMedia;
    private boolean forceResyncRequested;

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

    public void updateSnapshot(ClientChannelPlaybackSnapshot snapshot, boolean forceResync) {
        if (snapshot == null) {
            return;
        }
        if (this.snapshot.revision() != snapshot.revision()
                || !this.snapshot.mediaUrl().equals(snapshot.mediaUrl())
                || this.snapshot.paused() != snapshot.paused()
                || Double.compare(this.snapshot.speed(), snapshot.speed()) != 0) {
            forceResyncRequested = true;
        }
        forceResyncRequested = forceResyncRequested || forceResync;
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

    public void suspend() {
        snapshot = ClientChannelPlaybackSnapshot.EMPTY;
        playingUrl = null;
        loadingMedia = false;
        errorMedia = false;
        forceResyncRequested = false;
        lastAppliedSpeed = Double.NaN;
        lastHeartbeatAtMs = 0L;
        stopMedia();
    }

    public void destroy() {
        suspend();
        MediaPlayerHostManager.get().requestDestroy(host);
    }

    private void stopMediaIfIdle() {
        if (playingUrl == null) {
            return;
        }
        LOGGER.debug("Stop MTV media because channel has no active snapshot: channel={}, playingUrl={}", channelId, playingUrl);
        playingUrl = null;
        loadingMedia = false;
        errorMedia = false;
        lastAppliedSpeed = Double.NaN;
        stopMedia();
    }

    private void applySnapshot(ClientChannelPlaybackSnapshot snapshot) {
        if (!snapshot.hasMedia()) {
            stopMediaIfIdle();
            return;
        }
        boolean forceResync = forceResyncRequested;
        forceResyncRequested = false;
        boolean speedChanged = Double.compare(lastAppliedSpeed, snapshot.speed()) != 0;
        if (speedChanged) {
            lastAppliedSpeed = snapshot.speed();
        }
        boolean mediaChanged = !snapshot.mediaUrl().equals(playingUrl);
        boolean shouldRetryFailedMedia = forceResync && errorMedia && !loadingMedia;
        if (mediaChanged || shouldRetryFailedMedia) {
            LOGGER.debug("Load MTV media from snapshot: channel={}, revision={}, mediaUrl={}, retry={}",
                    snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), shouldRetryFailedMedia);
            playingUrl = snapshot.mediaUrl();
            loadingMedia = true;
            errorMedia = false;
            loadMediaFromChannel(snapshot);
            return;
        }

        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            return;
        }

        long target = computeTargetPositionUs(snapshot);
        long localPos = singlePlayer.getMedia() == null ? -1L : singlePlayer.getMedia().getEstimatedTime();
        long deltaUs = localPos >= 0 ? Math.abs(target - localPos) : Long.MAX_VALUE;
        boolean stateMismatch = snapshot.paused() != singlePlayer.isPaused();
        boolean shouldSeek = localPos >= 0 && ((forceResync && deltaUs > FORCED_SYNC_THRESHOLD_US) || deltaUs > PERIODIC_SYNC_THRESHOLD_US || stateMismatch);
        if (shouldSeek) {
            singlePlayer.seekAsync(target);
        }
        if (stateMismatch) {
            singlePlayer.setPaused(snapshot.paused());
        }
        if (speedChanged) {
            singlePlayer.setSpeed(snapshot.speed());
        }
    }

    private void loadMediaFromChannel(ClientChannelPlaybackSnapshot snapshot) {
        var player = host.getPlayer();
        if (!(player instanceof SingleMediaPlayer singlePlayer)) {
            LOGGER.warn("Host player is not SingleMediaPlayer for channel={}", snapshot.channelId());
            return;
        }
        try {
            singlePlayer.playAsync(() -> MediaResolvers.resolve(snapshot.mediaUrl()))
                    .thenAccept(mediaPlay -> {
                        loadingMedia = false;
                        errorMedia = false;
                        long target = computeTargetPositionUs(snapshot);
                        var duration = mediaPlay.getDuration();
                        long seekTarget = duration > 0 ? Math.min(target, duration) : target;
                        singlePlayer.seekAsync(seekTarget);
                        singlePlayer.setSpeed(snapshot.speed());
                        singlePlayer.setPaused(snapshot.paused());
                    })
                    .exceptionally(throwable -> {
                        loadingMedia = false;
                        errorMedia = true;
                        LOGGER.error("Failed to load media from channel: {}", snapshot.mediaUrl(), throwable);
                        return null;
                    });
        } catch (Exception e) {
            loadingMedia = false;
            errorMedia = true;
            LOGGER.error("Failed to start media load from channel: {}", snapshot.mediaUrl(), e);
        }
    }

    private long computeTargetPositionUs(ClientChannelPlaybackSnapshot snapshot) {
        long target = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) {
            target += (long) (Math.max(0L, snapshot.elapsedTimeMs()) * 1000L * snapshot.speed());
            target += (long) (Math.max(0L, currentMonotonicMs() - snapshot.receivedAtMonotonicMs()) * 1000L * snapshot.speed());
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
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        var media = singlePlayer.getMedia();
        long resolvedDurationUs = 0L;
        boolean loaded = false;
        boolean completed = false;
        if (media != null) {
            long localPositionUs = Math.max(0L, media.getEstimatedTime());
            resolvedDurationUs = Math.max(0L, media.getDuration());
            loaded = true;
            completed = resolvedDurationUs > 0L && localPositionUs >= resolvedDurationUs;
        }
        MtvChannelHeartbeatSender.send(new MtvAudienceHeartbeat(
                snapshot.channelId(),
                snapshot.revision(),
                loaded,
                completed,
                resolvedDurationUs,
                errorMedia
        ));
    }

    private void stopMedia() {
        var player = host.getPlayer();
        if (player instanceof SingleMediaPlayer singlePlayer) {
            singlePlayer.close();
        }
    }

    static long currentMonotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
