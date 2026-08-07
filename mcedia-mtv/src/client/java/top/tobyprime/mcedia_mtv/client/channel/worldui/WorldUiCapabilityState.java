package top.tobyprime.mcedia_mtv.client.channel.worldui;

import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public final class WorldUiCapabilityState {
    private static final WorldUiCapabilityState INSTANCE = new WorldUiCapabilityState();

    private volatile WorldUiCapabilities capabilities;

    public static WorldUiCapabilityState getInstance() {
        return INSTANCE;
    }

    public boolean supported() {
        WorldUiCapabilities current = capabilities;
        return current != null
                && current.protocolVersion() == MtvChannelProtocol.WORLD_UI_PROTOCOL_VERSION
                && current.maxPageItems() > 0
                && current.maxPageItems() <= MtvChannelProtocol.MAX_PLAYLIST_PAGE_ITEMS;
    }

    public WorldUiCapabilities capabilities() {
        return capabilities;
    }

    public void onCapabilities(WorldUiCapabilities capabilities) {
        if (capabilities == null
                || capabilities.protocolVersion() != MtvChannelProtocol.WORLD_UI_PROTOCOL_VERSION
                || capabilities.maxPageItems() <= 0
                || capabilities.maxPageItems() > MtvChannelProtocol.MAX_PLAYLIST_PAGE_ITEMS
                || capabilities.featureFlags() < 0L) {
            this.capabilities = null;
            return;
        }
        this.capabilities = capabilities;
    }

    public void clear() {
        capabilities = null;
    }
}
