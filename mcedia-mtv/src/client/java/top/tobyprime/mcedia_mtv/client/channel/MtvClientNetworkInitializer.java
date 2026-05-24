package top.tobyprime.mcedia_mtv.client.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class MtvClientNetworkInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientNetworkInitializer.class);
    private static volatile UUID sessionId;

    private MtvClientNetworkInitializer() {
    }

    public static void beginSession(UUID id) {
        sessionId = id;
        LOGGER.debug("Begin MTV client channel session: session={}", id);
    }

    public static UUID getSessionId() {
        return sessionId;
    }

    public static void onChannelSnapshot(ClientChannelPlaybackSnapshot snapshot) {
        LOGGER.info("Receive MTV channel snapshot: session={}, channel={}, revision={}, mediaUrl={}, speed={}, startAt={}, baseTime={}, baseOffset={}, state={}, paused={}, resolvedDurationUs={}, completed={}",
                sessionId, snapshot.channelId(), snapshot.revision(), snapshot.mediaUrl(), snapshot.speed(), snapshot.startAt(),
                snapshot.baseTime(), snapshot.baseOffset(), snapshot.state(), snapshot.paused(), snapshot.resolvedDurationUs(), snapshot.completed());
        ClientChannelPlaybackManager.getInstance().onSnapshot(snapshot);
    }

    public static void onChannelRemoved(String channelId) {
        LOGGER.info("Receive MTV channel removal: session={}, channel={}", sessionId, channelId);
        ClientChannelPlaybackManager.getInstance().onRemove(channelId);
    }

    public static void clearAll() {
        LOGGER.debug("Clear MTV client channel state: session={}", sessionId);
        sessionId = null;
        ClientChannelPlaybackManager.getInstance().clear();
    }
}
