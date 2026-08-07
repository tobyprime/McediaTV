package top.tobyprime.mcedia_mtv.client.metadata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MtvCoverTextureRegistryTest {
    @Test
    void evictsLeastRecentlyUsedTextureAndReleasesIt() {
        var released = new ArrayList<String>();
        var registry = new MtvCoverTextureRegistry<String>(2, released::add);

        registry.put("first", "first-texture");
        registry.put("second", "second-texture");
        registry.get("first");
        registry.put("third", "third-texture");

        assertEquals(null, registry.get("second"));
        assertEquals("first-texture", registry.get("first"));
        assertEquals("third-texture", registry.get("third"));
        assertEquals(java.util.List.of("second-texture"), released);
    }

    @Test
    void clearReleasesEveryRegisteredTexture() {
        var released = new ArrayList<String>();
        var registry = new MtvCoverTextureRegistry<String>(2, released::add);
        registry.put("first", "first-texture");
        registry.put("second", "second-texture");

        registry.clear();

        assertEquals(0, registry.size());
        assertEquals(java.util.List.of("first-texture", "second-texture"), released);
    }
}
