package top.tobyprime.mcedia_mtv.client.worldui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import top.tobyprime.mcedia.api.player.MediaPlay;
import top.tobyprime.mcedia.api.player.PlaybackState;
import top.tobyprime.mcedia_core.client.renderer.McediaRenderTypes;
import top.tobyprime.mcedia_core.client.renderer.PlayerScreenEntityRenderer;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackSnapshot;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistManifest;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlState;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlStateCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadata;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaMetadataCache;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCover;
import top.tobyprime.mcedia_mtv.client.metadata.MtvMediaCoverCache;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.MtvScreenPeripheral;

/**
 * Version-independent world-plane control drawing shared by every MC version's
 * thin {@code MtvWorldUiRenderer} shim.  Only the video-layer submission (which
 * needs the version-specific {@code CameraRenderState}) and the render-pass entry
 * point live in the per-version renderer; everything painted on top of the video
 * lives here so UI changes are written once.
 */
public final class WorldUiRenderer {
    private static final WorldUiPresentationState PRESENTATION = new WorldUiPresentationState();
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath("mcedia", "textures/gui/white.png");
    private static final int COLOR_BUTTON = 0xFF2A2A2A;
    private static final int COLOR_BUTTON_HOVER = 0xFF4A4A4A;
    private static final int COLOR_PLAY = 0xFFE8E8E8;
    private static final int COLOR_PLAY_HOVER = 0xFFFFFFFF;
    private static final int COLOR_TRACK = 0xFF3A3A3A;
    private static final int COLOR_FILL = 0xFFE0E0E0;
    private static final int COLOR_THUMB = 0xFFFFFFFF;
    /** Reference screen height that the text scales were tuned for; text scales with screen height. */
    private static final float TEXT_REFERENCE_HEIGHT = 0.9F;
    /** Icon identifiers are drawn every frame; cache them so no per-frame Identifier/String allocation happens. */
    private static final java.util.Map<String, Identifier> ICONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Tooltip pill height: reference screen height; the pill is drawn at a 0.0016 text scale. */
    private static final float TOOLTIP_SCALE = 0.0016F;

    private WorldUiRenderer() { }

    public static WorldUiPresentationState presentation() {
        return PRESENTATION;
    }

    /** Wires shared render resources; called once by each version's renderer entry. */
    public static void initializeResources() {
        MtvWorldUiRenderResources.getInstance().setTextureCleanup(MtvWorldUiCoverTextures::clear);
    }

    /** Fills core's screen render state from an MTV peripheral; version-stable so it is shared. */
    public static void fillVideoState(MtvScreenPeripheral peripheral, PlayerScreenEntityRenderer.State state) {
        state.worldRotation.set(peripheral.getWorldRotation());
        state.width = peripheral.getScreenWidth();
        state.height = peripheral.getScreenHeight();
        state.fillMode = peripheral.getFillMode();
        state.backgroundTextureId = peripheral.getBackgroundTextureId();
        state.lightCoords = applyMinimumBrightness(peripheral.getMinBrightness());
        state.media = peripheral.getMediaPlay();
        state.danmakuSession = peripheral.getDanmakuSession();
        state.danmakuVisible = peripheral.isDanmakuVisible();
        state.progressBarVisible = peripheral.isProgressBarVisible();
        state.statusText = localizedStatus(peripheral.getPlaybackState());
        state.playbackState = peripheral.getPlaybackState();
        state.errorMessage = peripheral.getErrorMessage();
        if (state.media != null) {
            long duration = state.media.getDuration();
            state.progress = duration > 0L ? (float) state.media.getEstimatedTime() / (float) duration : 0.0F;
        } else {
            state.progress = 0.0F;
        }
        var texture = peripheral.getTexture();
        if (texture == null) {
            state.textureId = MissingTextureAtlasSprite.getLocation();
            state.textureWidth = 0;
            state.textureHeight = 0;
        } else {
            state.textureId = texture.getTextureId();
            state.textureWidth = texture.getTextureWidth();
            state.textureHeight = texture.getTextureHeight();
        }
    }

    /** Draws the control UI for one screen.  The video layer must already be submitted by the version shim. */
    public static void drawControls(SubmitNodeCollector collector, PoseStack pose, Vec3 camera,
                                    EntityPlayerHandle.WorldUiScreen screen) {
        var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
        var target = new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision());
        if (!PRESENTATION.shouldRender(target)) return;
        WorldUiScreenRaycast.Screen plane = screen.plane();
        // The whole UI is drawn in the screen's local frame: translate to the camera-relative
        // plane centre once, then submit small local-coordinate vertices. At large world
        // coordinates (10000+) absolute float positions lose the precision needed to keep the
        // UI in front of the video quad and to separate the UI's own z-layers; the video layer
        // is translated the same way (core shifts the quad by world-Y half height then rotates),
        // so the two frames stay aligned. Quad submission also moves to order(1) so the controls
        // paint after the order(0) video layer regardless of distance sorting.
        pose.pushPose();
        try {
            pose.translate(
                    (float) (plane.center().x - camera.x),
                    (float) (plane.center().y - camera.y),
                    (float) (plane.center().z - camera.z)
            );
            if (!PRESENTATION.isExpanded(target)) {
                quadZ(collector, pose, camera, plane, .93F, .93F, .99F, .99F, 0xFF282828, 0.003F);
                drawIconCentered(collector, pose, camera, plane, icon("plus"), .93F, .93F, .99F, .99F, 0xFFFFFFFF);
                return;
            }
            var media = screen.peripheral().getMediaPlay();
            drawMediaHeader(collector, pose, camera, plane, snapshot);
            WorldUiHit hit = PRESENTATION.hit();
            WorldUiHit.Kind hovered = hit.kind();
            WorldUiControlState controlState = WorldUiControlStateCache.getInstance().state(screen.mtvUuid(), screen.screenId());
            float volume = controlState == null ? 1.0F : controlState.masterVolume();
            drawLeftPanel(collector, pose, camera, plane,
                    controlState == null ? 8 : controlState.brightness(),
                    volume,
                    controlState == null || controlState.danmakuVisible(),
                    hovered);
            drawProgress(collector, pose, camera, plane, media);
            drawTransport(collector, pose, camera, plane, hovered);
            drawControlLabels(collector, pose, camera, plane, media, snapshot, volume);
            if (WorldUiLayout.showsDetails(plane.width(), plane.height())) {
                drawCover(collector, pose, camera, plane, snapshot);
                if (PRESENTATION.isPlaylistExpanded()) drawPlaylist(collector, pose, camera, plane, snapshot, hit);
            }
            drawTooltip(collector, pose, camera, plane, hit, snapshot);
        } finally {
            pose.popPose();
        }
    }

    /** Left-side vertical brightness/volume bars with a danmaku toggle below. */
    private static void drawLeftPanel(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                      int brightness, float volume, boolean danmakuVisible, WorldUiHit.Kind hovered) {
        drawIconCentered(collector, pose, camera, screen, icon("brightness"), .035F, .24F, .065F, .28F, 0xFFDDDDDD, 0.005F);
        drawVerticalBar(collector, pose, camera, screen, .035F, .065F, .30F, .58F,
                brightness / 15.0F, hovered == WorldUiHit.Kind.BRIGHTNESS);
        drawIconCentered(collector, pose, camera, screen, icon(volume <= 0.01F ? "volume-off" : "volume-high"), .075F, .24F, .105F, .28F, 0xFFDDDDDD, 0.005F);
        drawVerticalBar(collector, pose, camera, screen, .075F, .105F, .30F, .58F,
                volume, hovered == WorldUiHit.Kind.VOLUME);
        quadZ(collector, pose, camera, screen, .035F, .60F, .105F, .68F,
                hovered == WorldUiHit.Kind.DANMAKU ? COLOR_BUTTON_HOVER : (danmakuVisible ? 0xFF3A5A3A : COLOR_BUTTON), 0.003F);
        drawIconCentered(collector, pose, camera, screen, icon("danmaku"), .035F, .60F, .105F, .68F,
                danmakuVisible ? 0xFFFFFFFF : 0xFF666666, 0.005F);
    }

    /** A vertical track filled bottom-up with a thumb, value in 0..1. */
    private static void drawVerticalBar(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                        float left, float right, float top, float bottom, float value, boolean hovered) {
        quadZ(collector, pose, camera, screen, left, top, right, bottom,
                hovered ? COLOR_BUTTON_HOVER : COLOR_TRACK, 0.004F);
        float fill = Math.max(0.0F, Math.min(1.0F, value));
        float fillTop = bottom - (bottom - top) * fill;
        if (fill > 0F) {
            quadZ(collector, pose, camera, screen, left, fillTop, right, bottom, COLOR_FILL, 0.005F);
        }
        quadZ(collector, pose, camera, screen, left - 0.006F, fillTop - 0.008F, right + 0.006F, fillTop + 0.008F, COLOR_THUMB, 0.006F);
    }

    private static void drawProgress(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, MediaPlay media) {
        quadZ(collector, pose, camera, screen, .16F, .945F, .96F, .965F, COLOR_TRACK, 0.004F);
        float progress = progress(media);
        if (progress > 0F) {
            float endU = .16F + .80F * progress;
            quadZ(collector, pose, camera, screen, .16F, .945F, endU, .965F, COLOR_FILL, 0.005F);
            quadZ(collector, pose, camera, screen, endU - 0.008F, .940F, endU + 0.008F, .970F, COLOR_THUMB, 0.006F);
        }
    }

    private static void drawTransport(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen, WorldUiHit.Kind hovered) {
        quadZ(collector, pose, camera, screen, .16F, .82F, .24F, .90F, hovered == WorldUiHit.Kind.SPEED ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
        quadZ(collector, pose, camera, screen, .26F, .82F, .34F, .90F, hovered == WorldUiHit.Kind.PREVIOUS ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
        quadZ(collector, pose, camera, screen, .36F, .82F, .44F, .90F, hovered == WorldUiHit.Kind.TOGGLE_PAUSE ? COLOR_PLAY_HOVER : COLOR_PLAY, 0.003F);
        quadZ(collector, pose, camera, screen, .46F, .82F, .54F, .90F, hovered == WorldUiHit.Kind.NEXT ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
        quadZ(collector, pose, camera, screen, .56F, .82F, .64F, .90F, hovered == WorldUiHit.Kind.QUEUE ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
        quadZ(collector, pose, camera, screen, .66F, .82F, .74F, .90F, hovered == WorldUiHit.Kind.MUTE ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
        quadZ(collector, pose, camera, screen, .76F, .82F, .84F, .90F, hovered == WorldUiHit.Kind.TOGGLE ? COLOR_BUTTON_HOVER : COLOR_BUTTON, 0.003F);
    }

    private static void drawControlLabels(SubmitNodeCollector collector, PoseStack pose, Vec3 camera,
                                          WorldUiScreenRaycast.Screen screen, MediaPlay media, ClientChannelPlaybackSnapshot snapshot, float volume) {
        long durationUs = mediaDurationUs(media, snapshot);
        long positionUs = mediaPositionUs(media, snapshot, durationUs);
        drawLabel(collector, pose, camera, screen, WorldUiPlaybackPresentation.timeLabel(positionUs, durationUs),
                .03F, .94F, .002F, 0xFFD0D0D0, 0xFF141414, 0.006F);
        drawCenteredText(collector, pose, camera, screen, Math.round(snapshot.speed() * 10.0F) / 10.0F + "x", .20F, .86F, .0018F, 0xFFE0E0E0);
        drawIconCentered(collector, pose, camera, screen, icon("skip-previous"), .26F, .82F, .34F, .90F, 0xFFFFFFFF);
        drawIconCentered(collector, pose, camera, screen, icon(snapshot.paused() ? "play" : "pause"), .36F, .82F, .44F, .90F, 0xFF101010);
        drawIconCentered(collector, pose, camera, screen, icon("skip-next"), .46F, .82F, .54F, .90F, 0xFFFFFFFF);
        drawIconCentered(collector, pose, camera, screen, icon("playlist-music"), .56F, .82F, .64F, .90F, 0xFFFFFFFF);
        drawIconCentered(collector, pose, camera, screen, icon(volume <= 0.01F ? "volume-off" : "volume-high"), .66F, .82F, .74F, .90F, 0xFFFFFFFF);
        drawIconCentered(collector, pose, camera, screen, icon("chevron-down"), .76F, .82F, .84F, .90F, 0xFFFFFFFF);
    }

    /** Always-visible media identity: title (or file name) plus the source link. */
    private static void drawMediaHeader(SubmitNodeCollector collector, PoseStack pose, Vec3 camera,
                                        WorldUiScreenRaycast.Screen screen, ClientChannelPlaybackSnapshot snapshot) {
        String mediaUrl = snapshot.mediaUrl();
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return;
        }
        String title = mediaTitle(mediaUrl);
        // Background is tall enough for the URL line below the title (submitText's y is the text top).
        quadZ(collector, pose, camera, screen, .02F, .012F, .66F, .074F, 0xFF141414, 0.003F);
        drawText(collector, pose, camera, screen, title, .03F, .018F, .0022F, 0xFFF0F0F0);
        drawText(collector, pose, camera, screen, truncate(mediaUrl, 52), .03F, .052F, .0014F, 0xFF9A9A9A);
    }

    private static String mediaTitle(String mediaUrl) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        if (metadata != null && metadata.status() == MtvMediaMetadata.Status.RESOLVED && !metadata.title().isBlank()) {
            return truncate(metadata.title(), 42);
        }
        int slash = Math.max(mediaUrl.lastIndexOf('/'), mediaUrl.lastIndexOf('\\'));
        return truncate(slash >= 0 && slash < mediaUrl.length() - 1 ? mediaUrl.substring(slash + 1) : mediaUrl, 42);
    }

    private static Identifier icon(String name) {
        return ICONS.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath("mcedia_mtv", "textures/gui/icons/" + n + ".png"));
    }

    private static String playOrderIcon(String mode) {
        return switch (mode) {
            case "SHUFFLE" -> "shuffle";
            case "LOOP_ALL" -> "repeat";
            case "LOOP_ONE" -> "repeat-once";
            case "CURRENT_ONLY" -> "numeric-1-box";
            default -> "playlist-play";
        };
    }

    private static void drawIconCentered(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         Identifier textureId, float u1, float v1, float u2, float v2, int color) {
        drawIconCentered(collector, pose, camera, screen, textureId, u1, v1, u2, v2, color, 0.005F, 0.6F);
    }

    private static void drawIconCentered(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         Identifier textureId, float u1, float v1, float u2, float v2, int color, float z) {
        drawIconCentered(collector, pose, camera, screen, textureId, u1, v1, u2, v2, color, z, 0.6F);
    }

    private static void drawIconCentered(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         Identifier textureId, float u1, float v1, float u2, float v2, int color, float z, float sizeFactor) {
        // Icons are square textures; a screen's UV space is not square (16:9), so the
        // icon must be sized to the smaller physical dimension to avoid stretching.
        float cu = (u1 + u2) * 0.5F, cv = (v1 + v2) * 0.5F;
        float cellWidth = (u2 - u1) * screen.width();
        float cellHeight = (v2 - v1) * screen.height();
        float size = Math.min(cellWidth, cellHeight) * sizeFactor;
        float du = size / screen.width();
        float dv = size / screen.height();
        texturedQuadColor(collector, pose, camera, screen, textureId, cu - du * 0.5F, cv - dv * 0.5F, cu + du * 0.5F, cv + dv * 0.5F, color, z);
    }

    private static void drawPlaylist(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                     ClientChannelPlaybackSnapshot snapshot, WorldUiHit hit) {
        quad(collector, pose, camera, screen, .68F, .04F, .98F, .64F, 0xFF161616);
        // Header row: play order, page back/forward, add media, clear.
        quadZ(collector, pose, camera, screen, .70F, .05F, .75F, .09F, 0xFF3A3A3A, 0.003F);
        quadZ(collector, pose, camera, screen, .755F, .05F, .805F, .09F, 0xFF2A2A2A, 0.003F);
        quadZ(collector, pose, camera, screen, .81F, .05F, .86F, .09F, 0xFF2A2A2A, 0.003F);
        quadZ(collector, pose, camera, screen, .865F, .05F, .915F, .09F, 0xFF3A3A3A, 0.003F);
        quadZ(collector, pose, camera, screen, .92F, .05F, .97F, .09F, 0xFF3A3A3A, 0.003F);
        WorldUiPlaylistManifest manifest = WorldUiPlaylistCache.getInstance().manifest(snapshot.channelId()).orElse(null);
        drawIconCentered(collector, pose, camera, screen, icon(playOrderIcon(manifest == null ? "SEQUENTIAL" : manifest.playOrderMode())), .70F, .05F, .75F, .09F, 0xFFDDDDDD, 0.005F);
        drawIconCentered(collector, pose, camera, screen, icon("chevron-left"), .755F, .05F, .805F, .09F, 0xFFFFFFFF, 0.005F);
        drawIconCentered(collector, pose, camera, screen, icon("chevron-right"), .81F, .05F, .86F, .09F, 0xFFFFFFFF, 0.005F);
        drawIconCentered(collector, pose, camera, screen, icon("playlist-plus"), .865F, .05F, .915F, .09F, 0xFFFFFFFF, 0.005F);
        drawIconCentered(collector, pose, camera, screen, icon("delete"), .92F, .05F, .97F, .09F, 0xFFDDDDDD, 0.005F);
        if (manifest == null) {
            return;
        }
        int start = PRESENTATION.playlistStart();
        var page = WorldUiPlaylistCache.getInstance().pageAt(snapshot.channelId(), (start / 32) * 32).orElse(null);
        int hoveredRow = hit.kind() == WorldUiHit.Kind.PLAYLIST_ITEM ? hit.index() : -1;
        for (int row = 0; row < 7; row++) {
            float top = .10F + row * .075F, bottom = top + .06F;
            int index = start + row;
            int pageIndex = page == null ? -1 : index - page.offset();
            boolean present = pageIndex >= 0 && pageIndex < page.mediaUrls().size();
            boolean current = present && index == manifest.cursor();
            boolean hovered = index == hoveredRow;
            quadZ(collector, pose, camera, screen, .70F, top, .97F, bottom,
                    current ? (hovered ? 0xFF5A5A5A : 0xFF444444) : (hovered ? 0xFF3D3D3D : 0xFF242424), 0.003F);
            if (present) {
                if (page != null) {
                    String mediaUrl = page.mediaUrls().get(pageIndex);
                    drawPlaylistCover(collector, pose, camera, screen, mediaUrl, .71F, top + .001F, .77F, bottom - .001F);
                    drawMetadataText(collector, pose, camera, screen, mediaUrl, .775F, top + .012F, .0015F, 0xFFE8E8E8, 14);
                }
                // Up/down moves stack vertically in two tight columns; delete is the far-right strip.
                quadZ(collector, pose, camera, screen, .895F, top + .004F, .92F, top + .028F, 0xFF3A3A3A, 0.004F);
                quadZ(collector, pose, camera, screen, .895F, top + .032F, .92F, bottom - .004F, 0xFF3A3A3A, 0.004F);
                quadZ(collector, pose, camera, screen, .92F, top + .004F, .945F, top + .028F, 0xFF3A3A3A, 0.004F);
                quadZ(collector, pose, camera, screen, .92F, top + .032F, .945F, bottom - .004F, 0xFF3A3A3A, 0.004F);
                quadZ(collector, pose, camera, screen, .945F, top + .005F, .97F, bottom - .005F, 0xFF703030, 0.004F);
                drawIconCentered(collector, pose, camera, screen, icon("arrow-up"), .895F, top, .92F, top + .028F, 0xFFCCCCCC, 0.006F, 0.4F);
                drawIconCentered(collector, pose, camera, screen, icon("arrow-down"), .895F, top + .032F, .92F, bottom, 0xFFCCCCCC, 0.006F, 0.4F);
                drawIconCentered(collector, pose, camera, screen, icon("skip-previous"), .92F, top, .945F, top + .028F, 0xFFCCCCCC, 0.006F, 0.4F);
                drawIconCentered(collector, pose, camera, screen, icon("skip-next"), .92F, top + .032F, .945F, bottom, 0xFFCCCCCC, 0.006F, 0.4F);
                drawIconCentered(collector, pose, camera, screen, icon("close"), .945F, top, .97F, bottom, 0xFFFFAAAA, 0.006F, 0.45F);
            }
        }
    }

    private static void drawCover(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                  ClientChannelPlaybackSnapshot snapshot) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(snapshot.mediaUrl());
        // Fixed 16:9 frame (0.224 x 0.126 world units) so common video covers fill it;
        // the image is contain-fitted inside so other ratios never distort.
        quad(collector, pose, camera, screen, .02F, .075F, .16F, .215F, 0xFF333333);
        if (metadata == null) return;
        Identifier textureId = MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) return;
        MtvMediaCover cover = MtvMediaCoverCache.getInstance().cached(metadata.coverUrl());
        if (cover == null || cover.status() != MtvMediaCover.Status.RESOLVED) return;
        // Image sits on a higher z than the frame so its edge never z-fights the backdrop.
        drawCoverContained(collector, pose, camera, screen, textureId, cover, .02F, .075F, .16F, .215F, 0.004F);
    }

    private static void drawPlaylistCover(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                          String mediaUrl, float left, float top, float right, float bottom) {
        MtvMediaMetadata metadata = MtvMediaMetadataCache.getInstance().cached(mediaUrl);
        quadZ(collector, pose, camera, screen, left, top, right, bottom, 0xFF414141, 0.004F);
        if (metadata == null) return;
        Identifier textureId = MtvWorldUiCoverTextures.texture(metadata.coverUrl());
        if (textureId == null) return;
        MtvMediaCover cover = MtvMediaCoverCache.getInstance().cached(metadata.coverUrl());
        if (cover == null || cover.status() != MtvMediaCover.Status.RESOLVED) return;
        // Higher z than the frame keeps the contain-fit image's edge from z-fighting the backdrop.
        drawCoverContained(collector, pose, camera, screen, textureId, cover, left, top, right, bottom, 0.006F);
    }

    /** Draws a cover image as large as possible inside the fixed frame while keeping its aspect ratio. */
    private static void drawCoverContained(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                           Identifier textureId, MtvMediaCover cover, float left, float bottom, float right, float top, float z) {
        float frameWidth = (right - left) * screen.width();
        float frameHeight = (top - bottom) * screen.height();
        float imageAspect = cover.width() <= 0 || cover.height() <= 0
                ? frameWidth / frameHeight : (float) cover.width() / cover.height();
        float imageWidth, imageHeight;
        if (frameWidth / frameHeight > imageAspect) {
            imageHeight = frameHeight;
            imageWidth = frameHeight * imageAspect;
        } else {
            imageWidth = frameWidth;
            imageHeight = frameWidth / imageAspect;
        }
        float du = imageWidth / screen.width();
        float dv = imageHeight / screen.height();
        float cu = (left + right) * 0.5F;
        float cv = (bottom + top) * 0.5F;
        texturedQuadColor(collector, pose, camera, screen, textureId,
                cu - du * 0.5F, cv - dv * 0.5F, cu + du * 0.5F, cv + dv * 0.5F, 0xFFFFFFFF, z);
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
        Vector3f origin = point(screen, u, v, 0.006F);
        pose.pushPose();
        pose.translate(origin.x, origin.y, origin.z);
        pose.mulPose(textRotation(screen));
        float poseScale = textScale(screen, scale);
        pose.scale(poseScale, poseScale, poseScale);
        // order(1) is required for world text: without it the batch is drawn under
        // the level geometry and ends up invisible. Param order: ..., light, color.
        // Depth testing keeps the text occluded by blocks in front of the screen.
        collector.order(1).submitText(pose, 0.0F, 0.0F, Component.literal(value).getVisualOrderText(), false,
                Font.DisplayMode.POLYGON_OFFSET, 15728880, color, 0, 0);
        pose.popPose();
    }

    /** Text scales with the screen height so it stays proportional on larger displays. */
    private static float textScale(WorldUiScreenRaycast.Screen screen, float scale) {
        return scale * screen.height() / TEXT_REFERENCE_HEIGHT;
    }

    /** Draws a value on a dark pill sized to the text, so it stays readable over the video. */
    private static void drawLabel(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                  String value, float u, float v, float scale, int textColor, int pillColor, float z) {
        if (value == null || value.isBlank()) return;
        TextExtent extent = textExtent(Minecraft.getInstance().font, screen, value, scale);
        quadZ(collector, pose, camera, screen, u - 0.004F, v - extent.heightV() * 0.5F, u + extent.widthU() + 0.004F, v + extent.heightV() * 1.5F, pillColor, z);
        drawText(collector, pose, camera, screen, value, u, v, scale, textColor);
    }

    /** Draws a value horizontally and vertically centered at (cu, cv). */
    private static void drawCenteredText(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                         String value, float cu, float cv, float scale, int color) {
        if (value == null || value.isBlank()) return;
        TextExtent extent = textExtent(Minecraft.getInstance().font, screen, value, scale);
        drawText(collector, pose, camera, screen, value, cu - extent.widthU() * 0.5F, cv - extent.heightV() * 0.5F, scale, color);
    }

    /** The drawn width/height of a world text in normalized screen units. */
    private static TextExtent textExtent(Font font, WorldUiScreenRaycast.Screen screen, String value, float scale) {
        float poseScale = textScale(screen, scale);
        return new TextExtent(font.width(value) * poseScale / screen.width(), 8.0F * poseScale / screen.height());
    }

    private record TextExtent(float widthU, float heightV) { }

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

    /** Localized name of the hovered control, or null when no tooltip applies. */
    private static String tooltipKey(WorldUiHit hit, ClientChannelPlaybackSnapshot snapshot) {
        return switch (hit.kind()) {
            case SPEED -> "mcedia_mtv.tooltip.speed";
            case PREVIOUS -> "mcedia_mtv.tooltip.previous";
            case TOGGLE_PAUSE -> snapshot.paused() ? "mcedia_mtv.tooltip.play" : "mcedia_mtv.tooltip.pause";
            case NEXT -> "mcedia_mtv.tooltip.next";
            case QUEUE -> "mcedia_mtv.tooltip.queue";
            case ADD_MEDIA -> "mcedia_mtv.tooltip.add_media";
            case MUTE -> "mcedia_mtv.tooltip.mute";
            case TOGGLE -> "mcedia_mtv.tooltip.collapse";
            case BRIGHTNESS -> "mcedia_mtv.tooltip.brightness";
            case VOLUME -> "mcedia_mtv.tooltip.volume";
            case DANMAKU -> "mcedia_mtv.tooltip.danmaku";
            default -> null;
        };
    }

    /** Centered tooltip anchor for the hovered control; left-panel pills sit right of their bar, transport pills above their button. */
    private static float[] tooltipAnchor(WorldUiHit hit) {
        return switch (hit.kind()) {
            case SPEED -> new float[] { .20F, .785F };
            case PREVIOUS -> new float[] { .30F, .785F };
            case TOGGLE_PAUSE -> new float[] { .40F, .785F };
            case NEXT -> new float[] { .50F, .785F };
            case QUEUE -> new float[] { .60F, .785F };
            case MUTE -> new float[] { .70F, .785F };
            case TOGGLE -> new float[] { .80F, .785F };
            case ADD_MEDIA -> new float[] { .89F, .115F };
            case BRIGHTNESS -> new float[] { .14F, .44F };
            case VOLUME -> new float[] { .19F, .44F };
            case DANMAKU -> new float[] { .19F, .64F };
            default -> null;
        };
    }

    /** A small opaque pill with the control's localized name, shown while hovering a control. */
    private static void drawTooltip(SubmitNodeCollector collector, PoseStack pose, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                    WorldUiHit hit, ClientChannelPlaybackSnapshot snapshot) {
        String key = tooltipKey(hit, snapshot);
        if (key == null) return;
        float[] anchor = tooltipAnchor(hit);
        if (anchor == null) return;
        String value = Component.translatable(key).getString();
        if (value == null || value.isBlank()) return;
        TextExtent extent = textExtent(Minecraft.getInstance().font, screen, value, TOOLTIP_SCALE);
        float cu = anchor[0], cv = anchor[1];
        float halfW = extent.widthU() * 0.5F;
        float halfH = extent.heightV() * 0.5F;
        quadZ(collector, pose, camera, screen, cu - halfW - 0.006F, cv - halfH - 0.004F, cu + halfW + 0.006F, cv + halfH + 0.004F, 0xFF141414, 0.006F);
        // submitText's y is the text top, so top-align it to the pill's top edge.
        drawText(collector, pose, camera, screen, value, cu - halfW, cv - halfH, TOOLTIP_SCALE, 0xFFD0D0D0);
    }

    /** The brightness slider drives the screen light level directly, so the screen
     *  visibly brightens or dims regardless of the surrounding world light. */
    private static int applyMinimumBrightness(int minBrightness) {
        int brightness = Math.max(0, Math.min(15, minBrightness));
        return (brightness << 20) | (brightness << 4);
    }

    /** Localized playback status overlay, replacing core's built-in Chinese text. */
    private static final java.util.EnumMap<PlaybackState, String> STATUS_TEXT = new java.util.EnumMap<>(PlaybackState.class);

    private static String localizedStatus(PlaybackState state) {
        if (state == null) {
            return null;
        }
        String cached = STATUS_TEXT.get(state);
        if (cached != null) {
            return cached;
        }
        String resolved = switch (state) {
            case LOADING -> Component.translatable("mcedia_mtv.status.loading").getString();
            case PAUSED -> Component.translatable("mcedia_mtv.status.paused").getString();
            case ENDED -> Component.translatable("mcedia_mtv.status.ended").getString();
            case ERROR -> Component.translatable("mcedia_mtv.status.error").getString();
            default -> null;
        };
        if (resolved != null) {
            STATUS_TEXT.put(state, resolved);
        }
        return resolved;
    }

    private static float progress(MediaPlay media) {
        if (media == null) return 0F;
        long durationUs = media.getDuration();
        if (durationUs <= 0L) return 0F;
        return Math.max(0F, Math.min(1F, (float) media.getEstimatedTime() / durationUs));
    }

    /** UI playback position: the local ffmpeg clock when media is loaded, else the server snapshot estimate. */
    private static long mediaPositionUs(MediaPlay media, ClientChannelPlaybackSnapshot snapshot, long durationUs) {
        if (media != null && media.getEstimatedTime() >= 0L) {
            long positionUs = media.getEstimatedTime();
            if (durationUs > 0L) positionUs = Math.min(positionUs, durationUs);
            return Math.max(0L, positionUs);
        }
        long snapshotPositionUs = snapshot.anchorMediaTimeUs();
        if (!snapshot.paused()) snapshotPositionUs += Math.max(0L, snapshot.elapsedTimeMs()) * 1000L;
        if (snapshot.resolvedDurationUs() > 0L) snapshotPositionUs = Math.min(snapshotPositionUs, snapshot.resolvedDurationUs());
        return Math.max(0L, snapshotPositionUs);
    }

    /** UI media duration: the local ffmpeg duration when loaded, else the server snapshot estimate. */
    private static long mediaDurationUs(MediaPlay media, ClientChannelPlaybackSnapshot snapshot) {
        if (media != null && media.getDuration() > 0L) {
            return media.getDuration();
        }
        return snapshot.resolvedDurationUs();
    }

    private static void quad(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, WorldUiScreenRaycast.Screen screen, float left, float bottom, float right, float top, int color) {
        texturedQuadColor(collector, poseStack, camera, screen, WHITE_TEXTURE, left, bottom, right, top, color, 0.002F);
    }

    private static void texturedQuadColor(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                                          Identifier textureId, float left, float bottom, float right, float top, int color, float z) {
        // point()'s v grows downward on screen, so the "bottom" edge (a, b) is visually
        // the upper edge. Map the texture's top row (v=0) there, otherwise every texture
        // (icons, covers) renders vertically flipped.
        Vector3f a = point(screen, left, bottom, z), b = point(screen, right, bottom, z), c = point(screen, right, top, z), d = point(screen, left, top, z);
        // Submitted at order(1) so the controls paint after the order(0) video layer
        // regardless of distance sorting. Vertices are local to the plane centre, which
        // drawControls already translated into camera space, so no camera subtraction here.
        collector.order(1).submitCustomGeometry(poseStack, McediaRenderTypes.entityTranslucentUnlit(textureId), (pose, vertex) -> {
            uiVertex(vertex, pose, a.x, a.y, a.z, color, 0F, 0F);
            uiVertex(vertex, pose, b.x, b.y, b.z, color, 1F, 0F);
            uiVertex(vertex, pose, c.x, c.y, c.z, color, 1F, 1F);
            uiVertex(vertex, pose, d.x, d.y, d.z, color, 0F, 1F);
        });
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v) {
        return point(screen, u, v, 0.002F);
    }

    private static Vector3f point(WorldUiScreenRaycast.Screen screen, float u, float v, float z) {
        // Local coordinate relative to the plane centre; drawControls has already translated
        // the pose there. Offset toward the viewer (right × up is the video front face) so
        // controls sort in front of the translucent video quad. Distinct z layers keep
        // overlapping UI elements from z-fighting.
        var normal = new Vector3f(screen.right()).cross(screen.up()).normalize().mul(z);
        return new Vector3f().fma((u - .5F) * screen.width(), screen.right()).fma((.5F - v) * screen.height(), screen.up()).add(normal);
    }

    /** Like {@link #quad} but on a dedicated z layer to avoid z-fighting with lower UI. */
    private static void quadZ(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, WorldUiScreenRaycast.Screen screen,
                              float left, float bottom, float right, float top, int color, float z) {
        texturedQuadColor(collector, poseStack, camera, screen, WHITE_TEXTURE, left, bottom, right, top, color, z);
    }

    private static void uiVertex(VertexConsumer vertex, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v) {
        vertex.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
