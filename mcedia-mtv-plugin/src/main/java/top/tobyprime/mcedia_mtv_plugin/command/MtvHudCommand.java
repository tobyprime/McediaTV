package top.tobyprime.mcedia_mtv_plugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.channel.HudBindingService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class MtvHudCommand implements CommandExecutor, TabCompleter {
    private final HudBindingService hudBinding;

    public MtvHudCommand(HudBindingService hudBinding) {
        this.hudBinding = hudBinding;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以执行此命令。");
            return true;
        }

        if (args.length == 0) {
            showStatus(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "subscribe" -> handleSubscribe(player, args);
            case "unsubscribe" -> handleUnsubscribe(player);
            case "status" -> { showStatus(player); yield true; }
            default -> { sendHelp(player); yield true; }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("subscribe", "unsubscribe", "status")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private boolean handleSubscribe(Player player, String[] args) {
        if (!player.hasPermission("mtv.hud.subscribe")) {
            player.sendMessage("你没有权限执行此操作。");
            return true;
        }
        if (args.length < 2 || args[1].isBlank()) {
            player.sendMessage("用法: /mtvhud subscribe <channelId>");
            return true;
        }
        hudBinding.subscribe(player, args[1]);
        player.sendMessage("HUD 已订阅频道: " + args[1]);
        return true;
    }

    private boolean handleUnsubscribe(Player player) {
        if (!player.hasPermission("mtv.hud.unsubscribe")) {
            player.sendMessage("你没有权限执行此操作。");
            return true;
        }
        hudBinding.unsubscribe(player);
        player.sendMessage("HUD 已取消订阅。");
        return true;
    }

    private void showStatus(Player player) {
        var channelId = hudBinding.getBinding(player);
        if (channelId != null) {
            player.sendMessage("HUD 频道: " + channelId);
        } else {
            player.sendMessage("HUD 当前未订阅任何频道。");
        }
    }

    private void sendHelp(Player sender) {
        sender.sendMessage("/mtvhud status - 查看当前 HUD 频道");
        sender.sendMessage("/mtvhud subscribe <channelId> - 订阅频道");
        sender.sendMessage("/mtvhud unsubscribe - 取消订阅");
    }
}
