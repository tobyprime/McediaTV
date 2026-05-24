package top.tobyprime.mcedia_mtv_plugin.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;

public class MtvChatListener implements Listener {
    private final JavaPlugin plugin;
    private final MtvGui gui;

    public MtvChatListener(JavaPlugin plugin, MtvGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        if (gui.getState(player) == null) {
            return;
        }
        event.setCancelled(true);
        player.getScheduler().run(plugin, task -> gui.handleChatInput(player, message), null);
    }
}
