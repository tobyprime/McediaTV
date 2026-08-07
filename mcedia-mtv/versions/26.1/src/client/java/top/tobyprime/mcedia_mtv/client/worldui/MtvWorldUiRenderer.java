package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** 26.1 world-plane MPV-style controls drawn through the normal depth pipeline. */
public final class MtvWorldUiRenderer {
    private static final WorldUiPresentationState PRESENTATION = new WorldUiPresentationState();
    private static boolean initialized;

    private MtvWorldUiRenderer() {
    }

    public static WorldUiPresentationState presentation() {
        return PRESENTATION;
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (camera == null) return;
            Vec3 position = camera.position();
            var vertex = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());
            var pose = context.poseStack().last();
            for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) {
                drawScreen(vertex, pose, position, screen);
            }
        });
    }

    private static void drawScreen(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, EntityPlayerHandle.WorldUiScreen screen) {
        var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
        var target = new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision());
        if (!PRESENTATION.shouldRender(target)) return;

        if (!PRESENTATION.isExpanded(target)) {
            quad(vertex, pose, camera, screen.plane(), 0.93F, 0.93F, 0.99F, 0.99F, 0xD9242828);
            quad(vertex, pose, camera, screen.plane(), 0.955F, 0.945F, 0.975F, 0.975F, 0xFFF0F0F0);
            return;
        }

        quad(vertex, pose, camera, screen.plane(), 0.02F, 0.65F, 0.98F, 0.98F, 0xD0101010);
        drawProgress(vertex, pose, camera, screen.plane(), snapshot);
        drawTransport(vertex, pose, camera, screen.plane());
        drawVolume(vertex, pose, camera, screen.plane());
        quad(vertex, pose, camera, screen.plane(), 0.93F, 0.93F, 0.99F, 0.99F, 0xD9242828);
    }

    private static void drawProgress(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                     ClientChannelPlaybackSnapshot snapshot) {
        quad(vertex, pose, camera, screen, 0.06F, 0.845F, 0.80F, 0.885F, 0xFF373737);
        float progress = progress(snapshot);
        if (progress > 0.0F) quad(vertex, pose, camera, screen, 0.06F, 0.845F, 0.06F + 0.74F * progress, 0.885F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.84F, 0.845F, 0.96F, 0.885F, 0xFF373737);
        quad(vertex, pose, camera, screen, 0.84F, 0.845F, 0.93F, 0.885F, 0xFFE0E0E0);
    }

    private static void drawTransport(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(vertex, pose, camera, screen, 0.30F, 0.69F, 0.42F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.44F, 0.69F, 0.56F, 0.79F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.58F, 0.69F, 0.70F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.76F, 0.69F, 0.82F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.77F, 0.71F, 0.81F, 0.72F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.77F, 0.74F, 0.81F, 0.75F, 0xFFE0E0E0);
    }

    private static void drawVolume(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(vertex, pose, camera, screen, 0.84F, 0.72F, 0.96F, 0.76F, 0xFF373737);
        quad(vertex, pose, camera, screen, 0.84F, 0.72F, 0.93F, 0.76F, 0xFFE0E0E0);
    }

    private static float progress(ClientChannelPlaybackSnapshot snapshot) {
        if (snapshot.resolvedDurationUs() <= 0L) return 0.0F;
        long position = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) position += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        return Math.max(0.0F, Math.min(1.0F, (float) position / snapshot.resolvedDurationUs()));
    }

    private static void quad(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                             float left, float bottom, float right, float top, int color) {
        Vector3f a = point(screen, left, bottom);
        Vector3f b = point(screen, right, bottom);
        Vector3f c = point(screen, right, top);
        Vector3f d = point(screen, left, top);
        vertex.addVertex(pose, a.x - (float) camera.x, a.y - (float) camera.y, a.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, b.x - (float) camera.x, b.y - (float) camera.y, b.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, c.x - (float) camera.x, c.y - (float) camera.y, c.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, d.x - (float) camera.x, d.y - (float) camera.y, d.z - (float) camera.z).setColor(color);
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        var normal = new Vector3f(screen.up()).cross(screen.right()).normalize().mul(0.002F);
        return new Vector3f(screen.center())
                .fma((u - 0.5F) * screen.width(), screen.right())
                .fma((0.5F - v) * screen.height(), screen.up())
                .add(normal);
    }
}
