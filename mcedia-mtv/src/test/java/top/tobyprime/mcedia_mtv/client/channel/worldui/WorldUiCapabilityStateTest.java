package top.tobyprime.mcedia_mtv.client.channel.worldui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiCapabilityStateTest {
    @Test
    void controlsRemainDisabledUntilMatchingCapabilitiesArrive() {
        var state = new WorldUiCapabilityState();

        assertFalse(state.supported());

        state.onCapabilities(new WorldUiCapabilities(1, 32, 0L));

        assertTrue(state.supported());
    }

    @Test
    void unsupportedVersionOrPageSizeDoesNotEnableControls() {
        var state = new WorldUiCapabilityState();

        state.onCapabilities(new WorldUiCapabilities(2, 32, 0L));
        assertFalse(state.supported());

        state.onCapabilities(new WorldUiCapabilities(1, 33, 0L));
        assertFalse(state.supported());
    }

    @Test
    void clearDisablesControlsAfterDisconnect() {
        var state = new WorldUiCapabilityState();
        state.onCapabilities(new WorldUiCapabilities(1, 32, 0L));

        state.clear();

        assertFalse(state.supported());
    }
}
