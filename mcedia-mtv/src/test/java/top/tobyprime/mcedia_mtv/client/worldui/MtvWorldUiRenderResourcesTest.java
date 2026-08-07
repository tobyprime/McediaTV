package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MtvWorldUiRenderResourcesTest {
    @Test
    void clearInvokesTheRegisteredTextureCleanup() {
        var cleaned = new AtomicInteger();
        var resources = new MtvWorldUiRenderResources();
        resources.setTextureCleanup(cleaned::incrementAndGet);

        resources.clear();

        assertEquals(1, cleaned.get());
    }

    @Test
    void replacingCleanupDoesNotInvokeTheStaleRenderer() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var resources = new MtvWorldUiRenderResources();
        resources.setTextureCleanup(first::incrementAndGet);
        resources.setTextureCleanup(second::incrementAndGet);

        resources.clear();

        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }
}
