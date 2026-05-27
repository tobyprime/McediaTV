package top.tobyprime.mcedia_mtv_plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelNetworkService;
import top.tobyprime.mcedia_mtv_plugin.command.MtvCommand;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvChatListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvGuiListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvRemoteControlListener;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

public final class Mcedia_mtv_plugin extends JavaPlugin {
    private static final long AUDIENCE_TIMEOUT_MS = 30_000L;

    private MtvPlayerManager manager;
    private MtvGui gui;
    private MtvChannelNetworkService networkService;
    private ScheduledTask audiencePruneTask;

    @Override
    public void onEnable() {
        this.manager = new MtvPlayerManager(this);
        var channelService = manager.getChannelService();
        channelService.loadPersistedStates();
        channelService.getAudienceSessionManager().setActiveTimeoutMs(AUDIENCE_TIMEOUT_MS);
        this.networkService = new MtvChannelNetworkService(this, channelService);
        channelService.setChangeListener(networkService::publishSnapshot);
        channelService.setRemoveListener(networkService::invalidateChannel);
        var controller = new MtvPeripheralController(manager);
        var playbackController = new MtvPlaybackController(manager);
        this.gui = new MtvGui(this, manager, controller, playbackController);

        var mtvCommand = new MtvCommand(controller, playbackController, gui);
        var pluginCommand = getCommand("mtv");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(mtvCommand);
            pluginCommand.setTabCompleter(mtvCommand);
        }

        this.audiencePruneTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task ->
                channelService.getAudienceSessionManager().pruneExpired(System.currentTimeMillis()),
                20L, 20L);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(networkService, this);
        pluginManager.registerEvents(new MtvGuiListener(gui, playbackController), this);
        pluginManager.registerEvents(new MtvRemoteControlListener(gui), this);
        pluginManager.registerEvents(new MtvChatListener(this, gui), this);
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
            manager = null;
        }
    }
}
