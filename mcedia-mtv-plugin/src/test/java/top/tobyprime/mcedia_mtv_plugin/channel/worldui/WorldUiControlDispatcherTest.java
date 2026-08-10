package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaylistItem;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelType;
import top.tobyprime.mcedia_mtv_plugin.model.ControlAccess;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorldUiControlDispatcherTest {
    @Test
    void rejectsMalformedRequestBeforeItCanReachChannelState() {
        var request = new WorldUiControlRequest(UUID.randomUUID(), "screen_0", "channel", 2L, 3L,
                Float.NaN, 0.5F, WorldUiControlOperation.NEXT, WorldUiControlArgument.None.INSTANCE);

        var result = WorldUiControlDispatcher.validateRequest(request);

        assertNotNull(result);
        assertEquals(WorldUiControlError.MALFORMED_REQUEST, result.error());
    }

    @Test
    void rejectsOutOfBoundsAndWronglyTypedOperationArguments() {
        var state = new ChannelRuntimeState("channel", MtvChannelType.SELF);
        state.getPlaylist().add(new ChannelPlaylistItem("https://example.test/video"));
        state.setDurationMs(10_000L);

        assertEquals(WorldUiControlError.INVALID_ARGUMENT, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.SET_SPEED, new WorldUiControlArgument.Scalar(4.1F)), state));
        assertEquals(WorldUiControlError.INVALID_ARGUMENT, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.PLAY_INDEX, new WorldUiControlArgument.PlaylistIndex(1)), state));
        assertEquals(WorldUiControlError.INVALID_ARGUMENT, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.SEEK_ABSOLUTE, new WorldUiControlArgument.PositionUs(10_000_001L)), state));
        assertEquals(WorldUiControlError.INVALID_ARGUMENT, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.APPEND, WorldUiControlArgument.None.INSTANCE), state));
    }

    @Test
    void acceptsNormalizedUrlAndKnownPlayOrder() {
        var state = new ChannelRuntimeState("channel", MtvChannelType.SELF);

        assertEquals(WorldUiControlError.NONE, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.APPEND, new WorldUiControlArgument.MediaUrl(" https://example.test/video ")), state));
        assertEquals(WorldUiControlError.NONE, WorldUiControlDispatcher.validateArgument(
                request(WorldUiControlOperation.SET_PLAY_ORDER, new WorldUiControlArgument.PlayOrderMode("LOOP_ALL")), state));
    }

    @Test
    void selfBindingRequiresTheExistingMtvPermissionPolicy() {
        var target = new ManagedMtvPlayer();
        target.setUuid(UUID.randomUUID());
        target.setOwner(UUID.randomUUID());
        target.setControlAccess(ControlAccess.PRIVATE);

        assertEquals(false, WorldUiControlDispatcher.canControlTargetBinding(null, target, MtvChannelBinding.self(target.getUuid())));
        assertEquals(true, WorldUiControlDispatcher.canControlTargetBinding(null, target, MtvChannelBinding.broadcast("channel")));

        // 无主播放器任何玩家可控制
        target.setOwner(null);
        assertEquals(true, WorldUiControlDispatcher.canControlTargetBinding(null, target, MtvChannelBinding.self(target.getUuid())));
    }

    @Test
    void watchTargetUsesTheSamePermissionPolicyAsControls() {
        var target = new ManagedMtvPlayer();
        target.setUuid(UUID.randomUUID());
        target.setOwner(UUID.randomUUID());
        target.setControlAccess(ControlAccess.PRIVATE);

        assertEquals(false, WorldUiControlDispatcher.canWatchTarget(null, target, MtvChannelBinding.self(target.getUuid())));
        assertEquals(true, WorldUiControlDispatcher.canWatchTarget(null, target, MtvChannelBinding.broadcast("channel")));
    }

    private static WorldUiControlRequest request(WorldUiControlOperation operation, WorldUiControlArgument argument) {
        return new WorldUiControlRequest(UUID.randomUUID(), "screen_0", "channel", 1L, 0L,
                0.5F, 0.5F, operation, argument);
    }
}
