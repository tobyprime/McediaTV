package top.tobyprime.mcedia_mtv.client.entityplayer;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks the world-UI plane geometry to the core video quad. Core's
 * {@code PlayerScreenEntityRenderer.submit} offsets the quad by half its height
 * along the world Y axis and only then applies the rotation (PoseStack.translate
 * then mulPose), so the quad pivots around {@code anchor + (0, h/2, 0)} regardless
 * of screen orientation. The UI plane must share that centre — using the rotated
 * {@code up} instead detaches the UI from the video whenever the screen has
 * pitch/roll (e.g. tilted -90°).
 */
class WorldUiPlaneGeometryTest {

    private static final Vector3f ANCHOR = new Vector3f(10.0F, 64.0F, -20.0F);
    private static final float WIDTH = 1.6F;
    private static final float HEIGHT = 0.9F;

    private static List<Quaternionf> rotations() {
        return List.of(
                new Quaternionf(),
                new Quaternionf().rotateY((float) Math.toRadians(90)),
                new Quaternionf().rotateY((float) Math.toRadians(180)),
                new Quaternionf().rotateY((float) Math.toRadians(-90)),
                new Quaternionf().rotateX((float) Math.toRadians(90)),
                new Quaternionf().rotateX((float) Math.toRadians(-90)),
                new Quaternionf().rotateX((float) Math.toRadians(180)),
                new Quaternionf().rotateZ((float) Math.toRadians(-90))
        );
    }

    /** Video quad corner in world space: replicate core's translate(0, h/2, 0) then mulPose(R). */
    private static Vector3f videoCorner(Quaternionf rotation, float lx, float ly) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0, HEIGHT * 0.5F, 0);
        matrix.mul(new Matrix4f().rotation(rotation));
        return matrix.transformPosition(new Vector3f(lx, ly, 0)).add(ANCHOR);
    }

    /** UI plane corner with the fixed centre (anchor + world-Y half height). */
    private static Vector3f uiCorner(Quaternionf rotation, float u, float v) {
        var right = rotation.transform(new Vector3f(1, 0, 0));
        var up = rotation.transform(new Vector3f(0, 1, 0));
        var center = new Vector3f(ANCHOR).add(0, HEIGHT * 0.5F, 0);
        return new Vector3f(center)
                .fma((u - 0.5F) * WIDTH, right)
                .fma((0.5F - v) * HEIGHT, up);
    }

    @Test
    void uiPlaneOverlapsVideoQuadAtEveryRotation() {
        // 两条等价计算路径存在 ~1e-6 的浮点差异(fma 是否收缩取决于 JIT),用距离容差而非字符串比较。
        for (var rotation : rotations()) {
            assertEquals(0.0F, videoCorner(rotation, -WIDTH * 0.5F, -HEIGHT * 0.5F).distance(uiCorner(rotation, 0, 1)), 1e-3F,
                    "bottom-left " + rotation);
            assertEquals(0.0F, videoCorner(rotation, -WIDTH * 0.5F, HEIGHT * 0.5F).distance(uiCorner(rotation, 0, 0)), 1e-3F,
                    "top-left " + rotation);
            assertEquals(0.0F, videoCorner(rotation, WIDTH * 0.5F, -HEIGHT * 0.5F).distance(uiCorner(rotation, 1, 1)), 1e-3F,
                    "bottom-right " + rotation);
            assertEquals(0.0F, videoCorner(rotation, WIDTH * 0.5F, HEIGHT * 0.5F).distance(uiCorner(rotation, 1, 0)), 1e-3F,
                    "top-right " + rotation);
        }
    }
}
