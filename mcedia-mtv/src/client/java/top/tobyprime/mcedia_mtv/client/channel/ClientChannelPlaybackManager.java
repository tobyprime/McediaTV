package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.client.Minecraft;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientChannelPlaybackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientChannelPlaybackManager.class);
    private static final ClientChannelPlaybackManager INSTANCE = new ClientChannelPlaybackManager();

    private final Map<String, ClientChannelSession> sessions = new ConcurrentHashMap<>();

    private ClientChannelPlaybackManager() {
    }

    public static ClientChannelPlaybackManager getInstance() {
        return INSTANCE;
    }

    public void onSnapshot(ClientChannelPlaybackSnapshot snapshot) {
        LOGGER.debug("Routing MTV snapshot: channel={}, revision={}",
                snapshot == null ? null : snapshot.channelId(), snapshot == null ? null : snapshot.revision());
        applySnapshot(snapshot, false);
    }

    public void onSync(ClientChannelPlaybackSnapshot snapshot) {
        LOGGER.debug("Routing MTV sync: channel={}, revision={}",
                snapshot == null ? null : snapshot.channelId(), snapshot == null ? null : snapshot.revision());
        applySnapshot(snapshot, true);
    }

    private void applySnapshot(ClientChannelPlaybackSnapshot snapshot, boolean forceResync) {
        if (snapshot == null || snapshot.channelId() == null || snapshot.channelId().isBlank()) {
            return;
        }
        if (snapshot.mediaUrl() != null && !snapshot.mediaUrl().isBlank()) {
            MtvMediaMetadataCache.getInstance().resolveAsync(snapshot.mediaUrl());
        }
        var session = sessions.get(snapshot.channelId());
        if (session != null) {
            session.updateSnapshot(snapshot.receivedNow(ClientChannelSession.currentMonotonicMs()), forceResync);
            LOGGER.debug("Applied MTV snapshot: channel={}, revision={}, forceResync={}, sessions={}",
                    snapshot.channelId(), snapshot.revision(), forceResync, sessions.size());
        } else {
            LOGGER.warn("Dropped MTV snapshot without active session: channel={}, revision={}, forceResync={}, sessions={}",
                    snapshot.channelId(), snapshot.revision(), forceResync, sessions.size());
        }
    }

    public void onRemove(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var session = sessions.get(channelId);
        if (session != null) {
            LOGGER.info("Received MTV channel removal: channel={}, sessionUnused={}", channelId, session.isUnused());
            session.updateSnapshot(ClientChannelPlaybackSnapshot.EMPTY, true);
            if (session.isUnused()) {
                session.destroy();
                sessions.remove(channelId);
            }
        }
    }

    public ClientChannelSession attach(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }
        boolean newSession = !sessions.containsKey(channelId);
        var session = sessions.computeIfAbsent(channelId, ClientChannelSession::new);
        boolean shouldSubscribe = session.isUnused();
        session.attach();
        LOGGER.info("Attached MTV channel session: channel={}, newSession={}, subscribe={}, sessions={}",
                channelId, newSession, shouldSubscribe, sessions.size());
        if (shouldSubscribe) {
            LOGGER.debug("Sending MTV channel subscribe: channel={}", channelId);
            MtvChannelSubscribeSender.send(new MtvChannelSubscriptionRequest(channelId));
        }
        return session;
    }

    public void detach(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var session = sessions.get(channelId);
        if (session == null) {
            LOGGER.warn("Ignored MTV channel detach without session: channel={}", channelId);
            return;
        }
        session.detach();
        LOGGER.info("Detached MTV channel session: channel={}, unused={}", channelId, session.isUnused());
        if (session.isUnused()) {
            MtvChannelUnsubscribeSender.send(new MtvChannelSubscriptionRequest(channelId));
            session.destroy();
            sessions.remove(channelId);
        }
    }

    public void detachForPowerOff(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var session = sessions.get(channelId);
        if (session == null) {
            return;
        }
        session.detach();
        if (session.isUnused()) {
            MtvChannelUnsubscribeSender.send(new MtvChannelSubscriptionRequest(channelId));
            session.suspend();
        }
    }

    public void onClientTick(Minecraft client) {
        for (var session : sessions.values()) {
            session.tick();
        }
    }

    /** Returns the latest authoritative snapshot already received for an attached channel. */
    public ClientChannelPlaybackSnapshot snapshot(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return ClientChannelPlaybackSnapshot.EMPTY;
        }
        var session = sessions.get(channelId);
        return session == null ? ClientChannelPlaybackSnapshot.EMPTY : session.getSnapshot();
    }

    public void clear() {
        LOGGER.info("Clearing MTV channel sessions: count={}", sessions.size());
        for (var session : sessions.values()) {
            session.destroy();
        }
        sessions.clear();
        LOGGER.info("Cleared MTV channel sessions");
    }
}
