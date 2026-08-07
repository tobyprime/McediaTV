package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.function.LongSupplier;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;

public final class WorldUiPlaylistCache {
    private static final int PAGE_SIZE = 32;
    private static final int MAX_PENDING_REQUESTS = 4;
    private static final long PENDING_TIMEOUT_NANOS = 3_000_000_000L;
    private static final WorldUiPlaylistCache INSTANCE = new WorldUiPlaylistCache();

    private final LongSupplier clockNanos;
    private final Map<String, ChannelPages> channels = new HashMap<>();

    public WorldUiPlaylistCache() {
        this(System::nanoTime);
    }

    WorldUiPlaylistCache(LongSupplier clockNanos) {
        this.clockNanos = Objects.requireNonNull(clockNanos, "clockNanos");
    }

    public static WorldUiPlaylistCache getInstance() {
        return INSTANCE;
    }

    public synchronized void applyManifest(WorldUiPlaylistManifest manifest) {
        ChannelPages state = channels.computeIfAbsent(manifest.channelId(), ignored -> new ChannelPages());
        if (state.manifest == null || state.manifest.revision() != manifest.revision()) {
            state.pages.clear();
            state.pendingOffsets.clear();
        }
        state.manifest = manifest;
    }

    public synchronized void applyPage(WorldUiPlaylistPage page) {
        ChannelPages state = channels.get(page.channelId());
        if (state == null || state.manifest == null || state.manifest.revision() != page.revision()) {
            return;
        }
        state.pages.put(page.offset(), page);
        state.pendingOffsets.remove(page.offset());
        for (String mediaUrl : page.mediaUrls()) {
            MtvMediaMetadataCache.getInstance().resolveAsync(mediaUrl);
        }
    }

    public synchronized Optional<WorldUiPlaylistManifest> manifest(String channelId) {
        ChannelPages state = channels.get(channelId);
        return state == null ? Optional.empty() : Optional.ofNullable(state.manifest);
    }

    public synchronized Optional<WorldUiPlaylistPage> pageAt(String channelId, int offset) {
        ChannelPages state = channels.get(channelId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(state.pages.get(offset));
    }

    public synchronized List<Integer> missingOffsetsForVisibleRange(String channelId, int visibleStart, int visibleEnd) {
        if (visibleStart < 0 || visibleEnd < visibleStart) {
            throw new IllegalArgumentException("visible range is invalid");
        }
        ChannelPages state = channels.get(channelId);
        if (state == null || state.manifest == null || state.manifest.itemCount() == 0) {
            return List.of();
        }
        long now = clockNanos.getAsLong();
        state.pendingOffsets.entrySet().removeIf(entry -> {
            long age = now - entry.getValue();
            return age >= PENDING_TIMEOUT_NANOS || age < 0L;
        });
        int first = (visibleStart / PAGE_SIZE) * PAGE_SIZE;
        int last = (Math.min(visibleEnd, state.manifest.itemCount() - 1) / PAGE_SIZE) * PAGE_SIZE;
        var missing = new ArrayList<Integer>();
        for (int offset = first; offset <= last; offset += PAGE_SIZE) {
            if (!state.pages.containsKey(offset) && state.pendingOffsets.size() < MAX_PENDING_REQUESTS
                    && state.pendingOffsets.putIfAbsent(offset, now) == null) {
                missing.add(offset);
            }
        }
        return List.copyOf(missing);
    }

    public synchronized void failPageRequest(String channelId, int offset) {
        ChannelPages state = channels.get(channelId);
        if (state != null) {
            state.pendingOffsets.remove(offset);
        }
    }

    public synchronized void clear() {
        channels.clear();
    }

    private static final class ChannelPages {
        private WorldUiPlaylistManifest manifest;
        private final Map<Integer, WorldUiPlaylistPage> pages = new HashMap<>();
        private final Map<Integer, Long> pendingOffsets = new HashMap<>();
    }
}
