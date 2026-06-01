package top.tobyprime.mcedia_mtv_plugin.controller;

import org.bukkit.Bukkit;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelService;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class MtvPlaybackController {
    private final MtvPlayerManager manager;
    private final MtvChannelService channelService;

    public MtvPlaybackController(MtvPlayerManager manager) {
        this.manager = manager;
        this.channelService = manager.getChannelService();
    }

    public void updateMediaUrl(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.updateMediaUrl(channelId, mediaUrl), done);
    }

    public void updateMediaUrlAsCurrentOnly(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.updateMediaUrlAsCurrentOnly(channelId, mediaUrl), done);
    }

    public void updateSpeed(UUID uuid, float speed, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.updateSpeed(channelId, speed), done);
    }

    public void updateStartAt(UUID uuid, long startAt, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.updateStartAt(channelId, startAt), done);
    }

    public void seekRelative(UUID uuid, long deltaUs, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.seekRelative(channelId, deltaUs), done);
    }

    public void togglePause(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.togglePause(channelId), done);
    }

    public void playPlaylistIndex(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.playPlaylistIndex(channelId, index), done);
    }

    public void playNextManual(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.playNextManual(channelId), done);
    }

    public void playPreviousManual(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.playPreviousManual(channelId), done);
    }

    public void appendPlaylistItem(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.appendPlaylistItem(channelId, mediaUrl), done);
    }

    public void insertNextPlaylistItem(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.insertNextPlaylistItem(channelId, mediaUrl), done);
    }

    public void insertNextAndPlay(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.insertNextAndPlay(channelId, mediaUrl), done);
    }

    public void prependPlaylistItem(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.prependPlaylistItem(channelId, mediaUrl), done);
    }

    public void removePlaylistItem(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.removePlaylistItem(channelId, index), done);
    }

    public void movePlaylistItemToFront(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.movePlaylistItemToFront(channelId, index), done);
    }

    public void movePlaylistItemToBack(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.movePlaylistItemToBack(channelId, index), done);
    }

    public void cyclePlayOrderMode(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelId -> channelService.cyclePlayOrderMode(channelId), done);
    }

    public void clearPlaylist(UUID uuid, Consumer<Boolean> done) {
        var bukkitPlayer = Bukkit.getPlayer(uuid);
        manager.withManagedPlayer(uuid, player -> {
            var binding = channelService.resolveBinding(player);
            return channelService.clearPlaylist(bukkitPlayer, binding.channelId());
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }

    public void canControlPlayback(UUID entityUuid, org.bukkit.entity.Player player, Consumer<Boolean> done) {
        manager.withManagedPlayer(entityUuid, managed -> {
            var binding = channelService.resolveBinding(managed);
            var state = channelService.ensureChannelState(binding.channelId());
            return channelService.canControlChannelPlayback(player, state);
        }, done);
    }

    private void updatePlayback(UUID uuid, Function<String, Boolean> operation, Consumer<Boolean> done) {
        manager.withManagedPlayer(uuid, player -> {
            var binding = channelService.resolveBinding(player);
            return operation.apply(binding.channelId());
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }
}
