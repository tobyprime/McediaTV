package top.tobyprime.mcedia_mtv.client.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Bounded render-thread texture registry paired with the CPU-side cover cache. */
public final class MtvCoverTextureRegistry<T> {
    private final int capacity;
    private final Consumer<T> releaser;
    private final LinkedHashMap<String, T> entries = new LinkedHashMap<>(16, 0.75F, true);

    public MtvCoverTextureRegistry(int capacity, Consumer<T> releaser) {
        if (capacity <= 0) throw new IllegalArgumentException("texture capacity must be positive");
        this.capacity = capacity;
        this.releaser = Objects.requireNonNull(releaser, "releaser");
    }

    public T get(String url) {
        return entries.get(url);
    }

    public void put(String url, T texture) {
        T previous = entries.put(Objects.requireNonNull(url, "url"), Objects.requireNonNull(texture, "texture"));
        if (previous != null && previous != texture) releaser.accept(previous);
        while (entries.size() > capacity) {
            var iterator = entries.entrySet().iterator();
            Map.Entry<String, T> eldest = iterator.next();
            iterator.remove();
            releaser.accept(eldest.getValue());
        }
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        for (T texture : entries.values()) releaser.accept(texture);
        entries.clear();
    }
}
