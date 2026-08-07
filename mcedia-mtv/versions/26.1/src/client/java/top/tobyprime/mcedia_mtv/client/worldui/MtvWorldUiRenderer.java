package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlStateCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
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
        MtvWorldUiRenderResources.getInstance().setTextureCleanup(MtvWorldUiCoverTextures::clear);
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (camera == null) return;
            Vec3 position = camera.position();
            var vertex = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());
            var pose = context.poseStack().last();
            for (var screen : EntityPlayerManager.getInstance().worldUiScreens()) {
                drawScreen(vertex, context.bufferSource(), pose, position, screen);
            }
        });
    }

    private static void drawScreen(VertexConsumer vertex, MultiBufferSource bufferSource, PoseStack.Pose pose, Vec3 camera, EntityPlayerHandle.WorldUiScreen screen) {
        var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
        var target = new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision());
        if (!PRESENTATION.shouldRender(target)) return;

        if (!PRESENTATION.isExpanded(target)) {
            quad(vertex, pose, camera, screen.plane(), 0.93F, 0.93F, 0.99F, 0.99F, 0xD9242828);
            quad(vertex, pose, camera, screen.plane(), 0.955F, 0.945F, 0.975F, 0.975F, 0xFFF0F0F0);
            drawText(pose, bufferSource, camera, screen.plane(), "+", 0.958F, 0.954F, 0.0014F, 0xFFFFFFFF);
            return;
        }

        quad(vertex, pose, camera, screen.plane(), 0.02F, 0.65F, 0.98F, 0.98F, 0xD0101010);
        drawProgress(vertex, pose, camera, screen.plane(), snapshot);
        drawTransport(vertex, pose, camera, screen.plane());
        float volume = WorldUiControlStateCache.getInstance().state(screen.mtvUuid()) == null ? 1.0F : WorldUiControlStateCache.getInstance().state(screen.mtvUuid()).masterVolume();
        drawVolume(vertex, pose, camera, screen.plane(), volume);
        drawControlLabels(pose, bufferSource, camera, screen.plane(), snapshot);
        if (WorldUiLayout.showsDetails(screen.plane().width(), screen.plane().height())) {
            drawCover(vertex, pose, bufferSource, camera, screen.plane(), snapshot);
            drawMediaInfo(pose, bufferSource, camera, screen.plane(), snapshot);
            if (PRESENTATION.isPlaylistExpanded()) drawPlaylist(vertex, pose, bufferSource, camera, screen.plane(), snapshot);
        }
        quad(vertex, pose, camera, screen.plane(), 0.93F, 0.93F, 0.99F, 0.99F, 0xD9242828);
    }

    private static void drawProgress(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                     ClientChannelPlaybackSnapshot snapshot) {
        quad(vertex, pose, camera, screen, 0.06F, 0.845F, 0.80F, 0.885F, 0xFF373737);
        float progress = progress(snapshot);
        if (progress > 0.0F) quad(vertex, pose, camera, screen, 0.06F, 0.845F, 0.06F + 0.74F * progress, 0.885F, 0xFFE0E0E0);
    }

    private static void drawTransport(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen) {
        quad(vertex, pose, camera, screen, 0.20F, 0.69F, 0.28F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.30F, 0.69F, 0.42F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.44F, 0.69F, 0.56F, 0.79F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.58F, 0.69F, 0.70F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.76F, 0.69F, 0.82F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.77F, 0.71F, 0.81F, 0.72F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.77F, 0.74F, 0.81F, 0.75F, 0xFFE0E0E0);
        quad(vertex, pose, camera, screen, 0.84F, 0.69F, 0.90F, 0.79F, 0xFF333333);
        quad(vertex, pose, camera, screen, 0.90F, 0.69F, 0.96F, 0.79F, 0xFF333333);
    }

    private static void drawVolume(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, float volume) {
        quad(vertex, pose, camera, screen, 0.84F, 0.845F, 0.96F, 0.885F, 0xFF373737);
        quad(vertex, pose, camera, screen, 0.84F, 0.845F, 0.84F + 0.12F * Math.max(0.0F, Math.min(1.0F, volume)), 0.885F, 0xFFE0E0E0);
    }

    private static void drawControlLabels(PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera,
                                          WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        long positionUs = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) positionUs += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        drawText(pose, bufferSource, camera, screen, WorldUiPlaybackPresentation.timeLabel(positionUs, snapshot.resolvedDurationUs()), 0.06F, 0.81F, 0.0012F, 0xFFD0D0D0);
        drawText(pose, bufferSource, camera, screen, Math.round(snapshot.speed() * 10.0F) / 10.0F + "x", 0.215F, 0.73F, 0.0016F, 0xFFE0E0E0);
        drawText(pose, bufferSource, camera, screen, "<", 0.34F, 0.73F, 0.0020F, 0xFFE0E0E0);
        drawText(pose, bufferSource, camera, screen, snapshot.paused() ? ">" : "||", 0.48F, 0.73F, 0.0020F, 0xFF101010);
        drawText(pose, bufferSource, camera, screen, ">", 0.62F, 0.73F, 0.0020F, 0xFFE0E0E0);
        drawText(pose, bufferSource, camera, screen, "Q", 0.775F, 0.73F, 0.0016F, 0xFFE0E0E0);
        drawText(pose, bufferSource, camera, screen, "+", 0.855F, 0.73F, 0.0018F, 0xFFE0E0E0);
        drawText(pose, bufferSource, camera, screen, "M", 0.915F, 0.73F, 0.0016F, 0xFFE0E0E0);
    }

    private static void drawPlaylist(VertexConsumer vertex, PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        quad(vertex, pose, camera, screen, .68F, .04F, .98F, .64F, 0xE0161616);
        quad(vertex, pose, camera, screen, .70F, .05F, .82F, .09F, 0xFF3A3A3A);
        quad(vertex, pose, camera, screen, .77F, .05F, .81F, .09F, 0xFF555555);
        quad(vertex, pose, camera, screen, .82F, .05F, .86F, .09F, 0xFF555555);
        quad(vertex, pose, camera, screen, .88F, .05F, .98F, .09F, 0xFF3A3A3A);
        WorldUiPlaylistCache.getInstance().manifest(snapshot.channelId()).ifPresent(manifest -> {
            int start = PRESENTATION.playlistStart();
            var page = WorldUiPlaylistCache.getInstance().pageAt(snapshot.channelId(), (start / 32) * 32).orElse(null);
            for (int row = 0; row < 7; row++) {
                float top = .10F + row * .08F, bottom = top + .065F;
                int index = start + row;
                int pageIndex = page == null ? -1 : index - page.offset();
                boolean present = pageIndex >= 0 && pageIndex < page.mediaUrls().size();
                boolean current = present && index == manifest.cursor();
                quad(vertex, pose, camera, screen, .70F, top, .98F, bottom, current ? 0xFF666666 : 0xFF292929);
                if (present) {
                    if (page != null) {
                        String mediaUrl = page.mediaUrls().get(pageIndex);
                        drawPlaylistCover(vertex, pose, bufferSource, camera, screen, mediaUrl, .71F, top + .01F, .735F, bottom - .01F);
                        drawMetadataText(pose, bufferSource, camera, screen, mediaUrl, .74F, top + .02F, .0016F, 0xFFE8E8E8, 10);
                    }
                    quad(vertex, pose, camera, screen, .80F, top + .01F, .84F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .85F, top + .01F, .89F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .90F, top + .01F, .94F, bottom - .01F, 0xFF515151);
                    quad(vertex, pose, camera, screen, .95F, top + .01F, .97F, bottom - .01F, 0xFF8A4040);
                }
            }
        });
    }

    private static void drawMediaInfo(PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        drawMetadataText(pose, bufferSource, camera, screen, snapshot.mediaUrl(), .08F, .10F, .0024F, 0xFFF0F0F0, 42);
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(snapshot.mediaUrl());
        if (metadata == null) return;
        drawText(pose, bufferSource, camera, screen, truncate(metadata.author(), 28), .08F, .15F, .0018F, 0xFFB8B8B8);
        drawText(pose, bufferSource, camera, screen, truncate(metadata.description(), 42), .08F, .20F, .0016F, 0xFF9A9A9A);
    }

    private static void drawCover(VertexConsumer fallback, PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera,
                                  WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(snapshot.mediaUrl());
        Identifier textureId = metadata == null ? null : MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) {
            quad(fallback, pose, camera, screen, .04F, .08F, .07F, .24F, 0xFF333333);
            return;
        }
        texturedQuad(bufferSource.getBuffer(RenderTypes.text(textureId)), pose, camera, screen, .04F, .08F, .07F, .24F);
    }

    private static void drawPlaylistCover(VertexConsumer fallback, PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera,
                                          WorldUiScreenRaycast.Screen screen, String mediaUrl, float left, float top, float right, float bottom) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        Identifier textureId = metadata == null ? null : MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) {
            quad(fallback, pose, camera, screen, left, top, right, bottom, 0xFF414141);
            return;
        }
        texturedQuad(bufferSource.getBuffer(RenderTypes.text(textureId)), pose, camera, screen, left, top, right, bottom);
    }

    private static void drawMetadataText(PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         String mediaUrl, float u, float v, float scale, int color, int maxChars) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        if (metadata != null && metadata.status() == MtvMediaMetadata.Status.RESOLVED) {
            drawText(pose, bufferSource, camera, screen, truncate(metadata.title(), maxChars), u, v, scale, color);
        }
    }

    private static void drawText(PoseStack.Pose pose, MultiBufferSource bufferSource, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                 String value, float u, float v, float scale, int color) {
        if (value == null || value.isBlank()) return;
        Vector3f origin = point(screen, u, v);
        Matrix4f matrix = new Matrix4f(pose.pose())
                .translate(origin.x - (float) camera.x, origin.y - (float) camera.y, origin.z - (float) camera.z)
                .rotate(textRotation(screen))
                .scale(scale);
        Minecraft.getInstance().font.drawInBatch(Component.literal(value), 0.0F, 0.0F, color, true,
                matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
    }

    private static Quaternionf textRotation(WorldUiScreenRaycast.Screen screen) {
        Vector3f x = new Vector3f(screen.right()).normalize();
        Vector3f y = new Vector3f(screen.up()).negate().normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().set(x, y, z));
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 1)) + "...";
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

    private static void texturedQuad(VertexConsumer vertex, PoseStack.Pose pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                     float left, float bottom, float right, float top) {
        Vector3f a = point(screen, left, bottom);
        Vector3f b = point(screen, right, bottom);
        Vector3f c = point(screen, right, top);
        Vector3f d = point(screen, left, top);
        vertex.addVertex(pose, a.x - (float) camera.x, a.y - (float) camera.y, a.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(0F, 1F);
        vertex.addVertex(pose, b.x - (float) camera.x, b.y - (float) camera.y, b.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(1F, 1F);
        vertex.addVertex(pose, c.x - (float) camera.x, c.y - (float) camera.y, c.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(1F, 0F);
        vertex.addVertex(pose, d.x - (float) camera.x, d.y - (float) camera.y, d.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(0F, 0F);
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        var normal = new Vector3f(screen.up()).cross(screen.right()).normalize().mul(0.002F);
        return new Vector3f(screen.center())
                .fma((u - 0.5F) * screen.width(), screen.right())
                .fma((0.5F - v) * screen.height(), screen.up())
                .add(normal);
    }
}
