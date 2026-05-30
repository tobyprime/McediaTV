package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

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

        // ── 左侧：播放器 ──
        inv.setItem(20, item(Material.ENDER_PEARL, "控制最近的播放器",
                "按当前距离最近的 MTV 打开控制页"));
        inv.setItem(29, item(Material.COMPASS, "附近的播放器",
                "分页查看附近 MTV 并选择控制"));
        inv.setItem(38, item(Material.CRAFTING_TABLE, "创建播放器",
                "输入名称后在你当前位置创建 MTV 播放器"));

        // ── 中间：视觉分隔 ──
        inv.setItem(22, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(31, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(40, item(Material.GRAY_STAINED_GLASS_PANE, " "));

        // ── 右侧：频道 ──
        inv.setItem(24, item(Material.JUKEBOX, "频道控制",
                "直接控制最近播放器当前绑定的频道"));
        inv.setItem(33, item(Material.BOOK, "频道列表",
                "浏览全部公共频道"));
        inv.setItem(42, item(Material.PLAYER_HEAD, "我的频道",
                "只查看我创建的公共频道"));

        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        switch (slot) {
            // ── 播放器操作 ──
            case 20 -> {
                context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                        results -> context.runOnPlayer(player, () -> {
                            if (results.isEmpty()) {
                                player.sendMessage("附近 " + (int) MtvGui.NEARBY_RANGE + " 米内没有 MTV 播放器。");
                                return;
                            }
                            context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, results.get(0).getUuid());
                        }));
            }
            case 29 -> {
                var st = context.newState();
                st.put(MtvGui.NEARBY_PAGE_KEY, "0");
                context.navigateTo(player, MtvGui.GuiType.NEARBY_PLAYER_LIST, null, null, st);
            }
            case 38 -> {
                if (!player.hasPermission("mtv.player.create")) {
                    player.sendMessage("你没有权限执行此操作。需要权限: mtv.player.create");
                    return true;
                }
                context.requestInput(player, "请输入新 MTV 播放器名称。", MtvGui.AWAITING_CREATE_NAME);
            }

            // ── 频道操作 ──
            case 24 -> {
                context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                        results -> context.runOnPlayer(player, () -> {
                            if (results.isEmpty()) {
                                player.sendMessage("附近 " + (int) MtvGui.NEARBY_RANGE + " 米内没有 MTV 播放器。");
                                return;
                            }
                            context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, results.get(0).getUuid());
                        }));
            }
            case 33 -> context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, null);
            case 42 -> {
                var st = context.newState();
                st.put(MtvGui.PUBLIC_OWN_ONLY_KEY, "true");
                st.put(MtvGui.PUBLIC_QUERY_KEY, "");
                st.put(MtvGui.PUBLIC_PAGE_KEY, "0");
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, null, null, st);
            }

            // 中间分隔列 — 无操作
            case 22, 31, 40 -> { return true; }
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
        if (!player.hasPermission("mtv.player.create")) {
            context.runOnPlayer(player, () -> player.sendMessage("你没有权限执行此操作。需要权限: mtv.player.create"));
            return true;
        }
        context.manager().createPlayerAsync(player.getLocation(), input, player,
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
