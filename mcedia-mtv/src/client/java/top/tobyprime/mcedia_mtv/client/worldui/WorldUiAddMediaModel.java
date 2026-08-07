package top.tobyprime.mcedia_mtv.client.worldui;

import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlOperation;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlResult;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Local URL-preview state for the full-screen MTV add-media view. */
public final class WorldUiAddMediaModel {
    private static final long RESOLUTION_DEBOUNCE_MILLIS = 200L;
    private static final ScheduledExecutorService RESOLUTION_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "mtv-add-media-resolver");
        thread.setDaemon(true);
        return thread;
    });

    private final MetadataResolver resolver;
    private final ResolutionScheduler scheduler;
    private final AtomicLong requestIds = new AtomicLong();

    private String input = "";
    private MtvMediaMetadata preview;
    private boolean resolving;
    private long pendingRequestId;
    private boolean accepted;
    private WorldUiControlError lastError = WorldUiControlError.NONE;
    private Cancellable scheduledResolution = Cancellable.NONE;

    public WorldUiAddMediaModel(MtvMediaMetadataCache cache) {
        this(cache::resolveAsync, task -> {
            var future = RESOLUTION_SCHEDULER.schedule(task, RESOLUTION_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        });
    }

    public WorldUiAddMediaModel(MetadataResolver resolver) {
        this(resolver, task -> {
            task.run();
            return Cancellable.NONE;
        });
    }

    public WorldUiAddMediaModel(MetadataResolver resolver, ResolutionScheduler scheduler) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void setInput(String value) {
        scheduledResolution.cancel();
        scheduledResolution = Cancellable.NONE;
        input = value == null ? "" : value.trim();
        preview = null;
        lastError = WorldUiControlError.NONE;
        resolving = !input.isBlank();
        if (!resolving) {
            return;
        }
        String resolvingInput = input;
        scheduledResolution = scheduler.schedule(() -> resolve(resolvingInput));
    }

    private void resolve(String resolvingInput) {
        if (!resolvingInput.equals(input)) {
            return;
        }
        resolver.resolveAsync(resolvingInput).whenComplete((metadata, failure) -> {
            if (!resolvingInput.equals(input)) {
                return;
            }
            resolving = false;
            preview = failure == null && metadata != null
                    ? metadata : MtvMediaMetadata.failed(resolvingInput, failure == null ? "no metadata" : failure.getMessage());
            if (preview.status() == MtvMediaMetadata.Status.RESOLVED && !preview.coverUrl().isBlank()) {
                MtvMediaCoverCache.getInstance().loadAsync(preview.coverUrl());
            }
        });
    }

    public String input() {
        return input;
    }

    public boolean resolving() {
        return resolving;
    }

    public MtvMediaMetadata preview() {
        return preview;
    }

    public boolean canConfirm() {
        return preview != null && preview.status() == MtvMediaMetadata.Status.RESOLVED
                && preview.normalizedUrl() != null && !preview.normalizedUrl().isBlank();
    }

    public boolean confirm(WorldUiInteractionState.Target target, WorldUiInteractionState.ControlSender sender, AddMode mode) {
        if (target == null || sender == null || mode == null || !canConfirm()) {
            return false;
        }
        pendingRequestId = requestIds.incrementAndGet();
        accepted = false;
        lastError = WorldUiControlError.NONE;
        sender.send(new WorldUiControlRequest(target.mtvUuid(), target.screenId(), target.channelId(), pendingRequestId,
                target.revision(), 0.5F, 0.5F, mode.operation, new WorldUiControlArgument.MediaUrl(preview.normalizedUrl())));
        return true;
    }

    /** Applies the asynchronous server result without discarding a rejected local preview. */
    public void onControlResult(WorldUiControlResult result) {
        if (result != null && result.requestId() == pendingRequestId) {
            if (result.accepted()) {
                accepted = true;
                lastError = WorldUiControlError.NONE;
            } else {
                lastError = result.error() == null ? WorldUiControlError.INTERNAL_ERROR : result.error();
            }
        }
    }

    public WorldUiControlError lastError() {
        return lastError;
    }

    /** Returns true exactly once after this model's add request was accepted. */
    public boolean consumeAccepted() {
        boolean result = accepted;
        accepted = false;
        return result;
    }

    public enum AddMode {
        PREPEND(WorldUiControlOperation.PREPEND),
        INSERT_NEXT(WorldUiControlOperation.INSERT_NEXT),
        APPEND(WorldUiControlOperation.APPEND),
        INSERT_AND_PLAY(WorldUiControlOperation.INSERT_AND_PLAY);

        private final WorldUiControlOperation operation;

        AddMode(WorldUiControlOperation operation) {
            this.operation = operation;
        }
    }

    @FunctionalInterface
    public interface MetadataResolver {
        CompletableFuture<MtvMediaMetadata> resolveAsync(String url);
    }

    @FunctionalInterface
    public interface ResolutionScheduler {
        Cancellable schedule(Runnable task);
    }

    @FunctionalInterface
    public interface Cancellable {
        Cancellable NONE = () -> { };

        void cancel();
    }
}
