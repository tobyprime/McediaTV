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
        var session = sessions.get(snapshot.channelId());
        if (session == null) {
            return;
        }
        session.updateSnapshot(snapshot);
    }

    public void onRemove(String channelId) {
        var session = sessions.get(channelId);
        if (session != null) {
            session.updateSnapshot(ClientChannelPlaybackSnapshot.EMPTY);
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
        session.attach();
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
