package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiPresentationStateTest {
    @Test
    void onlyTheHoveredBottomRightTargetShowsTheCollapsedToggle() {
        var state = new WorldUiPresentationState();
        var target = target();

        state.update(target, 0.98F, 0.98F);

        assertTrue(state.shouldRender(target));
        state.update(target, 0.50F, 0.50F);
        assertFalse(state.shouldRender(target));
    }

    @Test
    void expandedTargetStaysVisibleAfterPointerLeavesTheToggle() {
        var state = new WorldUiPresentationState();
        var target = target();

        state.update(target, 0.98F, 0.98F);
        state.expand(target);
        state.update(target, 0.20F, 0.20F);

        assertTrue(state.shouldRender(target));
    }

    private static WorldUiInteractionState.Target target() {
        return new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 4L);
    }
}
