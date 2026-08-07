package top.tobyprime.mcedia_mtv_plugin.worldui;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.model.ScreenPeripheralConfigModel;

/** Rebuilds an MTV screen hit solely from persisted server-side transform data. */
public final class WorldUiScreenHitValidator {
    private static final double OCCLUSION_EPSILON_SQUARED = 1.0E-6D;

    public ValidationResult validate(Player player, ManagedMtvPlayer target, String screenId, float u, float v) {
        if (target == null || target.findScreen(screenId) == null) {
            return new ValidationResult(WorldUiControlError.SCREEN_NOT_FOUND, null);
        }
        if (player == null || player.getWorld() == null || target.getWorld() == null
                || !target.getWorld().equals(player.getWorld().getName())) {
            return new ValidationResult(WorldUiControlError.WORLD_MISMATCH, null);
        }
        if (!isUnitUv(u, v)) {
            return new ValidationResult(WorldUiControlError.INVALID_ARGUMENT, null);
        }

        Vector point = screenPoint(target, target.findScreen(screenId), u, v);
        Location eye = player.getEyeLocation();
        Vector direction = point.clone().subtract(eye.toVector());
        double distance = direction.length();
        if (distance <= 0.0D) {
            return new ValidationResult(WorldUiControlError.NONE, point);
        }
        RayTraceResult trace = player.getWorld().rayTraceBlocks(
                eye, direction.clone().multiply(1.0D / distance), distance, FluidCollisionMode.NEVER, true);
        if (trace != null && trace.getHitPosition().distanceSquared(point) + OCCLUSION_EPSILON_SQUARED < distance * distance) {
            return new ValidationResult(WorldUiControlError.OCCLUDED, point);
        }
        return new ValidationResult(WorldUiControlError.NONE, point);
    }

    public static boolean isUnitUv(float u, float v) {
        return Float.isFinite(u) && Float.isFinite(v) && u >= 0.0F && u <= 1.0F && v >= 0.0F && v <= 1.0F;
    }

    public static Vector screenPoint(ManagedMtvPlayer target, ScreenPeripheralConfigModel screen, float u, float v) {
        if (target == null || screen == null || !isUnitUv(u, v)) {
            throw new IllegalArgumentException("screen transform arguments are invalid");
        }
        var hostRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-target.getYaw()))
                .rotateX((float) Math.toRadians(-target.getPitch()));
        var screenRotation = new Quaternionf(hostRotation)
                .mul(new Quaternionf(screen.getOffsetRx(), screen.getOffsetRy(), screen.getOffsetRz(), screen.getOffsetRw()).normalize());

        var offset = new Vector3f(screen.getOffsetX(), screen.getOffsetY(), screen.getOffsetZ());
        hostRotation.transform(offset);
        var center = new Vector3f((float) target.getX(), (float) target.getY(), (float) target.getZ()).add(offset);

        var planePoint = new Vector3f(
                (u - 0.5F) * Math.max(0.0F, screen.getWidth()),
                (0.5F - v) * Math.max(0.0F, screen.getHeight()),
                0.0F
        );
        screenRotation.transform(planePoint);
        center.add(planePoint);
        return new Vector(center.x(), center.y(), center.z());
    }

    public record ValidationResult(WorldUiControlError error, Vector hitPoint) {
        public boolean accepted() {
            return error == WorldUiControlError.NONE;
        }
    }
}
