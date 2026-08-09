package top.tobyprime.mcedia_mtv.client.worldui;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiScreenRaycastTest {
    @Test
    void selectsNearestFrontFacingScreenAndMapsCenterToHalfUv() {
        var near = new WorldUiScreenRaycast.Screen("near", new Vector3f(0.0F, 0.0F, 3.0F),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);
        var far = new WorldUiScreenRaycast.Screen("far", new Vector3f(0.0F, 0.0F, 6.0F),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);

        // Ray approaches from the front (+Z side), hitting the far screen (z=6) first.
        var hit = WorldUiScreenRaycast.select(new Vector3f(0.0F, 0.0F, 8.0F), new Vector3f(0.0F, 0.0F, -1.0F), List.of(far, near));

        assertTrue(hit.isPresent());
        assertEquals("far", hit.orElseThrow().screen().id());
        assertEquals(0.5F, hit.orElseThrow().u());
        assertEquals(0.5F, hit.orElseThrow().v());
    }

    @Test
    void rejectsRayApproachingFromBehindTheScreen() {
        var screen = new WorldUiScreenRaycast.Screen("s", new Vector3f(0.0F, 0.0F, 3.0F),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);

        // Ray from the -Z side aiming toward the back face must be rejected: only the
        // visible (video) face is interactive.
        var hit = WorldUiScreenRaycast.select(new Vector3f(), new Vector3f(0.0F, 0.0F, 1.0F), List.of(screen));

        assertTrue(hit.isEmpty());
    }
}
