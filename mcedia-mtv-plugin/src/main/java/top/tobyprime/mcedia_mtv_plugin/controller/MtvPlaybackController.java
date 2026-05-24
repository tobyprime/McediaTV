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

    private void updatePlayback(UUID uuid,
                                Function<ManagedMtvPlayer, Boolean> operation,
                                Consumer<Boolean> done) {
        manager.withDisplaySnapshot(uuid, display -> {
            var player = manager.readFromEntity(display);
            if (!Boolean.TRUE.equals(operation.apply(player))) {
                return Boolean.FALSE;
            }
            manager.applyEntityState(display, player);
            return Boolean.TRUE;
        }, result -> done.accept(Boolean.TRUE.equals(result)));
    }
}
