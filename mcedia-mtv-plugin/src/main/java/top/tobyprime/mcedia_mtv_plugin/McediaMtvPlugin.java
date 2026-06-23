package top.tobyprime.mcedia_mtv_plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import top.tobyprime.mcedia_mtv_plugin.channel.HudBindingService;
import top.tobyprime.mcedia_mtv_plugin.channel.MigrationHelper;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelNetworkService;
import top.tobyprime.mcedia_mtv_plugin.channel.SqLiteChannelRepository;
import top.tobyprime.mcedia_mtv_plugin.command.MtvCommand;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvChatListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvGuiListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvRemoteControlListener;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.selector.MtvPlayerSelector;

public final class McediaMtvPlugin extends JavaPlugin {
    private static final long AUDIENCE_TIMEOUT_MS = 30_000L;
    private static final long CHANNEL_SYNC_INTERVAL_TICKS = 100L;

    private MtvPlayerManager manager;
    private MtvGui gui;
    private MtvChannelNetworkService networkService;
    private HudBindingService hudBindingService;
    private ScheduledTask audiencePruneTask;
    private ScheduledTask channelSyncTask;

    @Override
    public void onEnable() {
        this.manager = new MtvPlayerManager(this);
        var channelService = manager.getChannelService();

        // One-time migration from JSON filesystem storage to SQLite
        var repo = channelService.getRepository();
        if (repo instanceof SqLiteChannelRepository sqliteRepo) {
            MigrationHelper.migrateIfNeeded(this, sqliteRepo);
        }

        channelService.loadPersistedStates();
        channelService.getAudienceSessionManager().setActiveTimeoutMs(AUDIENCE_TIMEOUT_MS);
        this.networkService = new MtvChannelNetworkService(this, channelService);
        channelService.setChangeListener(networkService::publishSnapshot);
        channelService.setRemoveListener(networkService::invalidateChannel);

        this.hudBindingService = new HudBindingService(this);

        var controller = new MtvPeripheralController(manager);
        var playbackController = new MtvPlaybackController(manager);
        var selector = new MtvPlayerSelector(this, manager);
        this.gui = new MtvGui(this, manager, controller, playbackController, selector, hudBindingService);

        var mtvCommand = new MtvCommand(gui);
        var command = getCommand("mtv");
        if (command == null) {
            throw new IllegalStateException("未在 plugin.yml 中声明 /mtv 命令");
        }
        command.setExecutor(mtvCommand);
        command.setTabCompleter(mtvCommand);

        this.audiencePruneTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task ->
                channelService.getAudienceSessionManager().pruneExpired(System.currentTimeMillis()),
                20L, 20L);
        this.channelSyncTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task ->
                        networkService.publishPeriodicUpdates(),
                CHANNEL_SYNC_INTERVAL_TICKS, CHANNEL_SYNC_INTERVAL_TICKS);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(networkService, this);
        pluginManager.registerEvents(new MtvGuiListener(gui), this);
        pluginManager.registerEvents(new MtvRemoteControlListener(gui), this);
        pluginManager.registerEvents(new MtvChatListener(this, gui), this);
        pluginManager.registerEvents(hudBindingService, this);
    }

    @Override
    public void onDisable() {
        var currentManager = this.manager;
        if (currentManager != null) {
            currentManager.getChannelService().setChangeListener(null);
            currentManager.getChannelService().setRemoveListener(null);
        }
        if (audiencePruneTask != null) {
            audiencePruneTask.cancel();
            audiencePruneTask = null;
        }
        if (channelSyncTask != null) {
            channelSyncTask.cancel();
            channelSyncTask = null;
        }
        if (networkService != null) {
            networkService.shutdown();
            networkService = null;
        }
        if (gui != null) {
            gui.shutdown();
            gui = null;
        }
        if (currentManager != null) {
            currentManager.getChannelService().shutdown();
            // Close SQLite connection
            var repo = currentManager.getChannelService().getRepository();
            if (repo instanceof SqLiteChannelRepository sqliteRepo) {
                try {
                    sqliteRepo.close();
                } catch (Exception e) {
                    getLogger().warning("Failed to close SQLite connection: " + e.getMessage());
                }
            }
            manager = null;
        }
    }

}
