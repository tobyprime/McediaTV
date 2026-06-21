package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class HudMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.HUD_MENU; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("HUD 小窗"); }

    @Override
    public Material icon() { return Material.ENDER_EYE; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        var inv = createInventory(entry);

        var hud = context.hudBinding();
        var currentChannel = hud.getBinding(player);

        if (currentChannel != null) {
            inv.setItem(22, item(Material.JUKEBOX, "频道: " + currentChannel,
                    "当前 HUD 订阅的频道",
                    "点击取消订阅"));
        } else {
            inv.setItem(22, item(Material.BARRIER, "未订阅",
                    "HUD 当前未绑定任何频道"));
        }

        inv.setItem(29, item(Material.WRITABLE_BOOK, "订阅频道",
                "输入频道 ID 来订阅 HUD 小窗"));
        inv.setItem(33, item(Material.REDSTONE, "取消订阅",
                "停止 HUD 播放"));

        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 22, 33 -> {
                if (!player.hasPermission("mtv.hud.unsubscribe")) {
                    player.sendMessage("你没有权限执行此操作。");
                    return true;
                }
                context.hudBinding().unsubscribe(player);
                player.sendMessage("HUD 已取消订阅。");
                context.refresh(player);
            }
            case 29 -> {
                if (!player.hasPermission("mtv.hud.subscribe")) {
                    player.sendMessage("你没有权限执行此操作。");
                    return true;
                }
                context.requestInput(player, "请输入要订阅的频道 ID。", "hud_subscribe");
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (!"hud_subscribe".equals(awaiting)) return false;

        String input = message.trim();
        if (input.isBlank()) {
            context.runOnPlayer(player, () -> player.sendMessage("频道 ID 不能为空。"));
            return true;
        }
        context.hudBinding().subscribe(player, input);
        context.runOnPlayer(player, () -> {
            player.sendMessage("HUD 已订阅频道: " + input);
            context.navigateTo(player, MtvGui.GuiType.HUD_MENU);
        });
        return true;
    }
}
