package top.tobyprime.mcedia_mtv_plugin.util;

public final class EulerAngleUtil {
    private EulerAngleUtil() {
    }

    /** ZYX Euler degrees → quaternion [w, x, y, z] */
    public static float[] toQuaternion(float rollDeg, float pitchDeg, float yawDeg) {
        double r = Math.toRadians(rollDeg);
        double p = Math.toRadians(pitchDeg);
        double y = Math.toRadians(yawDeg);

        double cr = Math.cos(r / 2), sr = Math.sin(r / 2);
        double cp = Math.cos(p / 2), sp = Math.sin(p / 2);
        double cy = Math.cos(y / 2), sy = Math.sin(y / 2);

        return new float[]{
                (float) (cr * cp * cy + sr * sp * sy), // w
                (float) (cr * cp * sy - sr * sp * cy), // x
                (float) (cr * sp * cy + sr * cp * sy), // y
                (float) (sr * cp * cy - cr * sp * sy)  // z
        };
    }

    /** quaternion [w, x, y, z] → ZYX Euler degrees [roll, pitch, yaw] */
    public static float[] toEuler(float w, float x, float y, float z) {
        double roll = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        double pitch = Math.asin(Math.clamp(2 * (w * y - z * x), -1, 1));
        double yaw = Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));

        return new float[]{
                (float) Math.toDegrees(roll),
                (float) Math.toDegrees(pitch),
                (float) Math.toDegrees(yaw)
        };
    }
}
