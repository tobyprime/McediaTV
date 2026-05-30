package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class PeripheralListPage extends GuiPage {
    private static final int[] PERIPH_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34 };

    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PERIPHERAL_LIST; }

    @Override
    public Component getTitle(PageEntry entry) {
        String name = entry.getState("player_name", "外设");
        return Component.text("外设 - " + name);
    }

    @Override
    public Material icon() { return Material.ITEM_FRAME; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) { player.closeInventory(); return; }
        context.read(player, uuid, snapshot -> {
            entry.putState("player_name", snapshot.getName());
            var inv = createInventory(entry);

            int idx = 0;
            for (var s : snapshot.getScreens()) {
                if (idx >= PERIPH_SLOTS.length) break;
                inv.setItem(PERIPH_SLOTS[idx++], item(Material.GLOW_ITEM_FRAME,
                        "§b🖥 屏幕 §7[" + s.getId() + "]",
                        "§7尺寸: §f" + s.getWidth() + " × " + s.getHeight(),
                        "§7亮度: §f" + s.getMinBrightness(),
                        "§a点击编辑"));
            }
            for (var s : snapshot.getSpeakers()) {
                if (idx >= PERIPH_SLOTS.length) break;
                inv.setItem(PERIPH_SLOTS[idx++], item(Material.JUKEBOX,
                        "§6🔊 扬声器 §7[" + s.getId() + "]",
                        "§7音量: §f" + s.getVolume(),
                        "§7范围: §f" + s.getMaxRange(),
                        "§a点击编辑"));
            }

            inv.setItem(43, item(Material.GREEN_WOOL,
                    "§a+ 新增外设", "§7添加屏幕或扬声器到 MTV"));
            setupTitleBar(inv, nav, entry);
            openInventory(player, inv);
        });
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return false;

        if (slot == 43) {
            context.navigateTo(player, MtvGui.GuiType.ADD_PERIPHERAL, uuid);
        } else {
            int idx = GuiPage.indexOf(PERIPH_SLOTS, slot);
            if (idx < 0) return false;
            // Need snapshot to know how many screens there are
            context.read(player, uuid, snap -> {
                if (snap == null) return;
                int screenCount = snap.getScreens().size();
                if (idx < screenCount) {
                    var s = snap.getScreens().get(idx);
                    context.navigateTo(player, MtvGui.GuiType.SCREEN_SETTINGS, uuid, s.getId(), null);
                } else {
                    int speakerIdx = idx - screenCount;
                    if (speakerIdx < snap.getSpeakers().size()) {
                        var s = snap.getSpeakers().get(speakerIdx);
                        context.navigateTo(player, MtvGui.GuiType.SPEAKER_SETTINGS, uuid, s.getId(), null);
                    }
                }
            });
        }
        return true;
    }

}
