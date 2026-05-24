package top.tobyprime.mcedia_mtv_plugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelNetworkService;
import top.tobyprime.mcedia_mtv_plugin.command.MtvCommand;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvChatListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvGuiListener;
import top.tobyprime.mcedia_mtv_plugin.listener.MtvInteractionListener;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

public final class Mcedia_mtv_plugin extends JavaPlugin {
    private static final long AUDIENCE_TIMEOUT_MS = 30_000L;

    private MtvPlayerManager manager;
    private MtvGui gui;

    @Override
    public void onEnable() {
        this.manager = new MtvPlayerManager(this);
        manager.getChannelService().loadPersistedStates();
        var networkService = new MtvChannelNetworkService(this, manager.getChannelService());
        manager.getChannelService().setChangeListener(networkService::publishSnapshot);
        manager.getChannelService().setRemoveListener(networkService::invalidateChannel);
        var controller = new MtvPeripheralController(manager);
        var playbackController = new MtvPlaybackController(manager);
        this.gui = new MtvGui(this, manager, controller, playbackController);

        var mtvCommand = new MtvCommand(controller, playbackController, gui);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
                commands.registrar().register(mtvCommand.buildCommandTree(), "Manage MTV interaction players"));

        getServer().getScheduler().runTaskTimer(this, () -> {
            var emptiedChannels = manager.getChannelService().getAudienceSessionManager().pruneExpired(System.currentTimeMillis(), AUDIENCE_TIMEOUT_MS);
            for (var channelId : emptiedChannels) {
                getLogger().info("MTV channel audience expired: channel=" + channelId);
            }
        }, 20L, 20L);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(networkService, this);
        pluginManager.registerEvents(new MtvGuiListener(gui, playbackController), this);
        pluginManager.registerEvents(new MtvChatListener(this, gui), this);
        pluginManager.registerEvents(new MtvInteractionListener(gui, manager), this);
    }

    @Override
    public void onDisable() {
    }
}
