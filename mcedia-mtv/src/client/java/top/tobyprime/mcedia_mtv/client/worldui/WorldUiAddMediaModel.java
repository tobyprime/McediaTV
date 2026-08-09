package top.tobyprime.mcedia_mtv.client.worldui;

import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia.api.media.MediaCollection;
import top.tobyprime.mcedia.api.resolver.MediaResolvers;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlOperation;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlResult;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiRequestIds;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Local URL-preview state for the full-screen MTV add-media view. */
public final class WorldUiAddMediaModel {
    private static final long RESOLUTION_DEBOUNCE_MILLIS = 200L;
    private static final ScheduledExecutorService RESOLUTION_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "mtv-add-media-resolver");
        thread.setDaemon(true);
        return thread;
    });
    private static final CollectionResolver DEFAULT_COLLECTION_RESOLVER = url ->
            CompletableFuture.supplyAsync(() -> MediaResolvers.tryResolveCollection(url), RESOLUTION_SCHEDULER);

    private final MetadataResolver resolver;
    private final CollectionResolver collectionResolver;
    private final ResolutionScheduler scheduler;
    private String input = "";
    private MtvMediaMetadata preview;
    private MediaCollection collection;
    private boolean resolving;
    private boolean collectionResolving;
    private long pendingRequestId;
    private boolean accepted;
    private boolean pending;
    private WorldUiControlError lastError = WorldUiControlError.NONE;
    private Cancellable scheduledResolution = Cancellable.NONE;

    public WorldUiAddMediaModel(MtvMediaMetadataCache cache) {
        this(cache::resolveAsync, DEFAULT_COLLECTION_RESOLVER, task -> {
            var future = RESOLUTION_SCHEDULER.schedule(task, RESOLUTION_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        });
    }

    public WorldUiAddMediaModel(MetadataResolver resolver) {
        this(resolver, DEFAULT_COLLECTION_RESOLVER, task -> {
            task.run();
            return Cancellable.NONE;
        });
    }

    public WorldUiAddMediaModel(MetadataResolver resolver, ResolutionScheduler scheduler) {
        this(resolver, DEFAULT_COLLECTION_RESOLVER, scheduler);
    }

    public WorldUiAddMediaModel(MetadataResolver resolver, CollectionResolver collectionResolver, ResolutionScheduler scheduler) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.collectionResolver = Objects.requireNonNull(collectionResolver, "collectionResolver");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void setInput(String value) {
        scheduledResolution.cancel();
        scheduledResolution = Cancellable.NONE;
        input = value == null ? "" : value.trim();
        preview = null;
        collection = null;
        lastError = WorldUiControlError.NONE;
        accepted = false;
        pending = false;
        pendingRequestId = -1L;
        resolving = !input.isBlank();
        collectionResolving = !input.isBlank();
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
        collectionResolver.resolveCollectionAsync(resolvingInput).whenComplete((resolvedCollection, failure) -> {
            if (!resolvingInput.equals(input)) {
                return;
            }
            collectionResolving = false;
            collection = failure == null && resolvedCollection != null && resolvedCollection.isPresent()
                    ? resolvedCollection.get() : null;
            if (collection != null && collection.getCoverUrl() != null && !collection.getCoverUrl().isBlank()) {
                MtvMediaCoverCache.getInstance().loadAsync(collection.getCoverUrl());
            }
        });
    }

    public String input() {
        return input;
    }

    public boolean resolving() {
        return resolving;
    }

    public boolean collectionResolving() {
        return collectionResolving;
    }

    public MtvMediaMetadata preview() {
        return preview;
    }

    /** Non-null when the input resolves to a playable collection/album. */
    public MediaCollection collection() {
        return collection;
    }

    public boolean canConfirm() {
        return !pending && preview != null && preview.status() == MtvMediaMetadata.Status.RESOLVED
                && preview.normalizedUrl() != null && !preview.normalizedUrl().isBlank();
    }

    public boolean canConfirmCollection() {
        return !pending && collection != null && !collection.getItems().isEmpty();
    }

    public boolean confirm(WorldUiInteractionState.Target target, WorldUiInteractionState.ControlSender sender, AddMode mode) {
        if (target == null || sender == null || mode == null || !canConfirm()) {
            return false;
        }
        pendingRequestId = WorldUiRequestIds.next();
        accepted = false;
        pending = true;
        lastError = WorldUiControlError.NONE;
        sender.send(new WorldUiControlRequest(target.mtvUuid(), target.screenId(), target.channelId(), pendingRequestId,
                target.revision(), 0.5F, 0.5F, mode.operation, new WorldUiControlArgument.MediaUrl(preview.normalizedUrl())));
        return true;
    }

    /** Adds every item of the detected collection to the playlist in one request. */
    public boolean confirmCollection(WorldUiInteractionState.Target target, WorldUiInteractionState.ControlSender sender) {
        if (target == null || sender == null || !canConfirmCollection()) {
            return false;
        }
        var urls = collection.getItems().stream()
                .map(item -> item.getResolutionTarget())
                .filter(targetUrl -> targetUrl != null && !targetUrl.isBlank())
                .limit(MtvChannelProtocol.MAX_COLLECTION_URLS)
                .collect(Collectors.toList());
        if (urls.isEmpty()) {
            return false;
        }
        pendingRequestId = WorldUiRequestIds.next();
        accepted = false;
        pending = true;
        lastError = WorldUiControlError.NONE;
        sender.send(new WorldUiControlRequest(target.mtvUuid(), target.screenId(), target.channelId(), pendingRequestId,
                target.revision(), 0.5F, 0.5F, WorldUiControlOperation.ADD_COLLECTION,
                new WorldUiControlArgument.MediaUrlList(urls)));
        return true;
    }

    /** Applies the asynchronous server result without discarding a rejected local preview. */
    public void onControlResult(WorldUiControlResult result) {
        if (pending && result != null && result.requestId() == pendingRequestId) {
            pending = false;
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

    /** Localized preview status line for the add-media view, shared by all MC versions. */
    public String previewMessage() {
        String value;
        if (collection != null) {
            value = Component.translatable("mcedia_mtv.add_media.collection_preview", collection.getTitle(), collection.getItems().size()).getString();
        } else if (preview == null) value = Component.translatable(resolving ? "mcedia_mtv.add_media.resolving" : "mcedia_mtv.add_media.waiting").getString();
        else if (preview.status() == MtvMediaMetadata.Status.FAILED) value = Component.translatable("mcedia_mtv.add_media.failed", preview.errorReason()).getString();
        else value = Component.translatable("mcedia_mtv.add_media.preview", preview.title(), preview.author(), preview.platform(), preview.description()).getString()
                + " | Cover: " + MtvMediaCoverCache.getInstance().statusLabel(preview.coverUrl());
        return lastError.name().equals("NONE") ? value : value + " | " + Component.translatable("mcedia_mtv.add_media.server_error", lastError.name()).getString();
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
    public interface CollectionResolver {
        CompletableFuture<Optional<MediaCollection>> resolveCollectionAsync(String url);
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
