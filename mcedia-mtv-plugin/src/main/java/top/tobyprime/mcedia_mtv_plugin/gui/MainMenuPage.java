package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;

public class MainMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.MAIN_MENU; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("MTV"); }

    @Override
    public Material icon() { return Material.COMPASS; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        var inv = createInventory(entry);
        inv.setItem(10, item(Material.ENDER_PEARL, "控制最近的播放器", "按当前距离最近的 MTV 打开控制页"));
        inv.setItem(11, item(Material.COMPASS, "附近的播放器", "分页查看附近 MTV 并选择控制"));
        inv.setItem(12, item(Material.BOOK, "频道列表", "浏览全部公共频道"));
        inv.setItem(13, item(Material.ITEM_FRAME, "创建播放器", "输入名称后在你当前位置创建 MTV 播放器"));
        inv.setItem(14, item(Material.PLAYER_HEAD, "我的频道", "只查看我创建的公共频道"));
        inv.setItem(15, item(Material.JUKEBOX, "频道控制", "打开公共频道列表并选择要控制的频道"));
        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 10 -> {
                context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                        results -> context.runOnPlayer(player, () -> {
                            if (results.isEmpty()) {
                                player.sendMessage("附近 " + (int) MtvGui.NEARBY_RANGE + " 米内没有 MTV 播放器。");
                                return;
                            }
                            context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, results.get(0).getUuid());
                        }));
            }
            case 11 -> {
                var st = context.newState();
                st.put(MtvGui.NEARBY_PAGE_KEY, "0");
                context.navigateTo(player, MtvGui.GuiType.NEARBY_PLAYER_LIST, null, null, st);
            }
            case 12 -> context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, null);
            case 13 -> {
                if (!player.hasPermission("mcedia.mtv.create")) {
                    player.sendMessage("你没有权限执行此操作。需要权限: mcedia.mtv.create");
                    return true;
                }
                context.requestInput(player, "请输入新 MTV 播放器名称。", MtvGui.AWAITING_CREATE_NAME);
            }
            case 14 -> {
                var st = context.newState();
                st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, "true");
                st.put(MtvGui.PUBLIC_QUERY_KEY, "");
                st.put(MtvGui.PUBLIC_PAGE_KEY, "0");
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, null, null, st);
            }
            case 15 -> {
                var st = context.newState();
                st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, "false");
                st.put(MtvGui.PUBLIC_QUERY_KEY, "");
                st.put(MtvGui.PUBLIC_PAGE_KEY, "0");
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, null, null, st);
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (!MtvGui.AWAITING_CREATE_NAME.equals(awaiting)) return false;
        String input = message.trim();
        if (input.isBlank()) {
            context.runOnPlayer(player, () -> player.sendMessage("名称不能为空。"));
            return true;
        }
        if (!player.hasPermission("mcedia.mtv.create")) {
            context.runOnPlayer(player, () -> player.sendMessage("你没有权限执行此操作。需要权限: mcedia.mtv.create"));
            return true;
        }
        context.manager().createPlayerAsync(player.getLocation(), input,
                created -> context.runOnPlayer(player, () -> {
                    if (created == null) {
                        player.sendMessage("创建 MTV 播放器失败。");
                        return;
                    }
                    player.sendMessage("已创建 MTV 播放器: " + created.getName());
                    context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, created.getUuid());
                }));
        return true;
    }
}
