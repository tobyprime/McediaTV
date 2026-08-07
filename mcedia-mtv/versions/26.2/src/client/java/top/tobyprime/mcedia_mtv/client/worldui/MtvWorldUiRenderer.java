package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** 26.2 world-plane MPV-style controls drawn through the normal depth pipeline. */
public final class MtvWorldUiRenderer {
    private static final WorldUiPresentationState PRESENTATION = new WorldUiPresentationState();
    private static boolean initialized;

    private MtvWorldUiRenderer() { }

    public static WorldUiPresentationState presentation() { return PRESENTATION; }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            var camera = Minecraft.getInstance().gameRenderer.mainCamera();
            if (camera == null) return;
            Vec3 position = camera.position();
            for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) {
                drawScreen(context.submitNodeCollector(), context.poseStack(), position, screen);
            }
        });
    }

    private static void drawScreen(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, EntityPlayerHandle.WorldUiScreen screen) {
        var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
        var target = new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision());
        if (!PRESENTATION.shouldRender(target)) return;
        if (!PRESENTATION.isExpanded(target)) {
            quad(collector, pose, camera, screen.plane(), .93F, .93F, .99F, .99F, 0xD9242828);
            quad(collector, pose, camera, screen.plane(), .955F, .945F, .975F, .975F, 0xFFF0F0F0);
            return;
        }
        quad(collector, pose, camera, screen.plane(), .02F, .65F, .98F, .98F, 0xD0101010);
        drawProgress(collector, pose, camera, screen.plane(), snapshot);
        drawTransport(collector, pose, camera, screen.plane());
        drawVolume(collector, pose, camera, screen.plane());
        quad(collector, pose, camera, screen.plane(), .93F, .93F, .99F, .99F, 0xD9242828);
    }

    private static void drawProgress(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        quad(collector, pose, camera, screen, .06F, .845F, .80F, .885F, 0xFF373737);
        float progress = progress(snapshot);
        if (progress > 0F) quad(collector, pose, camera, screen, .06F, .845F, .06F + .74F * progress, .885F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .84F, .845F, .96F, .885F, 0xFF373737);
        quad(collector, pose, camera, screen, .84F, .845F, .93F, .885F, 0xFFE0E0E0);
    }

    private static void drawTransport(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(collector, pose, camera, screen, .30F, .69F, .42F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .44F, .69F, .56F, .79F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .58F, .69F, .70F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .76F, .69F, .82F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .77F, .71F, .81F, .72F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .77F, .74F, .81F, .75F, 0xFFE0E0E0);
    }

    private static void drawVolume(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(collector, pose, camera, screen, .84F, .72F, .96F, .76F, 0xFF373737);
        quad(collector, pose, camera, screen, .84F, .72F, .93F, .76F, 0xFFE0E0E0);
    }

    private static float progress(ClientChannelPlaybackSnapshot snapshot) {
        if (snapshot.resolvedDurationUs() <= 0L) return 0F;
        long position = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) position += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        return Math.max(0F, Math.min(1F, (float) position / snapshot.resolvedDurationUs()));
    }

    private static void quad(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, WorldUiScreenRaycast.Screen screen, float left, float bottom, float right, float top, int color) {
        Vector3f a = point(screen, left, bottom), b = point(screen, right, bottom), c = point(screen, right, top), d = point(screen, left, top);
        collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, vertex) -> {
            vertex.addVertex(pose, a.x - (float) camera.x, a.y - (float) camera.y, a.z - (float) camera.z).setColor(color);
            vertex.addVertex(pose, b.x - (float) camera.x, b.y - (float) camera.y, b.z - (float) camera.z).setColor(color);
            vertex.addVertex(pose, c.x - (float) camera.x, c.y - (float) camera.y, c.z - (float) camera.z).setColor(color);
            vertex.addVertex(pose, d.x - (float) camera.x, d.y - (float) camera.y, d.z - (float) camera.z).setColor(color);
        });
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        var normal = new Vector3f(screen.up()).cross(screen.right()).normalize().mul(.002F);
        return new Vector3f(screen.center()).fma((u - .5F) * screen.width(), screen.right()).fma((.5F - v) * screen.height(), screen.up()).add(normal);
    }
}
