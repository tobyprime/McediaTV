package top.tobyprime.mcedia_mtv_plugin.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

import java.util.List;
import java.util.Locale;

/**
 * GUI-first MTV command entry.
 * `/mtv` 与 `/mtv gui` 用于打开主菜单。
 */
public class MtvCommand implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvCommand.class);

    private final MtvPlayerManager manager;
    private final MtvGui gui;

    public MtvCommand(MtvPlayerManager manager, MtvPeripheralController controller, MtvPlaybackController playbackController, MtvGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/mtv - 打开 MTV 主菜单");
        sender.sendMessage("/mtv gui - 打开 MTV 主菜单");
        sender.sendMessage("/mtv create <name> - 在自己位置创建 MTV 播放器");
        sender.sendMessage("player 与 channel 的控制已改为通过 GUI 完成。请使用 /mtv 进入界面操作。");
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("你没有权限执行此操作。需要权限: " + permission);
        return false;
    }


    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 0) {
            openMainMenu(sender);
            return true;
        }
        if (args.length == 1) {
            if ("gui".equalsIgnoreCase(args[0])) {
                openGui(sender);
                return true;
            }
            if ("help".equalsIgnoreCase(args[0])) {
                sendHelp(sender);
                return true;
            }
        }
        if (args.length >= 2 && "create".equalsIgnoreCase(args[0])) {
            createPlayer(sender, String.join(" ", args).substring("create".length()).trim());
            return true;
        }
        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("gui", "help", "create")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private void openMainMenu(CommandSender sender) {
        if (sender instanceof Player player) {
            gui.navigateTo(player, MtvGui.GuiType.MAIN_MENU, null, null, null);
            return;
        }
        sendHelp(sender);
    }

    private void openGui(CommandSender sender) {
        if (!hasPermission(sender, "mtv.gui")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以打开 MTV GUI。");
            return;
        }
        gui.navigateTo(player, MtvGui.GuiType.MAIN_MENU, null, null, null);
    }

    private void createPlayer(CommandSender sender, String name) {
        if (!hasPermission(sender, "mtv.player.create")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以创建 MTV 播放器。");
            return;
        }
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isBlank()) {
            sender.sendMessage("名称不能为空。");
            return;
        }
        manager.createPlayerAsync(player.getLocation(), trimmedName, player, created -> player.getScheduler().run(gui.getPlugin(), task -> {
            if (created == null) {
                sender.sendMessage("创建 MTV 播放器失败。");
                return;
            }
            sender.sendMessage("已创建 MTV 播放器: " + created.getName());
            gui.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, created.getUuid(), null, null);
        }, null));
    }
}
