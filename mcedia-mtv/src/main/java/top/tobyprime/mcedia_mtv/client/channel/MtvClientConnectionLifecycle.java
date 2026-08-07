package top.tobyprime.mcedia_mtv.client.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MtvClientConnectionLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientConnectionLifecycle.class);

    private final Runnable hudCleanup;
    private final Runnable sessionClear;

    public MtvClientConnectionLifecycle(Runnable hudCleanup, Runnable sessionClear) {
        this.hudCleanup = hudCleanup;
        this.sessionClear = sessionClear;
    }

    public void onJoin() {
        LOGGER.info("MTV client JOIN: retain channel sessions until binding routing completes");
    }

    public void onDisconnect() {
        LOGGER.info("MTV client DISCONNECT: cleaning HUD peripherals before channel sessions");
        runCleanup("HUD peripherals", hudCleanup);
        runCleanup("channel sessions", sessionClear);
        LOGGER.info("MTV client DISCONNECT: channel sessions cleared");
    }

    private void runCleanup(String phase, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            LOGGER.warn("MTV client DISCONNECT: {} cleanup failed; continuing", phase, e);
        }
    }
}
