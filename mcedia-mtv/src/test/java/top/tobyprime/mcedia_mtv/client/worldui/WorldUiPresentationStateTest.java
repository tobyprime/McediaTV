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

    @Test
    void playlistPagingStepsBySevenSoEveryItemIsReachable() {
        var state = new WorldUiPresentationState();

        // 8 items: the 8th (index 7) must be reachable on the second page.
        state.nextPlaylistPage(8);
        assertEquals(7, state.playlistStart());

        // Clamped to the last full page, never past it.
        state.nextPlaylistPage(8);
        assertEquals(7, state.playlistStart());

        state.previousPlaylistPage();
        assertEquals(0, state.playlistStart());

        // 7 items fit on a single page; there is no next page.
        state.nextPlaylistPage(7);
        assertEquals(0, state.playlistStart());

        // 15 items span pages 0, 7 and 14; index 14 is reachable.
        state.nextPlaylistPage(15);
        assertEquals(7, state.playlistStart());
        state.nextPlaylistPage(15);
        assertEquals(14, state.playlistStart());
        state.nextPlaylistPage(15);
        assertEquals(14, state.playlistStart());
    }

    private static WorldUiInteractionState.Target target() {
        return new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 4L);
    }
}
