package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import top.tobyprime.mcedia_core.client.renderer.PlayerScreenEntityRenderer;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;
import top.tobyprime.mcedia_mtv.client.entityplayer.MtvScreenPeripheral;

/** 1.21.11 entry shim: submits the video layer from the level renderer's entity pass; all control drawing lives in {@link WorldUiRenderer}. */
public final class MtvWorldUiRenderer {
    private static boolean initialized;

    private MtvWorldUiRenderer() { }

    public static WorldUiPresentationState presentation() {
        return WorldUiRenderer.presentation();
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        WorldUiRenderer.initializeResources();
    }

    /**
     * Invoked from {@code MixinLevelRenderer} after entities are submitted, so the
     * media screen layer (video) and its controls share the level renderer's submit
     * collector and reliably draw in front of the translucent video quad.
     */
    public static void onLevelSubmit(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector) {
        var cameraRenderState = levelRenderState.cameraRenderState;
        Vec3 camera = cameraRenderState.pos;
        for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) {
            if (!screen.peripheral().isAlive()) continue;
            submitVideoLayer(submitNodeCollector, poseStack, cameraRenderState, camera, screen.peripheral());
            WorldUiRenderer.drawControls(submitNodeCollector, poseStack, camera, screen);
        }
    }

    /** Submits the media screen layer (video, danmaku, status); needs the version-specific camera state. */
    private static void submitVideoLayer(SubmitNodeCollector collector, PoseStack pose, CameraRenderState cameraRenderState,
                                         Vec3 camera, MtvScreenPeripheral peripheral) {
        var state = PlayerScreenEntityRenderer.createRenderState();
        WorldUiRenderer.fillVideoState(peripheral, state);
        var pos = peripheral.getPosition();
        pose.pushPose();
        pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        PlayerScreenEntityRenderer.submit(state, pose, collector, cameraRenderState);
        pose.popPose();
    }
}
