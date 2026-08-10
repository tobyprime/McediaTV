package top.tobyprime.mcedia_mtv.client.worldui;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldUiScreenRaycastTest {
    @Test
    void selectsNearestFrontFacingScreenAndMapsCenterToHalfUv() {
        var near = new WorldUiScreenRaycast.Screen("near", new Vector3d(0.0D, 0.0D, 3.0D),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);
        var far = new WorldUiScreenRaycast.Screen("far", new Vector3d(0.0D, 0.0D, 6.0D),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);

        // Ray approaches from the front (+Z side), hitting the far screen (z=6) first.
        var hit = WorldUiScreenRaycast.select(new Vector3d(0.0D, 0.0D, 8.0D), new Vector3d(0.0D, 0.0D, -1.0D), List.of(far, near));

        assertTrue(hit.isPresent());
        assertEquals("far", hit.orElseThrow().screen().id());
        assertEquals(0.5F, hit.orElseThrow().u());
        assertEquals(0.5F, hit.orElseThrow().v());
    }

    @Test
    void rejectsRayApproachingFromBehindTheScreen() {
        var screen = new WorldUiScreenRaycast.Screen("s", new Vector3d(0.0D, 0.0D, 3.0D),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);

        // Ray from the -Z side aiming toward the back face must be rejected: only the
        // visible (video) face is interactive.
        var hit = WorldUiScreenRaycast.select(new Vector3d(), new Vector3d(0.0D, 0.0D, 1.0D), List.of(screen));

        assertTrue(hit.isEmpty());
    }

    @Test
    void hitStaysAccurateAtLargeWorldCoordinates() {
        // Screens and camera all at z ≈ 12000, where float has ~0.001 precision and a
        // 0.002-0.006 UI layer offset would be indistinguishable; the double-precision
        // local-frame ray must still map the centre to exactly (0.5, 0.5).
        var screen = new WorldUiScreenRaycast.Screen("s", new Vector3d(12345.6789D, 64.0D, 12000.5D),
                new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 2.0F, 1.0F);

        var hit = WorldUiScreenRaycast.select(new Vector3d(12345.6789D, 64.0D, 12002.5D), new Vector3d(0.0D, 0.0D, -1.0D), List.of(screen));

        assertTrue(hit.isPresent());
        assertEquals(0.5F, hit.orElseThrow().u());
        assertEquals(0.5F, hit.orElseThrow().v());
        assertEquals(2.0F, hit.orElseThrow().distance(), 1.0E-5F);
    }
}
