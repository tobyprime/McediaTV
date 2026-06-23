package top.tobyprime.mcedia_mtv_plugin.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;

import java.util.List;
import java.util.Locale;

/**
 * `/mtv` 用于打开遥控器界面。
 */
public class MtvCommand implements CommandExecutor, TabCompleter {
    private final MtvGui gui;

    public MtvCommand(MtvGui gui) {
        this.gui = gui;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/mtv - 打开遥控器界面");
        sender.sendMessage("/mtv control - 打开遥控器界面");
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
        if (args.length == 0 || "control".equalsIgnoreCase(args[0])) {
            openControl(sender);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("control")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private void openControl(CommandSender sender) {
        if (!hasPermission(sender, "mtv.gui")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以打开遥控器界面。");
            return;
        }
        gui.navigateTo(player, MtvGui.GuiType.REMOTE_MENU, null, null, null);
    }
}
