package top.tobyprime.mcedia_mtv_plugin.channel;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HudBindingService implements Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(HudBindingService.class);
    private static final String PDC_KEY = "hud_channel";

    private final Plugin plugin;
    private final NamespacedKey pdcKey;

    public HudBindingService(Plugin plugin) {
        this.plugin = plugin;
        this.pdcKey = new NamespacedKey(plugin, PDC_KEY);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendBinding(event.getPlayer());
    }

    public void sendBinding(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        String channelId = pdc.get(pdcKey, PersistentDataType.STRING);
        sendPayload(player, channelId);
    }

    public void subscribe(Player player, String channelId) {
        if (player == null || channelId == null || channelId.isBlank()) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.STRING, channelId);
        sendPayload(player, channelId);
        LOGGER.info("HUD subscribe: player={}, channel={}", player.getName(), channelId);
    }

    public void unsubscribe(Player player) {
        if (player == null) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        pdc.remove(pdcKey);
        sendPayload(player, "");
        LOGGER.info("HUD unsubscribe: player={}", player.getName());
    }

    public String getBinding(Player player) {
        if (player == null) {
            return null;
        }
        var pdc = player.getPersistentDataContainer();
        return pdc.get(pdcKey, PersistentDataType.STRING);
    }

    private void sendPayload(Player player, String channelId) {
        player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_HUD_BINDING,
                MtvChannelProtocol.encodeHudBinding(channelId));
    }
}
