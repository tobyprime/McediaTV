package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlStateCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
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
        MtvWorldUiRenderResources.getInstance().setTextureCleanup(MtvWorldUiCoverTextures::clear);
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
            drawText(collector, pose, camera, screen.plane(), "+", .958F, .954F, .0014F, 0xFFFFFFFF);
            return;
        }
        quad(collector, pose, camera, screen.plane(), .02F, .65F, .98F, .98F, 0xD0101010);
        drawProgress(collector, pose, camera, screen.plane(), snapshot);
        drawTransport(collector, pose, camera, screen.plane());
        float volume = WorldUiControlStateCache.getInstance().state(screen.mtvUuid()) == null ? 1.0F : WorldUiControlStateCache.getInstance().state(screen.mtvUuid()).masterVolume();
        drawVolume(collector, pose, camera, screen.plane(), volume);
        drawControlLabels(collector, pose, camera, screen.plane(), snapshot);
        if (WorldUiLayout.showsDetails(screen.plane().width(), screen.plane().height())) {
            drawCover(collector, pose, camera, screen.plane(), snapshot);
            drawMediaInfo(collector, pose, camera, screen.plane(), snapshot);
            if (PRESENTATION.isPlaylistExpanded()) drawPlaylist(collector, pose, camera, screen.plane(), snapshot);
        }
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
        quad(collector, pose, camera, screen, .20F, .69F, .28F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .30F, .69F, .42F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .44F, .69F, .56F, .79F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .58F, .69F, .70F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .76F, .69F, .82F, .79F, 0xFF333333);
        quad(collector, pose, camera, screen, .77F, .71F, .81F, .72F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .77F, .74F, .81F, .75F, 0xFFE0E0E0);
        quad(collector, pose, camera, screen, .90F, .69F, .96F, .79F, 0xFF333333);
    }

    private static void drawVolume(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, float volume) {
        quad(collector, pose, camera, screen, .84F, .72F, .96F, .76F, 0xFF373737);
        quad(collector, pose, camera, screen, .84F, .72F, .84F + .12F * Math.max(0.0F, Math.min(1.0F, volume)), .76F, 0xFFE0E0E0);
    }

    private static void drawControlLabels(SubmitNodeCollector collector, PoseStack pose, Vec3 camera,
                                          WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        long positionUs = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) positionUs += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        drawText(collector, pose, camera, screen, WorldUiPlaybackPresentation.timeLabel(positionUs, snapshot.resolvedDurationUs()), .06F, .81F, .0012F, 0xFFD0D0D0);
        drawText(collector, pose, camera, screen, Math.round(snapshot.speed() * 10.0F) / 10.0F + "x", .215F, .73F, .0016F, 0xFFE0E0E0);
        drawText(collector, pose, camera, screen, "<", .34F, .73F, .0020F, 0xFFE0E0E0);
        drawText(collector, pose, camera, screen, snapshot.paused() ? ">" : "||", .48F, .73F, .0020F, 0xFF101010);
        drawText(collector, pose, camera, screen, ">", .62F, .73F, .0020F, 0xFFE0E0E0);
        drawText(collector, pose, camera, screen, "Q", .775F, .73F, .0016F, 0xFFE0E0E0);
        drawText(collector, pose, camera, screen, "+", .855F, .73F, .0018F, 0xFFE0E0E0);
        drawText(collector, pose, camera, screen, "M", .915F, .73F, .0016F, 0xFFE0E0E0);
    }

    private static void drawPlaylist(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        quad(collector, pose, camera, screen, .68F, .04F, .98F, .64F, 0xE0161616);
        quad(collector, pose, camera, screen, .70F, .05F, .82F, .09F, 0xFF3A3A3A);
        quad(collector, pose, camera, screen, .77F, .05F, .81F, .09F, 0xFF555555);
        quad(collector, pose, camera, screen, .82F, .05F, .86F, .09F, 0xFF555555);
        quad(collector, pose, camera, screen, .88F, .05F, .98F, .09F, 0xFF3A3A3A);
        WorldUiPlaylistCache.getInstance().manifest(snapshot.channelId()).ifPresent(manifest -> {
            int start = PRESENTATION.playlistStart();
            var page = WorldUiPlaylistCache.getInstance().pageAt(snapshot.channelId(), (start / 32) * 32).orElse(null);
            for (int row = 0; row < 7; row++) {
                float top = .10F + row * .08F, bottom = top + .065F;
                int index = start + row;
                int pageIndex = page == null ? -1 : index - page.offset();
                boolean present = pageIndex >= 0 && pageIndex < page.mediaUrls().size();
                boolean current = present && index == manifest.cursor();
                quad(collector, pose, camera, screen, .70F, top, .98F, bottom, current ? 0xFF666666 : 0xFF292929);
                if (present) {
                    if (page != null) {
                        String mediaUrl = page.mediaUrls().get(pageIndex);
                        drawPlaylistCover(collector, pose, camera, screen, mediaUrl, .71F, top + .01F, .735F, bottom - .01F);
                        drawMetadataText(collector, pose, camera, screen, mediaUrl, .74F, top + .02F, .0016F, 0xFFE8E8E8, 10);
                    }
                    quad(collector, pose, camera, screen, .80F, top + .01F, .84F, bottom - .01F, 0xFF515151);
                    quad(collector, pose, camera, screen, .85F, top + .01F, .89F, bottom - .01F, 0xFF515151);
                    quad(collector, pose, camera, screen, .90F, top + .01F, .94F, bottom - .01F, 0xFF515151);
                    quad(collector, pose, camera, screen, .95F, top + .01F, .97F, bottom - .01F, 0xFF8A4040);
                }
            }
        });
    }

    private static void drawMediaInfo(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        drawMetadataText(collector, pose, camera, screen, snapshot.mediaUrl(), .08F, .10F, .0024F, 0xFFF0F0F0, 42);
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(snapshot.mediaUrl());
        if (metadata == null) return;
        drawText(collector, pose, camera, screen, truncate(metadata.author(), 28), .08F, .15F, .0018F, 0xFFB8B8B8);
        drawText(collector, pose, camera, screen, truncate(metadata.description(), 42), .08F, .20F, .0016F, 0xFF9A9A9A);
    }

    private static void drawCover(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                  ClientChannelPlaybackSnapshot snapshot) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(snapshot.mediaUrl());
        Identifier textureId = metadata == null ? null : MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) {
            quad(collector, pose, camera, screen, .04F, .08F, .07F, .24F, 0xFF333333);
            return;
        }
        texturedQuad(collector, pose, camera, screen, textureId, .04F, .08F, .07F, .24F);
    }

    private static void drawPlaylistCover(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                          String mediaUrl, float left, float top, float right, float bottom) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        Identifier textureId = metadata == null ? null : MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) {
            quad(collector, pose, camera, screen, left, top, right, bottom, 0xFF414141);
            return;
        }
        texturedQuad(collector, pose, camera, screen, textureId, left, top, right, bottom);
    }

    private static void drawMetadataText(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         String mediaUrl, float u, float v, float scale, int color, int maxChars) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        if (metadata != null && metadata.status() == MtvMediaMetadata.Status.RESOLVED) {
            drawText(collector, pose, camera, screen, truncate(metadata.title(), maxChars), u, v, scale, color);
        }
    }

    private static void drawText(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                 String value, float u, float v, float scale, int color) {
        if (value == null || value.isBlank()) return;
        Vector3f origin = point(screen, u, v);
        pose.pushPose();
        pose.translate(origin.x - (float) camera.x, origin.y - (float) camera.y, origin.z - (float) camera.z);
        pose.mulPose(textRotation(screen));
        pose.scale(scale, scale, scale);
        collector.submitText(pose, 0.0F, 0.0F, Component.literal(value).getVisualOrderText(), true,
                Font.DisplayMode.SEE_THROUGH, color, 0, 15728880, 0);
        pose.popPose();
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

    private static void texturedQuad(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                     Identifier textureId, float left, float bottom, float right, float top) {
        Vector3f a = point(screen, left, bottom), b = point(screen, right, bottom), c = point(screen, right, top), d = point(screen, left, top);
        collector.submitCustomGeometry(poseStack, RenderTypes.text(textureId), (pose, vertex) -> {
            vertex.addVertex(pose, a.x - (float) camera.x, a.y - (float) camera.y, a.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(0F, 1F);
            vertex.addVertex(pose, b.x - (float) camera.x, b.y - (float) camera.y, b.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(1F, 1F);
            vertex.addVertex(pose, c.x - (float) camera.x, c.y - (float) camera.y, c.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(1F, 0F);
            vertex.addVertex(pose, d.x - (float) camera.x, d.y - (float) camera.y, d.z - (float) camera.z).setColor(0xFFFFFFFF).setUv(0F, 0F);
        });
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        var normal = new Vector3f(screen.up()).cross(screen.right()).normalize().mul(.002F);
        return new Vector3f(screen.center()).fma((u - .5F) * screen.width(), screen.right()).fma((.5F - v) * screen.height(), screen.up()).add(normal);
    }
}
