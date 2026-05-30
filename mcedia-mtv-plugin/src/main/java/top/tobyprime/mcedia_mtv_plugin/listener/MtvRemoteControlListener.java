package top.tobyprime.mcedia_mtv_plugin.listener;

import org.bukkit.event.player.PlayerInteractEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;

public final class MtvRemoteControlListener implements Listener {
    private static final String REMOTE_NAME = "遥控器";

    private final MtvGui gui;

    public MtvRemoteControlListener(MtvGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isRemoteControl(event.getItem())) {
            return;
        }
        if (!MtvPeripheralController.checkPerm(event.getPlayer(), "mtv.gui")) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        gui.navigateTo(event.getPlayer(), MtvGui.GuiType.REMOTE_MENU, null, null, null);
    }

    private static boolean isRemoteControl(ItemStack stack) {
        if (stack == null || stack.getType() != Material.IRON_DOOR || !stack.hasItemMeta()) {
            return false;
        }
        var meta = stack.getItemMeta();
        var displayName = meta.displayName();
        if (displayName == null) {
            return false;
        }
        return REMOTE_NAME.equals(PlainTextComponentSerializer.plainText().serialize(displayName).trim());
    }
}
