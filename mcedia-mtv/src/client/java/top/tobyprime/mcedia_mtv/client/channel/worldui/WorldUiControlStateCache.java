package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side cache for the sparse, event-driven state sent while a UI is watched. */
public final class WorldUiControlStateCache {
    private static final WorldUiControlStateCache INSTANCE = new WorldUiControlStateCache();
    private final Map<String, WorldUiControlState> states = new ConcurrentHashMap<>();

    private WorldUiControlStateCache() {
    }

    public static WorldUiControlStateCache getInstance() {
        return INSTANCE;
    }

    public void apply(WorldUiControlState state) {
        if (state != null) states.put(key(state.mtvUuid(), state.screenId()), state);
    }

    public WorldUiControlState state(UUID mtvUuid, String screenId) {
        return mtvUuid == null || screenId == null ? null : states.get(key(mtvUuid, screenId));
    }

    public void remove(UUID mtvUuid, String screenId) {
        if (mtvUuid != null && screenId != null) states.remove(key(mtvUuid, screenId));
    }

    public void clear() {
        states.clear();
    }

    private static String key(UUID mtvUuid, String screenId) {
        return mtvUuid + ":" + screenId;
    }
}
