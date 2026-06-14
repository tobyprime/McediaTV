package top.tobyprime.mcedia_mtv_plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        var topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof MtvGui.MtvHolder holder)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= topInventory.getSize()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        gui.dispatchClick(player, holder, slot, event.isRightClick(), event.isShiftClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        var topInventory = event.getView().getTopInventory();
        if (!gui.isMtvInventory(topInventory)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!gui.isMtvInventory(event.getView().getTopInventory())) {
            return;
        }
        player.getScheduler().run(gui.getPlugin(), task -> {
            if (gui.isMtvInventory(player.getOpenInventory().getTopInventory())) {
                return;
            }
            if (!gui.isAwaitingInput(player)) {
                gui.clearPlayerState(player);
            }
        }, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gui.clearPlayerState(event.getPlayer());
    }
}
