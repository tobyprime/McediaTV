package top.tobyprime.mcedia_mtv_plugin;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
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
import top.tobyprime.mcedia_mtv_plugin.selector.MtvPlayerSelector;

public final class Mcedia_mtv_plugin extends JavaPlugin {
    private static final long AUDIENCE_TIMEOUT_MS = 30_000L;
    private static final long CHANNEL_SYNC_INTERVAL_TICKS = 100L;

    private MtvPlayerManager manager;
    private MtvGui gui;
    private MtvChannelNetworkService networkService;
    private ScheduledTask audiencePruneTask;
    private ScheduledTask channelSyncTask;

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
        var selector = new MtvPlayerSelector(this, manager);
        this.gui = new MtvGui(this, manager, controller, playbackController, selector);

        var mtvCommand = new MtvCommand(controller, playbackController, gui, selector);
        // 直接注册 Brigadier 命令节点到服务端调度器，
        // 替代 getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, …)
        // 从而支持 plugman 热卸载/重载场景。
        unregisterCommand(); // 清理可能残留的旧节点
        registerCommand(mtvCommand.buildCommandTree());

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
        // 反注册 Brigadier 命令节点，确保 plugman reload 后能重新注册
        unregisterCommand();
        if (currentManager != null) {
            currentManager.getChannelService().shutdown();
            manager = null;
        }
    }

    /**
     * 将 Brigadier 命令节点直接注册到 Minecraft 服务器命令调度器。
     * 由于 Paper 的 {@link io.papermc.paper.command.brigadier.CommandSourceStack}
     * 由 NMS 的 net.minecraft.commands.CommandSourceStack 实现，
     * 运行时通过原始类型绕开编译期泛型差异。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerCommand(LiteralCommandNode<?> node) {
        try {
            var craftServer = (CraftServer) Bukkit.getServer();
            var nmsServer = craftServer.getServer();
            var dispatcher = nmsServer.getCommands().getDispatcher();
            dispatcher.getRoot().addChild((com.mojang.brigadier.tree.CommandNode) node);
        } catch (Exception e) {
            getLogger().warning("无法注册 /mtv 命令: " + e.getMessage());
        }
    }

    /**
     * 从服务端命令调度器中移除 /mtv 命令节点。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void unregisterCommand() {
        try {
            var craftServer = (CraftServer) Bukkit.getServer();
            var nmsServer = craftServer.getServer();
            var dispatcher = nmsServer.getCommands().getDispatcher();
            dispatcher.getRoot().getChildren().removeIf(child -> child.getName().equals("mtv"));
        } catch (Exception ignored) {
            // 某些环境可能不支持
        }
    }
}
