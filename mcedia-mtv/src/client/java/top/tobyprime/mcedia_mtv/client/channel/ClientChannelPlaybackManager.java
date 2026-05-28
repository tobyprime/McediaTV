package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientChannelPlaybackManager {
    private static final ClientChannelPlaybackManager INSTANCE = new ClientChannelPlaybackManager();

    private final Map<String, ClientChannelSession> sessions = new ConcurrentHashMap<>();

    private ClientChannelPlaybackManager() {
    }

    public static ClientChannelPlaybackManager getInstance() {
        return INSTANCE;
    }

    public void onSnapshot(ClientChannelPlaybackSnapshot snapshot) {
        applySnapshot(snapshot, false);
    }

    public void onSync(ClientChannelPlaybackSnapshot snapshot) {
        applySnapshot(snapshot, true);
    }

    private void applySnapshot(ClientChannelPlaybackSnapshot snapshot, boolean forceResync) {
        if (snapshot == null || snapshot.channelId() == null || snapshot.channelId().isBlank()) {
            return;
        }
        var session = sessions.get(snapshot.channelId());
        if (session != null) {
            session.updateSnapshot(snapshot.receivedNow(ClientChannelSession.currentMonotonicMs()), forceResync);
        }
    }

    public void onRemove(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        var session = sessions.get(channelId);
        if (session != null) {
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
        var session = sessions.computeIfAbsent(channelId, ClientChannelSession::new);
        boolean shouldSubscribe = session.isUnused();
        session.attach();
        if (shouldSubscribe) {
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
            return;
        }
        session.detach();
        if (session.isUnused()) {
            MtvChannelUnsubscribeSender.send(new MtvChannelSubscriptionRequest(channelId));
            session.destroy();
            sessions.remove(channelId);
        }
    }

    public void onClientTick(Minecraft client) {
        for (var session : sessions.values()) {
            session.tick();
        }
    }

    public void clear() {
        for (var session : sessions.values()) {
            session.destroy();
        }
        sessions.clear();
    }
}
