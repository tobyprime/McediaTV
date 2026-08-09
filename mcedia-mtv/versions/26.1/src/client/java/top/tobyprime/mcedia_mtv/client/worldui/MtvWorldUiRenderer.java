package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import top.tobyprime.mcedia_core.client.renderer.PlayerScreenEntityRenderer;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;
import top.tobyprime.mcedia_mtv.client.entityplayer.MtvScreenPeripheral;

/** 26.1 entry shim: hooks the level render pass and submits the video layer; all control drawing lives in {@link WorldUiRenderer}. */
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
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (camera == null) return;
            Vec3 position = camera.position();
            var collector = context.submitNodeCollector();
            var pose = context.poseStack();
            var cameraRenderState = context.levelState().cameraRenderState;
            for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) {
                if (!screen.peripheral().isAlive()) continue;
                submitVideoLayer(collector, pose, cameraRenderState, position, screen.peripheral());
                WorldUiRenderer.drawControls(collector, pose, position, screen);
            }
        });
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
