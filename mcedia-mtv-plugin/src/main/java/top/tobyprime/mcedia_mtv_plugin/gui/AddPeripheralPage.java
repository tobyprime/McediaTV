package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;

import java.util.UUID;

public class AddPeripheralPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.ADD_PERIPHERAL; }

    @Override
    public Component getTitle(PageEntry entry) { return Component.text("新增外设"); }

    @Override
    public Material icon() { return Material.GREEN_WOOL; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        var inv = createInventory(entry);
        // Center the two choices
        inv.setItem(20, item(Material.MAP, "屏幕"));
        inv.setItem(24, item(Material.NOTE_BLOCK, "扬声器"));
        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return false;
        if (slot != 20 && slot != 24) {
            return false;
        }
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            if (!MtvPeripheralController.canEdit(player, snap)) {
                player.sendMessage("该 MTV 播放器为私有，只有创建者或拥有 mtv.player.edit.others 权限的玩家可以编辑。");
                return;
            }
            if (slot == 20) {
                context.manager().addScreen(uuid, screen ->
                        context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid));
            } else if (slot == 24) {
                context.manager().addSpeaker(uuid, speaker ->
                        context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid));
            }
        });
        return true;
    }
}
