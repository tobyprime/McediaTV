package top.tobyprime.mcedia_mtv.client.worldui;

import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Mapping-independent nearest, front-facing MTV screen selection. */
public final class WorldUiScreenRaycast {
    private static final float EPSILON = 1.0E-5F;

    private WorldUiScreenRaycast() {
    }

    public static Optional<Hit> select(Vector3f origin, Vector3f direction, List<Screen> screens) {
        if (origin == null || direction == null || screens == null || direction.lengthSquared() <= EPSILON) {
            return Optional.empty();
        }
        Vector3f normalizedDirection = new Vector3f(direction).normalize();
        Hit nearest = null;
        for (Screen screen : screens) {
            if (screen == null) {
                continue;
            }
            Hit hit = intersect(origin, normalizedDirection, screen);
            if (hit != null && (nearest == null || hit.distance() < nearest.distance())) {
                nearest = hit;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Hit intersect(Vector3f origin, Vector3f direction, Screen screen) {
        // right × up is the screen's visible (video) face; rays arriving from that
        // side have a negative dot product and are the ones a player can interact with.
        Vector3f normal = new Vector3f(screen.right()).cross(screen.up()).normalize();
        float denominator = normal.dot(direction);
        if (denominator >= -EPSILON) {
            return null;
        }
        float distance = normal.dot(new Vector3f(screen.center()).sub(origin)) / denominator;
        if (distance < 0.0F) {
            return null;
        }
        Vector3f point = new Vector3f(direction).mul(distance).add(origin);
        Vector3f local = point.sub(screen.center(), new Vector3f());
        float u = 0.5F + local.dot(screen.right()) / screen.width();
        float v = 0.5F - local.dot(screen.up()) / screen.height();
        if (u < 0.0F || u > 1.0F || v < 0.0F || v > 1.0F) {
            return null;
        }
        return new Hit(screen, distance, u, v);
    }

    public record Screen(String id, Vector3f center, Vector3f right, Vector3f up, float width, float height) {
        public Screen {
            if (id == null || id.isBlank() || center == null || right == null || up == null || width <= 0.0F || height <= 0.0F
                    || right.lengthSquared() <= EPSILON || up.lengthSquared() <= EPSILON) {
                throw new IllegalArgumentException("screen geometry is invalid");
            }
            center = new Vector3f(center);
            right = new Vector3f(right).normalize();
            up = new Vector3f(up).normalize();
            if (Math.abs(right.dot(up)) > EPSILON) {
                throw new IllegalArgumentException("screen axes must be perpendicular");
            }
        }
    }

    public record Hit(Screen screen, float distance, float u, float v) {
        public Hit {
            Objects.requireNonNull(screen, "screen");
        }
    }
}
