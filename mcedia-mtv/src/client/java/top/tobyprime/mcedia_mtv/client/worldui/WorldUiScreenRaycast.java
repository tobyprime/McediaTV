package top.tobyprime.mcedia_mtv.client.worldui;

import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapping-independent nearest, front-facing MTV screen selection.
 *
 * <p>All intersection math runs in double precision inside the screen's local
 * frame (centre at the origin, axes right/up/normal). Screens can sit at very
 * large world coordinates (e.g. 10000+) where single-precision float would wash
 * out the hit point; working relative to the screen centre keeps every
 * intermediate value small and exact.
 */
public final class WorldUiScreenRaycast {
    private static final float EPSILON = 1.0E-5F;

    private WorldUiScreenRaycast() {
    }

    public static Optional<Hit> select(Vector3d origin, Vector3d direction, List<Screen> screens) {
        if (origin == null || direction == null || screens == null || direction.lengthSquared() <= EPSILON) {
            return Optional.empty();
        }
        Vector3d normalizedDirection = new Vector3d(direction).normalize();
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

    private static Hit intersect(Vector3d origin, Vector3d direction, Screen screen) {
        double rx = screen.right().x, ry = screen.right().y, rz = screen.right().z;
        double ux = screen.up().x, uy = screen.up().y, uz = screen.up().z;
        // normal = right × up; unit length because right and up are unit and perpendicular
        double nx = ry * uz - rz * uy;
        double ny = rz * ux - rx * uz;
        double nz = rx * uy - ry * ux;

        Vector3d rel = origin.sub(screen.center(), new Vector3d());
        double ox = rel.x * rx + rel.y * ry + rel.z * rz;
        double oy = rel.x * ux + rel.y * uy + rel.z * uz;
        double oz = rel.x * nx + rel.y * ny + rel.z * nz;
        double dx = direction.x * rx + direction.y * ry + direction.z * rz;
        double dy = direction.x * ux + direction.y * uy + direction.z * uz;
        double dz = direction.x * nx + direction.y * ny + direction.z * nz;

        // Rays arriving from the visible (video) face have a negative dz.
        if (dz >= -EPSILON) {
            return null;
        }
        double t = -oz / dz;
        if (t < 0.0D) {
            return null;
        }
        double halfW = screen.width() * 0.5F;
        double halfH = screen.height() * 0.5F;
        double hx = ox + dx * t;
        double hy = oy + dy * t;
        if (hx < -halfW || hx > halfW || hy < -halfH || hy > halfH) {
            return null;
        }
        float u = (float) (hx / screen.width() + 0.5D);
        float v = (float) (0.5D - hy / screen.height());
        return new Hit(screen, (float) t, u, v);
    }

    public record Screen(String id, Vector3d center, Vector3f right, Vector3f up, float width, float height) {
        public Screen {
            if (id == null || id.isBlank() || center == null || right == null || up == null || width <= 0.0F || height <= 0.0F
                    || right.lengthSquared() <= EPSILON || up.lengthSquared() <= EPSILON) {
                throw new IllegalArgumentException("screen geometry is invalid");
            }
            center = new Vector3d(center);
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
