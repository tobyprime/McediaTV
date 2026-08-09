package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiLayoutTest {
    @Test
    void bottomRightToggleIsVisibleOnlyWhilePointerIsInTriggerOrButton() {
        var layout = new WorldUiLayout();

        assertTrue(layout.hit(0.98F, 0.98F, false).isToggle());
        assertFalse(layout.hit(0.50F, 0.50F, false).isToggle());
    }

    @Test
    void triggerAreaShowsButtonButDoesNotConsumeClicksOutsideTheButton() {
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 1L);
        var presentation = new WorldUiPresentationState();

        presentation.update(target, 0.91F, 0.91F);

        assertTrue(presentation.shouldRender(target));
        assertEquals(WorldUiHit.Kind.NONE, presentation.hit().kind());
    }

    @Test
    void seekSendsOnceOnRelease() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();

        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 100_000_000L);
        state.onPrimaryPress(target, layout.hit(0.25F, 0.94F, true), 0.25F, 0.94F, 100_000_000L);
        state.onPointerMove(0.45F, 0.94F);
        state.onPointerMove(0.75F, 0.94F);
        assertTrue(sender.requests.isEmpty());

        state.onPrimaryRelease(0.75F, 0.94F, 100_000_000L);

        assertEquals(1, sender.requests.size());
        WorldUiControlRequest request = sender.requests.getFirst();
        assertEquals("SEEK_ABSOLUTE", request.operation().name());
        assertEquals(new WorldUiControlArgument.PositionUs(73_750_008L), request.argument());
    }

    @Test
    void seekReleaseAfterLeavingScreenUsesLastPreviewAndClearsDrag() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();

        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 100_000_000L);
        state.onPrimaryPress(target, layout.hit(0.25F, 0.94F, true), 0.25F, 0.94F, 100_000_000L);
        state.onPointerMove(0.75F, 0.94F);
        state.onPrimaryRelease();
        state.onPrimaryRelease();

        assertEquals(1, sender.requests.size());
        assertEquals(new WorldUiControlArgument.PositionUs(73_750_008L), sender.requests.getFirst().argument());
    }

    @Test
    void seekTrackMapsItsVisualEdgesToZeroAndFullDuration() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();
        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 1_000_000L);

        state.onPrimaryPress(target, layout.hit(0.16F, 0.94F, true), 0.16F, 0.94F, 1_000_000L);
        state.onPrimaryRelease(0.16F, 0.94F, 1_000_000L);
        state.onPrimaryPress(target, layout.hit(0.96F, 0.94F, true), 0.96F, 0.94F, 1_000_000L);
        state.onPrimaryRelease(0.96F, 0.94F, 1_000_000L);

        assertEquals(new WorldUiControlArgument.PositionUs(0L), sender.requests.get(0).argument());
        assertEquals(new WorldUiControlArgument.PositionUs(1_000_000L), sender.requests.get(1).argument());
    }

    @Test
    void volumeBarMapsItsVerticalEdgesToZeroAndFullVolume() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();
        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 1L);

        state.onPrimaryPress(target, layout.hit(0.09F, 0.58F, true), 0.09F, 0.58F, 1L);
        state.onPrimaryRelease(0.09F, 0.58F, 1L);
        state.onPrimaryPress(target, layout.hit(0.09F, 0.30F, true), 0.09F, 0.30F, 1L);
        state.onPrimaryRelease(0.09F, 0.30F, 1L);

        assertEquals(new WorldUiControlArgument.Scalar(0.0F), sender.requests.get(0).argument());
        assertEquals(new WorldUiControlArgument.Scalar(1.0F), sender.requests.get(1).argument());
    }

    @Test
    void brightnessBarMapsItsVerticalEdgesToZeroAndFullBrightness() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();
        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 1L);

        state.onPrimaryPress(target, layout.hit(0.05F, 0.58F, true), 0.05F, 0.58F, 1L);
        state.onPrimaryRelease(0.05F, 0.58F, 1L);
        state.onPrimaryPress(target, layout.hit(0.05F, 0.30F, true), 0.05F, 0.30F, 1L);
        state.onPrimaryRelease(0.05F, 0.30F, 1L);

        assertEquals("SET_BRIGHTNESS", sender.requests.get(0).operation().name());
        assertEquals(new WorldUiControlArgument.Scalar(0.0F), sender.requests.get(0).argument());
        assertEquals("SET_BRIGHTNESS", sender.requests.get(1).operation().name());
        assertEquals(new WorldUiControlArgument.Scalar(15.0F), sender.requests.get(1).argument());
    }

    @Test
    void expandedControlsUseTheNewestChannelRevision() {
        var sender = new RecordingSender();
        var first = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var refreshed = new WorldUiInteractionState.Target(first.mtvUuid(), "screen_0", "self:test", 10L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();

        state.onPrimaryPress(first, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 100_000_000L);
        state.onPrimaryPress(refreshed, layout.hit(0.38F, 0.86F, true), 0.38F, 0.86F, 100_000_000L);

        assertEquals(1, sender.requests.size());
        assertEquals(10L, sender.requests.getFirst().expectedRevision());
    }

    @Test
    void playlistPanelProvidesStableIndexedRowsAndSeparateAddButton() {
        var layout = new WorldUiLayout();

        assertEquals(WorldUiHit.Kind.QUEUE, layout.hit(0.58F, 0.86F, true, true).kind());
        assertEquals(WorldUiHit.Kind.PLAYLIST_ITEM, layout.hit(0.75F, 0.20F, true, true).kind());
        assertEquals(1, layout.hit(0.75F, 0.20F, true, true).index());
        assertEquals(WorldUiHit.Kind.ADD_MEDIA, layout.hit(0.89F, 0.07F, true, true).kind());
    }

    @Test
    void speedAndMuteUseImmediateControls() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 2L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();
        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), .98F, .98F, 1L);
        state.onPrimaryPress(target, layout.hit(.18F, .86F, true), .18F, .86F, 1L);
        state.onPrimaryPress(target, layout.hit(.70F, .86F, true), .70F, .86F, 1L);
        assertEquals("SET_SPEED", sender.requests.get(0).operation().name());
        assertEquals(new WorldUiControlArgument.Scalar(.5F), sender.requests.get(0).argument());
        assertEquals("SET_MASTER_VOLUME", sender.requests.get(1).operation().name());
        assertEquals(new WorldUiControlArgument.Scalar(0.0F), sender.requests.get(1).argument());
    }

    @Test
    void detailsAreHiddenForSmallOrExtremeAspectScreens() {
        assertTrue(WorldUiLayout.showsDetails(1.6F, 0.9F));
        assertFalse(WorldUiLayout.showsDetails(0.79F, 0.9F));
        assertFalse(WorldUiLayout.showsDetails(1.6F, 0.44F));
        assertFalse(WorldUiLayout.showsDetails(3.2F, 0.5F));
        assertFalse(WorldUiLayout.showsDetails(0.4F, 0.9F));
    }

    private static final class RecordingSender implements WorldUiInteractionState.ControlSender {
        private final List<WorldUiControlRequest> requests = new ArrayList<>();

        @Override
        public void send(WorldUiControlRequest request) {
            requests.add(request);
        }

        @Override
        public void watch(WorldUiInteractionState.Target target) {
        }

        @Override
        public void unwatch(WorldUiInteractionState.Target target) {
        }
    }
}
