package top.tobyprime.mcedia_mtv_plugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.selector.MtvPlayerSelector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central GUI coordinator — owns page registry, per-player navigation state,
 * and provides utility methods shared by all pages.
 * <p>
 * All rendering and click logic lives in individual {@link GuiPage} implementations.
 * {@link MtvGui} only brokers: navigation, chat-input dispatch, shutdown.
 */
public class MtvGui {
    // ─────────────────────────────────────────────────────────
    //  Page type enum
    // ─────────────────────────────────────────────────────────

    public enum GuiType {
        MAIN_MENU,
        NEARBY_PLAYER_LIST,
        PLAYER_MENU,
        PERIPHERAL_LIST,
        SCREEN_SETTINGS,
        SPEAKER_SETTINGS,
        ADD_PERIPHERAL,
        PLAYER_ACTIVATION_RANGE,
        WORLD_TRANSFORM,
        CHANNEL_MENU,
        REMOTE_MENU,
        PUBLIC_CHANNEL_LIST,
        PUBLIC_CHANNEL_CREATE,
        PUBLIC_CHANNEL_MANAGE
    }

    // ─────────────────────────────────────────────────────────
    //  Inventory holder (identifies GUI inventories)
    // ─────────────────────────────────────────────────────────

    public static class MtvHolder implements InventoryHolder {
        private final GuiType type;
        private final UUID entityUuid;
        private final String peripheralId;

        public MtvHolder(GuiType type, UUID entityUuid, String peripheralId) {
            this.type = type;
            this.entityUuid = entityUuid;
            this.peripheralId = peripheralId;
        }

        public GuiType getType()               { return type; }
        public UUID getEntityUuid()             { return entityUuid; }
        public String getPeripheralId()         { return peripheralId; }
        @Override public Inventory getInventory() { throw new UnsupportedOperationException(); }
    }

    // ─────────────────────────────────────────────────────────
    //  Constants & slot definitions
    // ─────────────────────────────────────────────────────────

    public static final double NEARBY_RANGE = 50.0;
    public static final String MEDIA_INPUT_HINT  = "请输入支持的播放链接";
    public static final String MEDIA_INPUT_MESSAGE = MEDIA_INPUT_HINT + "。";

    // State keys (stored in PageEntry.state)
    public static final String AWAITING_KEY                 = "awaiting";
    public static final String AWAITING_CREATE_NAME         = "create_name";
    public static final String AWAITING_PUBLIC_CHANNEL_SEARCH = "public_channel_search";
    public static final String NEARBY_PAGE_KEY              = "nearby_page";
    public static final String NEARBY_SLOT_UUID_PREFIX      = "nearby_slot_";
    public static final String PUBLIC_QUERY_KEY             = "public_query";
    public static final String PUBLIC_PAGE_KEY              = "public_page";
    public static final String PUBLIC_OWN_ONLY_KEY          = "public_own_only";

    // Slot arrays
    public static final int[] CHANNEL_PLAYLIST_SLOTS = {
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43 };

    public static final int[] PUBLIC_CHANNEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34 };

    // ─────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────

    private final JavaPlugin plugin;
    private final MtvPlayerManager manager;
    private final MtvPeripheralController controller;
    private final MtvPlaybackController playbackController;
    private final MtvPlayerSelector selector;
    private final GuiPageContext pageContext;
    private final Map<GuiType, GuiPage> pages = new LinkedHashMap<>();
    private final Map<UUID, NavigationState> playerNavs = new ConcurrentHashMap<>();
    private volatile boolean closed;

    // ─────────────────────────────────────────────────────────
    //  Construction
    // ─────────────────────────────────────────────────────────

    public MtvGui(JavaPlugin plugin, MtvPlayerManager manager,
                  MtvPeripheralController controller,
                  MtvPlaybackController playbackController,
                  MtvPlayerSelector selector) {
        this.plugin = plugin;
        this.manager = manager;
        this.controller = controller;
        this.playbackController = playbackController;
        this.selector = selector;
        this.pageContext = new GuiPageContext(this);

        registerPage(new MainMenuPage());
        registerPage(new NearbyPlayerListPage());
        registerPage(new PublicChannelListPage());
        registerPage(new PlayerMenuPage());
        registerPage(new PeripheralListPage());
        registerPage(new ScreenSettingsPage());
        registerPage(new SpeakerSettingsPage());
        registerPage(new AddPeripheralPage());
        registerPage(new PlayerActivationRangePage());
        registerPage(new WorldTransformPage());
        registerPage(new ChannelMenuPage());
        registerPage(new RemoteMenuPage());
        registerPage(new PublicChannelManagePage());
        registerPage(new PublicChannelCreatePage());
    }

    // ─────────────────────────────────────────────────────────
    //  Navigation
    // ─────────────────────────────────────────────────────────

    /** Navigate to a page, pushing the current one to history. */
    public void navigateTo(Player player, GuiType type,
                           UUID entityUuid, String peripheralId,
                           Map<String, String> state) {
        GuiPage page = pages.get(type);
        if (page == null) return;
        page.open(player, pageContext, new PageEntry(type, entityUuid, peripheralId, state));
    }

    /** Restore a previous navigation entry (used by back/forward). */
    public void restoreNavigation(Player player, PageEntry entry) {
        GuiPage page = pages.get(entry.getType());
        if (page == null) return;
        NavigationState nav = getOrCreateNavigation(player);
        page.renderFromEntry(player, pageContext, nav, entry);
    }

    /** Re-render the current page without pushing history (refresh). */
    public void refreshCurrentPage(Player player) {
        NavigationState nav = getNavigation(player);
        if (nav == null || nav.getCurrent() == null) return;
        PageEntry entry = nav.getCurrent();
        GuiPage page = pages.get(entry.getType());
        if (page != null) {
            page.renderFromEntry(player, pageContext, nav, entry);
        }
    }

    /** Get or create the navigation state for a player. */
    public NavigationState getOrCreateNavigation(Player player) {
        return playerNavs.computeIfAbsent(player.getUniqueId(),
                k -> NavigationState.fromEntry(new PageEntry(GuiType.MAIN_MENU)));
    }

    public NavigationState getNavigation(Player player) {
        return playerNavs.get(player.getUniqueId());
    }

    public GuiPageContext getPageContext() {
        return pageContext;
    }

    // ─────────────────────────────────────────────────────────
    //  Click dispatch
    // ─────────────────────────────────────────────────────────

    public void dispatchClick(Player player, MtvHolder holder, int slot,
                              boolean rightClick, boolean shiftClick) {
        if (closed) return;
        GuiPage page = pages.get(holder.getType());
        if (page != null) {
            page.handleClick(player, pageContext, slot, rightClick, shiftClick);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Chat input handling
    // ─────────────────────────────────────────────────────────

    /**
     * Process a player's chat message while awaiting input.
     * Delegates to the current page's {@link GuiPage#handleChatInput}.
     *
     * @return true if the message was consumed by the GUI system
     */
    public boolean handleChatInput(Player player, String message) {
        if (closed) return false;
        NavigationState nav = getNavigation(player);
        if (nav == null || nav.getCurrent() == null) return false;
        PageEntry entry = nav.getCurrent();

        String awaiting = entry.getState().get(AWAITING_KEY);
        if (awaiting == null) return false;

        // First, let the current page handle it (key still present for the page to read)
        GuiPage page = pages.get(entry.getType());
        if (page != null && page.handleChatInput(player, pageContext, entry, message)) {
            entry.getState().remove(AWAITING_KEY);
            return true;
        }

        // Not handled by page — consume key and discard (unknown awaiting type)
        entry.getState().remove(AWAITING_KEY);
        return true;
    }

    /** Check if the player is currently awaiting chat input on the current page. */
    public boolean isAwaitingInput(Player player) {
        NavigationState nav = getNavigation(player);
        return nav != null && nav.getCurrent() != null
                && nav.getCurrent().getState().containsKey(AWAITING_KEY);
    }

    /** Mark the player as awaiting chat input (stored in the current entry state). */
    public void setAwaitingInput(Player player, String kind) {
        NavigationState nav = getNavigation(player);
        if (nav != null && nav.getCurrent() != null) {
            nav.getCurrent().getState().put(AWAITING_KEY, kind);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Shutdown
    // ─────────────────────────────────────────────────────────

    public void shutdown() {
        if (closed) return;
        closed = true;
        playerNavs.clear();
        if (!plugin.isEnabled()) return;
        for (var player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                var holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof MtvHolder) {
                    player.closeInventory();
                }
            }, null);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Scheduler helpers
    // ─────────────────────────────────────────────────────────

    void runOnPlayerTask(Player player, Runnable task) {
        if (closed || !plugin.isEnabled()) return;
        player.getScheduler().run(plugin, scheduledTask -> {
            if (closed || !plugin.isEnabled()) return;
            task.run();
        }, null);
    }

    // ─────────────────────────────────────────────────────────
    //  Static utility methods (shared across pages)
    // ─────────────────────────────────────────────────────────

    /** Create a state map for public-channel list navigation. */
    public static Map<String, String> publicChannelState(String query, int page, boolean ownOnly) {
        var st = new java.util.HashMap<String, String>();
        st.put(PUBLIC_QUERY_KEY, query);
        st.put(PUBLIC_PAGE_KEY, Integer.toString(page));
        st.put(PUBLIC_OWN_ONLY_KEY, Boolean.toString(ownOnly));
        return st;
    }

    // --- State parsing helpers ---

    public static int parsePage(PageEntry entry) { return parsePage(entry != null ? entry.getState() : null); }
    public static int parsePage(Map<String, String> state) {
        try { return Integer.parseInt(state != null ? state.getOrDefault(PUBLIC_PAGE_KEY, "0") : "0"); }
        catch (NumberFormatException e) { return 0; }
    }

    public static boolean isPublicOwnOnly(PageEntry entry) {
        return entry != null && Boolean.parseBoolean(
                entry.getState().getOrDefault(PUBLIC_OWN_ONLY_KEY, "false"));
    }

    public static int getNearbyPage(PageEntry entry) {
        return getStateInt(entry, NEARBY_PAGE_KEY);
    }

    public static UUID getNearbySlotUuid(PageEntry entry, int slot) {
        if (entry == null) return null;
        String value = entry.getState().get(NEARBY_SLOT_UUID_PREFIX + slot);
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static int getStateInt(PageEntry entry, String key) {
        if (entry == null) return 0;
        try { return Integer.parseInt(entry.getState().getOrDefault(key, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    // --- Text formatting helpers ---

    public static String summarizePublicChannelName(ChannelRuntimeState state) {
        return summarize(fallback(state.getChannelName(), state.getChannelId()));
    }

    public static String fallback(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public static String summarize(String text) {
        if (text == null || text.isBlank()) return "未设置";
        return text.length() > 28 ? text.substring(0, 25) + "..." : text;
    }

    public static String formatDurationUs(long us) {
        long totalSec = us / 1_000_000;
        long hours = totalSec / 3600, minutes = (totalSec % 3600) / 60, seconds = totalSec % 60;
        if (hours > 0) return String.format("%d:%02d:%02d", hours, minutes, seconds);
        return String.format("%d:%02d", minutes, seconds);
    }

    public static String formatPlayOrderMode(ChannelRuntimeState state) {
        return switch (state.getPlayOrderMode()) {
            case SEQUENTIAL   -> "列表播放";
            case SHUFFLE      -> "随机";
            case LOOP_ALL     -> "列表循环";
            case LOOP_ONE     -> "当前媒体循环";
            case CURRENT_ONLY -> "播完当前停止";
        };
    }

    public static String currentPlaylistLabel(ChannelRuntimeState state) {
        if (state.getPlaylist().isEmpty()) return "空列表";
        int cursor = state.getNormalizedPlaylistCursor();
        return "#" + (cursor + 1) + " " + summarize(state.getPlaylist().get(cursor).mediaUrl());
    }

    // ─────────────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────────────

    public JavaPlugin getPlugin()                  { return plugin; }
    public MtvPlayerManager getManager()           { return manager; }
    public MtvPeripheralController getController() { return controller; }
    public MtvPlaybackController getPlaybackController() { return playbackController; }
    public MtvPlayerSelector getSelector()         { return selector; }
    public boolean isClosed()                      { return closed; }

    // ─────────────────────────────────────────────────────────
    //  Internal
    // ─────────────────────────────────────────────────────────

    private void registerPage(GuiPage page) {
        pages.put(page.type(), page);
    }
}
