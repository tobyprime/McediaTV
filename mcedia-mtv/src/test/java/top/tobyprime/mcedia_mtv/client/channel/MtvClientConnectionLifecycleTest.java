package top.tobyprime.mcedia_mtv.client.channel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MtvClientConnectionLifecycleTest {

    @Test
    void joinDoesNotClearRuntimeSessions() {
        var events = new ArrayList<String>();
        var lifecycle = new MtvClientConnectionLifecycle(
                () -> events.add("hud-cleanup"),
                () -> events.add("session-clear")
        );

        lifecycle.onJoin();

        assertEquals(List.of(), events);
    }

    @Test
    void disconnectCleansHudBeforeChannelSessions() {
        var events = new ArrayList<String>();
        var lifecycle = new MtvClientConnectionLifecycle(
                () -> events.add("hud-cleanup"),
                () -> events.add("session-clear")
        );

        lifecycle.onDisconnect();

        assertEquals(List.of("hud-cleanup", "session-clear"), events);
    }

    @Test
    void disconnectClearsSessionsWhenHudCleanupFails() {
        var events = new ArrayList<String>();
        var lifecycle = new MtvClientConnectionLifecycle(
                () -> {
                    events.add("hud-cleanup");
                    throw new IllegalStateException("HUD cleanup failed");
                },
                () -> events.add("session-clear")
        );

        assertDoesNotThrow(lifecycle::onDisconnect);

        assertEquals(List.of("hud-cleanup", "session-clear"), events);
    }
}
