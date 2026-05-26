package top.tobyprime.mcedia_mtv_plugin.controller;

import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelService;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

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
        updatePlayback(uuid, player -> channelService.updateMediaUrl(player, mediaUrl), done);
    }

    public void updateMediaUrlAsCurrentOnly(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.updateMediaUrlAsCurrentOnly(player, mediaUrl), done);
    }

    public void updateSpeed(UUID uuid, float speed, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.updateSpeed(player, speed), done);
    }

    public void updateStartAt(UUID uuid, long startAt, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.updateStartAt(player, startAt), done);
    }

    public void seekRelative(UUID uuid, long deltaUs, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.seekRelative(player, deltaUs), done);
    }

    public void togglePause(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelService::togglePause, done);
    }

    public void playPlaylistIndex(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.playPlaylistIndex(player, index), done);
    }

    public void playNextManual(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelService::playNextManual, done);
    }

    public void playPreviousManual(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelService::playPreviousManual, done);
    }

    public void appendPlaylistItem(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.appendPlaylistItem(player, mediaUrl), done);
    }

    public void prependPlaylistItem(UUID uuid, String mediaUrl, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.prependPlaylistItem(player, mediaUrl), done);
    }

    public void removePlaylistItem(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.removePlaylistItem(player, index), done);
    }

    public void movePlaylistItemToFront(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.movePlaylistItemToFront(player, index), done);
    }

    public void movePlaylistItemToBack(UUID uuid, int index, Consumer<Boolean> done) {
        updatePlayback(uuid, player -> channelService.movePlaylistItemToBack(player, index), done);
    }

    public void cyclePlayOrderMode(UUID uuid, Consumer<Boolean> done) {
        updatePlayback(uuid, channelService::cyclePlayOrderMode, done);
    }

    private void updatePlayback(UUID uuid,
                                Function<ManagedMtvPlayer, Boolean> operation,
                                Consumer<Boolean> done) {
        manager.withManagedPlayer(uuid, operation, done);
    }
}
