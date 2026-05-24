package top.tobyprime.mcedia_mtv_plugin.listener;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

public class MtvInteractionListener implements Listener {
    private final MtvGui gui;
    private final MtvPlayerManager manager;

    public MtvInteractionListener(MtvGui gui, MtvPlayerManager manager) {
        this.gui = gui;
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemDisplay itemDisplay)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (manager.isManagedItemDisplay(itemDisplay)) {
            event.setCancelled(true);
            var snapshot = manager.readFromEntity(itemDisplay);
            gui.openPlayerMenu(player, snapshot);
        }
    }
}
