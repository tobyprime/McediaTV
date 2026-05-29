package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class NearbyPlayerListPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.NEARBY_PLAYER_LIST; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("附近的播放器"); }

    @Override
    public Material icon() { return Material.COMPASS; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        int requestedPage = MtvGui.getNearbyPage(entry);
        context.selector().findNearbyAsync(player, MtvGui.NEARBY_RANGE,
                results -> context.runOnPlayer(player, () -> {
                    int totalPages = Math.max(1, (results.size() + MtvGui.PUBLIC_CHANNEL_SLOTS.length - 1)
                            / MtvGui.PUBLIC_CHANNEL_SLOTS.length);
                    int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

                    var inv = createInventory(entry);
                    inv.setItem(45, item(Material.ARROW, "上一页"));
                    inv.setItem(49, item(Material.SPYGLASS, "刷新"));
                    inv.setItem(50, item(Material.ARROW, "返回主菜单"));
                    inv.setItem(53, item(Material.ARROW, "下一页"));

                    var origin = player.getLocation();
                    int start = page * MtvGui.PUBLIC_CHANNEL_SLOTS.length;
                    for (int i = 0; i < MtvGui.PUBLIC_CHANNEL_SLOTS.length && start + i < results.size(); i++) {
                        int slot = MtvGui.PUBLIC_CHANNEL_SLOTS[i];
                        var snapshot = results.get(start + i);
                        double dist = Math.sqrt(origin.distanceSquared(snapshot.toLocation()));
                        inv.setItem(slot, item(Material.ITEM_FRAME,
                                snapshot.getName(),
                                "距离: " + (int) dist + "m",
                                "频道: " + context.manager().getChannelService().resolveBinding(snapshot).channelId(),
                                "点击打开控制页"));
                        entry.putState(MtvGui.NEARBY_SLOT_UUID_PREFIX + slot, snapshot.getUuid().toString());
                    }
                    entry.putState(MtvGui.NEARBY_PAGE_KEY, Integer.toString(page));
                    setupTitleBar(inv, nav, entry);
                    openInventory(player, inv);
                }));
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        int page = MtvGui.getNearbyPage(entry);
        switch (slot) {
            case 45 -> {
                var st = context.newState();
                st.put(MtvGui.NEARBY_PAGE_KEY, Integer.toString(Math.max(0, page - 1)));
                context.navigateTo(player, MtvGui.GuiType.NEARBY_PLAYER_LIST, null, null, st);
            }
            case 49 -> {
                // refresh: copy current page number
                var st = context.newState();
                st.put(MtvGui.NEARBY_PAGE_KEY, Integer.toString(page));
                context.navigateTo(player, MtvGui.GuiType.NEARBY_PLAYER_LIST, null, null, st);
            }
            case 50 -> context.navigateTo(player, MtvGui.GuiType.MAIN_MENU);
            case 53 -> {
                var st = context.newState();
                st.put(MtvGui.NEARBY_PAGE_KEY, Integer.toString(page + 1));
                context.navigateTo(player, MtvGui.GuiType.NEARBY_PLAYER_LIST, null, null, st);
            }
            default -> {
                if (GuiPage.indexOf(MtvGui.PUBLIC_CHANNEL_SLOTS, slot) < 0) return false;
                UUID uuid = MtvGui.getNearbySlotUuid(entry, slot);
                if (uuid == null) return true;
                context.read(player, uuid, snap -> context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, uuid));
            }
        }
        return true;
    }

}
