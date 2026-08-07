package top.tobyprime.mcedia_mtv.client.channel.worldui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorldUiPlaylistCache {
    private static final int PAGE_SIZE = 32;

    private final Map<String, ChannelPages> channels = new HashMap<>();

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
        int first = (visibleStart / PAGE_SIZE) * PAGE_SIZE;
        int last = (Math.min(visibleEnd, state.manifest.itemCount() - 1) / PAGE_SIZE) * PAGE_SIZE;
        var missing = new ArrayList<Integer>();
        for (int offset = first; offset <= last; offset += PAGE_SIZE) {
            if (!state.pages.containsKey(offset) && state.pendingOffsets.add(offset)) {
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
        private final Set<Integer> pendingOffsets = new HashSet<>();
    }
}
