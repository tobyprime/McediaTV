package top.tobyprime.mcedia_mtv_plugin.channel;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
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
        var player = event.getPlayer();
        if (player.getListeningPluginChannels().contains(MtvChannelProtocol.CHANNEL_HUD_BINDING)) {
            LOGGER.info("HUD binding channel already registered at join: player={}", player.getName());
            sendBinding(player, "player_join");
            return;
        }
        LOGGER.info("HUD binding deferred until client channel registration: player={}", player.getName());
    }

    @EventHandler
    public void onPlayerRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!isHudBindingChannel(event.getChannel())) {
            return;
        }
        LOGGER.info("HUD binding client channel registered: player={}, channel={}; resending persisted binding",
                event.getPlayer().getName(), event.getChannel());
        sendBinding(event.getPlayer(), "client_channel_register");
    }

    public void sendBinding(Player player) {
        sendBinding(player, "manual_send");
    }

    private void sendBinding(Player player, String trigger) {
        if (player == null || !player.isOnline()) {
            LOGGER.debug("Skip HUD binding send: trigger={}, reason=player-offline", trigger);
            return;
        }
        var pdc = player.getPersistentDataContainer();
        String channelId = pdc.get(pdcKey, PersistentDataType.STRING);
        sendPayload(player, channelId, trigger);
    }

    public void subscribe(Player player, String channelId) {
        if (player == null || channelId == null || channelId.isBlank()) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        pdc.set(pdcKey, PersistentDataType.STRING, channelId);
        sendPayload(player, channelId, "subscribe");
        LOGGER.info("HUD subscribe: player={}, channel={}", player.getName(), channelId);
    }

    public void unsubscribe(Player player) {
        if (player == null) {
            return;
        }
        var pdc = player.getPersistentDataContainer();
        pdc.remove(pdcKey);
        sendPayload(player, "", "unsubscribe");
        LOGGER.info("HUD unsubscribe: player={}", player.getName());
    }

    public String getBinding(Player player) {
        if (player == null) {
            return null;
        }
        var pdc = player.getPersistentDataContainer();
        return pdc.get(pdcKey, PersistentDataType.STRING);
    }

    static boolean isHudBindingChannel(String channel) {
        return MtvChannelProtocol.CHANNEL_HUD_BINDING.equals(channel);
    }

    private void sendPayload(Player player, String channelId, String trigger) {
        String resolvedChannelId = channelId == null ? "" : channelId;
        try {
            player.sendPluginMessage(plugin, MtvChannelProtocol.CHANNEL_HUD_BINDING,
                    MtvChannelProtocol.encodeHudBinding(resolvedChannelId));
            LOGGER.info("Sent HUD binding: player={}, channel={}, trigger={}",
                    player.getName(), resolvedChannelId.isBlank() ? "<none>" : resolvedChannelId, trigger);
        } catch (Exception e) {
            LOGGER.warn("Failed to send HUD binding: player={}, channel={}, trigger={}",
                    player.getName(), resolvedChannelId.isBlank() ? "<none>" : resolvedChannelId, trigger, e);
        }
    }
}
