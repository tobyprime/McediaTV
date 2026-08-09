package top.tobyprime.mcedia_mtv.client.worldui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiCapabilityState;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistPageSender;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlStateCache;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** 26.2 mapping adapter for local ray input; only finalized controls leave the client. */
public final class MtvWorldUiInputHook {
    private static final WorldUiInteractionState INTERACTION = new WorldUiInteractionState(WorldUiControlSender.getInstance());
    private static boolean initialized;
    private static boolean primaryDown;
    private static boolean consumesAttack;
    private MtvWorldUiInputHook() { }
    public static void initialize() { if (!initialized) { initialized = true; ClientTickEvents.START_CLIENT_TICK.register(MtvWorldUiInputHook::tick); } }
    public static boolean consumesAttack() { return consumesAttack; }
    private static void tick(Minecraft client) {
        if (!WorldUiCapabilityState.getInstance().supported() || client.player == null) { reset(); return; }
        if (client.gui.screen() != null) { resetInput(); return; }
        var selection = select(client);
        if (selection == null) MtvWorldUiRenderer.presentation().clearHover();
        else {
            MtvWorldUiRenderer.presentation().update(selection.target(), selection.u(), selection.v());
            INTERACTION.refreshTarget(selection.target());
            var controlState = WorldUiControlStateCache.getInstance().state(selection.target().mtvUuid(), selection.target().screenId());
            if (controlState != null) {
                INTERACTION.setMasterVolume(controlState.masterVolume());
                INTERACTION.setDanmakuVisible(controlState.danmakuVisible());
            }
            WorldUiPlaylistCache.getInstance().manifest(selection.target().channelId()).ifPresent(m -> INTERACTION.setPlayOrderMode(m.playOrderMode()));
        }
                MtvWorldUiRenderer.presentation().tickHoverFade(selection != null && MtvWorldUiRenderer.presentation().hoveringToggleTrigger());
if (selection != null && MtvWorldUiRenderer.presentation().isPlaylistExpanded()) requestVisiblePage(selection.target());
        boolean down = client.options.keyAttack.isDown();
        if (down && !primaryDown && selection != null) {
            var presentation = MtvWorldUiRenderer.presentation();
            var hit = presentation.hit();
            consumesAttack = hit.kind() != WorldUiHit.Kind.NONE && (hit.isToggle() || presentation.isExpanded(selection.target()));
            if (consumesAttack) {
                if (hit.kind() == WorldUiHit.Kind.ADD_MEDIA) {
                    MtvAddMediaScreen.open(client, selection.target());
                    primaryDown = down;
                    return;
                }
                if (hit.kind() == WorldUiHit.Kind.QUEUE) {
                    presentation.togglePlaylist();
                    if (presentation.isPlaylistExpanded()) requestVisiblePage(selection.target());
                    primaryDown = down;
                    return;
                }
                if (hit.kind() == WorldUiHit.Kind.PLAYLIST_PREVIOUS_PAGE || hit.kind() == WorldUiHit.Kind.PLAYLIST_NEXT_PAGE) {
                    WorldUiPlaylistCache.getInstance().manifest(selection.target().channelId()).ifPresent(manifest -> {
                        if (hit.kind() == WorldUiHit.Kind.PLAYLIST_PREVIOUS_PAGE) presentation.previousPlaylistPage();
                        else presentation.nextPlaylistPage(manifest.itemCount());
                        requestVisiblePage(selection.target());
                    });
                    primaryDown = down;
                    return;
                }
                if (isPlaylistOperation(hit.kind()) && !hasCachedItem(selection.target().channelId(), hit.index())) {
                    requestVisiblePage(selection.target());
                    primaryDown = down;
                    return;
                }
                boolean wasExpanded = presentation.isExpanded(selection.target());
                INTERACTION.onPrimaryPress(selection.target(), hit, selection.u(), selection.v(), selection.durationUs());
                if (hit.isToggle()) { if (wasExpanded) presentation.collapse(); else presentation.expand(selection.target()); }
            }
        }
        if (down && consumesAttack && selection != null) INTERACTION.onPointerMove(selection.u(), selection.v());
        if (!down && primaryDown) { if (consumesAttack) INTERACTION.onPrimaryRelease(); consumesAttack = false; }
        primaryDown = down;
    }
    private static Selection select(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        if (camera == null) return null;
        var origin = camera.position();
        var screens = EntityPlayerManager.getInstance().worldUiScreens().stream()
                .filter(EntityPlayerHandle.WorldUiScreen::powered)
                .filter(s -> s.channelId() != null && !s.channelId().isBlank())
                .toList();
        var hit = WorldUiScreenRaycast.select(new Vector3f((float) origin.x, (float) origin.y, (float) origin.z), new Vector3f(camera.forwardVector()), screens.stream().map(EntityPlayerHandle.WorldUiScreen::plane).toList()).orElse(null);
        if (hit == null || blockedByBlock(client, origin, hit.distance())) return null;
        for (var screen : screens) if (screen.plane() == hit.screen()) {
            var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
            return new Selection(new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision()), hit.u(), hit.v(), snapshot.resolvedDurationUs());
        }
        return null;
    }
    private static boolean blockedByBlock(Minecraft client, net.minecraft.world.phys.Vec3 origin, float screenDistance) { return client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK && client.hitResult.getLocation().distanceToSqr(origin) + 1.0E-4D < screenDistance * screenDistance; }
    private static void resetInput() { MtvWorldUiRenderer.presentation().clearHover(); primaryDown = false; consumesAttack = false; }
    private static void reset() { resetInput(); MtvWorldUiRenderer.presentation().collapse(); INTERACTION.collapse(); }
    private static void requestVisiblePage(WorldUiInteractionState.Target target) {
        WorldUiPlaylistCache.getInstance().manifest(target.channelId()).ifPresent(manifest -> {
            int start = MtvWorldUiRenderer.presentation().playlistStart();
            int last = Math.max(0, manifest.itemCount() - 1);
            WorldUiPlaylistCache.getInstance().prefetchRange(target.channelId(), start, Math.min(last, start + 6));
            WorldUiPlaylistCache.getInstance().prefetchRange(target.channelId(), Math.max(0, manifest.cursor() - 1), Math.min(last, manifest.cursor() + 1));
            requestRange(target.channelId(), manifest.revision(), start, Math.min(last, start + 6));
            requestRange(target.channelId(), manifest.revision(), Math.max(0, manifest.cursor() - 1), Math.min(last, manifest.cursor() + 1));
            requestRange(target.channelId(), manifest.revision(), Math.max(0, start - 32), Math.min(last, start + 31));
            requestRange(target.channelId(), manifest.revision(), start + 32, Math.min(last, start + 63));
        });
    }
    private static void requestRange(String channelId, long revision, int start, int end) {
        if (end < start) return;
        for (int offset : WorldUiPlaylistCache.getInstance().missingOffsetsForVisibleRange(channelId, start, end)) {
            WorldUiPlaylistPageSender.request(channelId, revision, offset);
        }
    }
    private static boolean hasCachedItem(String channelId, int index) {
        if (index < 0) return false;
        int offset = (index / 32) * 32;
        return WorldUiPlaylistCache.getInstance().pageAt(channelId, offset)
                .map(page -> index - page.offset() >= 0 && index - page.offset() < page.mediaUrls().size())
                .orElse(false);
    }
    private static boolean isPlaylistOperation(WorldUiHit.Kind kind) {
        return kind == WorldUiHit.Kind.PLAYLIST_ITEM || kind == WorldUiHit.Kind.REMOVE_ITEM
                || kind == WorldUiHit.Kind.MOVE_FRONT || kind == WorldUiHit.Kind.MOVE_BACK
                || kind == WorldUiHit.Kind.MOVE_UP || kind == WorldUiHit.Kind.MOVE_DOWN;
    }
    private record Selection(WorldUiInteractionState.Target target, float u, float v, long durationUs) { }
}
