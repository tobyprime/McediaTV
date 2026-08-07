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
    void seekSendsOnceOnRelease() {
        var sender = new RecordingSender();
        var target = new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 9L);
        var state = new WorldUiInteractionState(sender);
        var layout = new WorldUiLayout();

        state.onPrimaryPress(target, layout.hit(0.98F, 0.98F, false), 0.98F, 0.98F, 100_000_000L);
        state.onPrimaryPress(target, layout.hit(0.25F, 0.88F, true), 0.25F, 0.88F, 100_000_000L);
        state.onPointerMove(0.45F, 0.88F);
        state.onPointerMove(0.75F, 0.88F);
        assertTrue(sender.requests.isEmpty());

        state.onPrimaryRelease(0.75F, 0.88F, 100_000_000L);

        assertEquals(1, sender.requests.size());
        WorldUiControlRequest request = sender.requests.getFirst();
        assertEquals("SEEK_ABSOLUTE", request.operation().name());
        assertEquals(new WorldUiControlArgument.PositionUs(75_000_000L), request.argument());
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
