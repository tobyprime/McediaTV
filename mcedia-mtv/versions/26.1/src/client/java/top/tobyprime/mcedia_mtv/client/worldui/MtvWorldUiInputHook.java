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
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** Mapping adapter for ray selection and left-click dispatch; no pointer movement is networked. */
public final class MtvWorldUiInputHook {
    private static final WorldUiInteractionState INTERACTION = new WorldUiInteractionState(WorldUiControlSender.getInstance());
    private static boolean initialized;
    private static boolean primaryDown;
    private static boolean consumesAttack;

    private MtvWorldUiInputHook() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.START_CLIENT_TICK.register(MtvWorldUiInputHook::tick);
    }

    public static boolean consumesAttack() {
        return consumesAttack;
    }

    private static void tick(Minecraft client) {
        if (!WorldUiCapabilityState.getInstance().supported() || client.screen != null || client.player == null) {
            reset();
            return;
        }

        var selection = select(client);
        if (selection == null) {
            MtvWorldUiRenderer.presentation().clearHover();
        } else {
            MtvWorldUiRenderer.presentation().update(selection.target(), selection.u(), selection.v());
            INTERACTION.refreshTarget(selection.target());
            WorldUiPlaylistCache.getInstance().manifest(selection.target().channelId()).ifPresent(m -> INTERACTION.setPlayOrderMode(m.playOrderMode()));
        }

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
                if (isPlaylistOperation(hit.kind()) && !hasCachedItem(selection.target().channelId(), hit.index())) {
                    requestVisiblePage(selection.target());
                    primaryDown = down;
                    return;
                }
                boolean wasExpanded = presentation.isExpanded(selection.target());
                INTERACTION.onPrimaryPress(selection.target(), hit, selection.u(), selection.v(), selection.durationUs());
                if (hit.isToggle()) {
                    if (wasExpanded) presentation.collapse();
                    else presentation.expand(selection.target());
                }
            }
        }
        if (down && consumesAttack && selection != null) {
            INTERACTION.onPointerMove(selection.u(), selection.v());
        }
        if (!down && primaryDown) {
            if (consumesAttack && selection != null) {
                INTERACTION.onPrimaryRelease(selection.u(), selection.v(), selection.durationUs());
            }
            consumesAttack = false;
        }
        primaryDown = down;
    }

    private static Selection select(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera();
        if (camera == null) return null;
        var origin = camera.position();
        var screens = EntityPlayerManager.getInstance().worldUiScreens();
        var hit = WorldUiScreenRaycast.select(new Vector3f((float) origin.x, (float) origin.y, (float) origin.z),
                new Vector3f(camera.forwardVector()), screens.stream().map(EntityPlayerHandle.WorldUiScreen::plane).toList()).orElse(null);
        if (hit == null || blockedByBlock(client, origin, hit.distance())) return null;
        for (var screen : screens) {
            if (screen.plane() != hit.screen()) continue;
            var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
            return new Selection(new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision()),
                    hit.u(), hit.v(), snapshot.resolvedDurationUs());
        }
        return null;
    }

    private static boolean blockedByBlock(Minecraft client, net.minecraft.world.phys.Vec3 origin, float screenDistance) {
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) return false;
        return client.hitResult.getLocation().distanceToSqr(origin) + 1.0E-4D < screenDistance * screenDistance;
    }

    private static void reset() {
        MtvWorldUiRenderer.presentation().clearHover();
        MtvWorldUiRenderer.presentation().collapse();
        if (primaryDown && consumesAttack) INTERACTION.collapse();
        primaryDown = false;
        consumesAttack = false;
    }

    private static void requestVisiblePage(WorldUiInteractionState.Target target) {
        WorldUiPlaylistCache.getInstance().manifest(target.channelId()).ifPresent(manifest -> {
            int end = Math.min(7, Math.max(0, manifest.itemCount() - 1));
            for (int offset : WorldUiPlaylistCache.getInstance().missingOffsetsForVisibleRange(target.channelId(), 0, end)) {
                WorldUiPlaylistPageSender.request(target.channelId(), manifest.revision(), offset);
            }
        });
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
                || kind == WorldUiHit.Kind.MOVE_FRONT || kind == WorldUiHit.Kind.MOVE_BACK;
    }

    private record Selection(WorldUiInteractionState.Target target, float u, float v, long durationUs) { }
}
