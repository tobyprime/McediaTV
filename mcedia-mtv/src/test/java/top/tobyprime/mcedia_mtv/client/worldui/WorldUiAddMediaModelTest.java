package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiAddMediaModelTest {
    @Test
    void confirmIsDisabledUntilTheLocalResolverReturnsSupportedMetadata() {
        var resolver = new CompletableFuture<MtvMediaMetadata>();
        var model = new WorldUiAddMediaModel(url -> resolver);

        model.setInput("https://unsupported.example/video");
        assertFalse(model.canConfirm());
        resolver.complete(MtvMediaMetadata.failed("https://unsupported.example/video", "unsupported"));

        assertFalse(model.canConfirm());
    }

    @Test
    void confirmSendsOnlyResolvedUrlForSelectedAddMode() {
        var sender = new RecordingSender();
        var metadata = new MtvMediaMetadata("https://example.com/video", "title", "", "", "", "", MtvMediaMetadata.Status.RESOLVED, "");
        var model = new WorldUiAddMediaModel(url -> CompletableFuture.completedFuture(metadata));
        model.setInput("https://example.com/video");

        assertTrue(model.canConfirm());
        assertTrue(model.confirm(new WorldUiInteractionState.Target(UUID.randomUUID(), "screen_0", "self:test", 4L), sender, WorldUiAddMediaModel.AddMode.INSERT_NEXT));
        assertEquals(1, sender.requests.size());
        assertEquals("INSERT_NEXT", sender.requests.getFirst().operation().name());
        assertEquals("https://example.com/video", ((WorldUiControlArgument.MediaUrl) sender.requests.getFirst().argument()).value());
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
