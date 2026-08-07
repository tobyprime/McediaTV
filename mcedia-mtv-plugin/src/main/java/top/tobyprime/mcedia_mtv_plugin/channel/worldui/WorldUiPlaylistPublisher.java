package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelService;

public final class WorldUiPlaylistPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldUiPlaylistPublisher.class);

    private final Plugin plugin;
    private final MtvChannelService channelService;

    public WorldUiPlaylistPublisher(Plugin plugin, MtvChannelService channelService) {
        this.plugin = plugin;
        this.channelService = channelService;
    }

    public void publishManifest(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }
        ChannelRuntimeState state = channelService.getChannelState(channelId);
        if (state == null) {
            return;
        }
        byte[] message = MtvChannelProtocol.encodePlaylistManifest(toManifest(state));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), channelId)) {
                send(player, MtvChannelProtocol.CHANNEL_PLAYLIST_MANIFEST, message, "playlist manifest");
            }
        }
    }

    public void publishPage(Player player, String channelId, int offset) {
        if (player == null || channelId == null || channelId.isBlank()) {
            return;
        }
        ChannelRuntimeState state = channelService.ensureChannelState(channelId);
        if (state == null || !channelService.getAudienceSessionManager().isSubscribed(player.getUniqueId(), channelId)) {
            return;
        }
        byte[] message = MtvChannelProtocol.encodePlaylistPage(PlaylistPageEncoder.encodePage(state, offset));
        send(player, MtvChannelProtocol.CHANNEL_PLAYLIST_PAGE, message, "playlist page");
    }

    private WorldUiPlaylistManifest toManifest(ChannelRuntimeState state) {
        return new WorldUiPlaylistManifest(
                state.getChannelId(),
                state.getRevision(),
                state.getPlaylist().size(),
                state.getNormalizedPlaylistCursor(),
                state.getPlayOrderMode().name()
        );
    }

    private void send(Player player, String channel, byte[] message, String action) {
        player.getScheduler().run(plugin, task -> {
            try {
                player.sendPluginMessage(plugin, channel, message);
            } catch (Exception e) {
                LOGGER.warn("Failed to send MTV {}: player={}, channel={}", action, player.getName(), channel, e);
            }
        }, null);
    }
}
