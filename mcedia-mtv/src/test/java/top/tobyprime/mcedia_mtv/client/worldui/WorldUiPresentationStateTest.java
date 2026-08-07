package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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


    @Test
    void collapsedToggleFadesInGraduallyWhileHoveringTheTriggerZone() {
        var state = new WorldUiPresentationState();
        var target = target();

        state.update(target, 0.98F, 0.98F);
        assertTrue(state.shouldRender(target));
        float first = state.hoverFade();
        state.tickHoverFade(true);
        assertTrue(state.hoverFade() > first);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        assertEquals(1.0F, state.hoverFade(), 0.001F);
    }

    @Test
    void collapsedToggleFadesOutAfterThePointerLeavesTheTriggerZone() {
        var state = new WorldUiPresentationState();
        var target = target();

        state.update(target, 0.98F, 0.98F);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        assertTrue(state.hoverFade() > 0.9F);

        state.update(target, 0.50F, 0.50F);
        state.tickHoverFade(false);
        assertTrue(state.shouldRender(target));
        assertTrue(state.hoverFade() < 1.0F);

        while (state.hoverFade() > 0.0F) {
            state.tickHoverFade(false);
        }
        assertFalse(state.shouldRender(target));
    }

    @Test
    void collapsingResetsTheHoverFade() {
        var state = new WorldUiPresentationState();
        var target = target();

        state.update(target, 0.98F, 0.98F);
        state.tickHoverFade(true);
        state.tickHoverFade(true);
        state.expand(target);
        state.collapse();
        assertEquals(0.0F, state.hoverFade(), 0.001F);
        state.clearHover();
        assertFalse(state.shouldRender(target));
    }

    private static WorldUiInteractionState.Target target() {
        return new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 4L);
    }
}
