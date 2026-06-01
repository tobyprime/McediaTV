package top.tobyprime.mcedia_mtv_plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;

/**
 * Listens for clicks on MTV GUI inventories and delegates to the page system.
 * All page-specific logic lives in {@link top.tobyprime.mcedia_mtv_plugin.gui.GuiPage}
 * implementations, so this class is minimal.
 */
public class MtvGuiListener implements Listener {
    private final MtvGui gui;

    public MtvGuiListener(MtvGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MtvGui.MtvHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0) return;
        if (event.getCurrentItem() == null) return;

        gui.dispatchClick(player, holder, slot, event.isRightClick(), event.isShiftClick());
    }
}
