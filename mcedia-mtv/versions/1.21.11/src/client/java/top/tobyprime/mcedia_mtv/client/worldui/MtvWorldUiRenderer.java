package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** 1.21.11 world-plane MPV-style controls using Fabric's world render event. */
public final class MtvWorldUiRenderer {
    private static final WorldUiPresentationState PRESENTATION = new WorldUiPresentationState();
    private static boolean initialized;

    private MtvWorldUiRenderer() { }

    public static WorldUiPresentationState presentation() { return PRESENTATION; }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (camera == null) return;
            Vec3 position = camera.position();
            var vertex = context.consumers().getBuffer(RenderTypes.debugFilledBox());
            var pose = context.matrices().last();
            for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) drawScreen(vertex, pose, position, screen);
        });
    }

    private static void drawScreen(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, EntityPlayerHandle.WorldUiScreen screen) {
        var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
        var target = new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision());
        if (!PRESENTATION.shouldRender(target)) return;
        if (!PRESENTATION.isExpanded(target)) {
            quad(vertex, pose, camera, screen.plane(), .93F, .93F, .99F, .99F, 0xD9242828);
            quad(vertex, pose, camera, screen.plane(), .955F, .945F, .975F, .975F, 0xFFF0F0F0);
            return;
        }
        quad(vertex, pose, camera, screen.plane(), .02F, .65F, .98F, .98F, 0xD0101010);
        drawProgress(vertex, pose, camera, screen.plane(), snapshot);
        drawTransport(vertex, pose, camera, screen.plane());
        drawVolume(vertex, pose, camera, screen.plane());
        if (PRESENTATION.isPlaylistExpanded()) drawPlaylist(vertex, pose, camera, screen.plane(), snapshot);
        quad(vertex, pose, camera, screen.plane(), .93F, .93F, .99F, .99F, 0xD9242828);
    }

    private static void drawProgress(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        quad(vertex, pose, camera, screen, .06F, .845F, .80F, .885F, 0xFF373737);
        float progress = progress(snapshot);
        if (progress > 0F) quad(vertex, pose, camera, screen, .06F, .845F, .06F + .74F * progress, .885F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, .84F, .845F, .96F, .885F, 0xFF373737);
        quad(vertex, pose, camera, screen, .84F, .845F, .93F, .885F, 0xFFE0E0E0);
    }

    private static void drawTransport(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(vertex, pose, camera, screen, .30F, .69F, .42F, .79F, 0xFF333333);
        quad(vertex, pose, camera, screen, .44F, .69F, .56F, .79F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, .58F, .69F, .70F, .79F, 0xFF333333);
        quad(vertex, pose, camera, screen, .76F, .69F, .82F, .79F, 0xFF333333);
        quad(vertex, pose, camera, screen, .77F, .71F, .81F, .72F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, .77F, .74F, .81F, .75F, 0xFFE0E0E0);
    }

    private static void drawVolume(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(vertex, pose, camera, screen, .84F, .72F, .96F, .76F, 0xFF373737);
        quad(vertex, pose, camera, screen, .84F, .72F, .93F, .76F, 0xFFE0E0E0);
    }

    private static void drawPlaylist(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        quad(vertex, pose, camera, screen, .68F, .04F, .98F, .64F, 0xE0161616);
        quad(vertex, pose, camera, screen, .70F, .05F, .82F, .09F, 0xFF3A3A3A);
        quad(vertex, pose, camera, screen, .88F, .05F, .98F, .09F, 0xFF3A3A3A);
        WorldUiPlaylistCache.getInstance().manifest(snapshot.channelId()).ifPresent(manifest -> {
            var page = WorldUiPlaylistCache.getInstance().pageAt(snapshot.channelId(), 0).orElse(null);
            for (int row = 0; row < 7; row++) {
                float top = .10F + row * .08F, bottom = top + .065F;
                boolean present = page != null && row < page.mediaUrls().size();
                boolean current = present && row == manifest.cursor();
                quad(vertex, pose, camera, screen, .70F, top, .98F, bottom, current ? 0xFF666666 : 0xFF292929);
                if (present) {
                    quad(vertex, pose, camera, screen, .80F, top + .01F, .84F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .85F, top + .01F, .89F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .90F, top + .01F, .94F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .95F, top + .01F, .97F, bottom - .01F, 0xFF8A4040);
                }
            }
        });
    }

    private static float progress(ClientChannelPlaybackSnapshot snapshot) {
        if (snapshot.resolvedDurationUs() <= 0L) return 0F;
        long position = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) position += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        return Math.max(0F, Math.min(1F, (float) position / snapshot.resolvedDurationUs()));
    }

    private static void quad(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, float left, float bottom, float right, float top, int color) {
        Vector3f a = point(screen, left, bottom), b = point(screen, right, bottom), c = point(screen, right, top), d = point(screen, left, top);
        vertex.addVertex(pose, a.x - (float) camera.x, a.y - (float) camera.y, a.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, b.x - (float) camera.x, b.y - (float) camera.y, b.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, c.x - (float) camera.x, c.y - (float) camera.y, c.z - (float) camera.z).setColor(color);
        vertex.addVertex(pose, d.x - (float) camera.x, d.y - (float) camera.y, d.z - (float) camera.z).setColor(color);
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        var normal = new Vector3f(screen.up()).cross(screen.right()).normalize().mul(.002F);
        return new Vector3f(screen.center()).fma((u - .5F) * screen.width(), screen.right()).fma((.5F - v) * screen.height(), screen.up()).add(normal);
    }
}
