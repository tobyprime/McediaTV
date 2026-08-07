package top.tobyprime.mcedia_mtv.client.worldui;

import java.util.Objects;

/** Coordinates version-specific render resource cleanup from shared client lifecycle code. */
public final class MtvWorldUiRenderResources {
    private static final MtvWorldUiRenderResources INSTANCE = new MtvWorldUiRenderResources();

    private Runnable textureCleanup = () -> { };

    public static MtvWorldUiRenderResources getInstance() {
        return INSTANCE;
    }

    public synchronized void setTextureCleanup(Runnable textureCleanup) {
        this.textureCleanup = Objects.requireNonNull(textureCleanup, "textureCleanup");
    }

    public void clear() {
        Runnable cleanup;
        synchronized (this) {
            cleanup = textureCleanup;
        }
        cleanup.run();
    }
}
