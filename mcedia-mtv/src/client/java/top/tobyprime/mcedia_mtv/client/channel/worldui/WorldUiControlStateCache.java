package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side cache for the sparse, event-driven state sent while a UI is watched. */
public final class WorldUiControlStateCache {
    private static final WorldUiControlStateCache INSTANCE = new WorldUiControlStateCache();
    private final Map<UUID, WorldUiControlState> states = new ConcurrentHashMap<>();

    private WorldUiControlStateCache() {
    }

    public static WorldUiControlStateCache getInstance() {
        return INSTANCE;
    }

    public void apply(WorldUiControlState state) {
        if (state != null) states.put(state.mtvUuid(), state);
    }

    public WorldUiControlState state(UUID mtvUuid) {
        return mtvUuid == null ? null : states.get(mtvUuid);
    }

    public void remove(UUID mtvUuid) {
        if (mtvUuid != null) states.remove(mtvUuid);
    }

    public void clear() {
        states.clear();
    }
}
