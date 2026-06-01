package top.tobyprime.mcedia_mtv_plugin.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single entry in the navigation history — represents one page visit
 * with its type, entity binding, peripheral, and arbitrary string state.
 * <p>
 * State is held in server-side {@link NavigationState} only. PDC serialization
 * on the item stack is not used — the server is the source of truth.
 * </p>
 */
public class PageEntry {
    private final MtvGui.GuiType type;
    private final UUID entityUuid;
    private final String peripheralId;
    private final Map<String, String> state;

    public PageEntry(MtvGui.GuiType type, UUID entityUuid, String peripheralId, Map<String, String> state) {
        this.type = type;
        this.entityUuid = entityUuid;
        this.peripheralId = peripheralId;
        this.state = state != null ? new HashMap<>(state) : new HashMap<>();
    }

    public PageEntry(MtvGui.GuiType type) {
        this(type, null, null, null);
    }

    public PageEntry(MtvGui.GuiType type, UUID entityUuid) {
        this(type, entityUuid, null, null);
    }

    public MtvGui.GuiType getType() { return type; }
    public UUID getEntityUuid() { return entityUuid; }
    public String getPeripheralId() { return peripheralId; }

    /** The mutable state map for this entry. Prefer {@link #getState(String, String)}. */
    public Map<String, String> getState() { return state; }

    public String getState(String key, String def) {
        return state.getOrDefault(key, def);
    }

    public void putState(String key, String value) {
        state.put(key, value);
    }
}
