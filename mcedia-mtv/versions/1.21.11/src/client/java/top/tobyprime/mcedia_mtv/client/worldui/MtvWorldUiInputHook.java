package top.tobyprime.mcedia_mtv.client.worldui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiCapabilityState;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerHandle;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

/** 1.21.11 mapping adapter for local ray input; only finalized controls leave the client. */
public final class MtvWorldUiInputHook {
    private static final WorldUiInteractionState INTERACTION = new WorldUiInteractionState(WorldUiControlSender.getInstance());
    private static boolean initialized;
    private static boolean primaryDown;
    private static boolean consumesAttack;
    private MtvWorldUiInputHook() { }
    public static void initialize() { if (!initialized) { initialized = true; ClientTickEvents.START_CLIENT_TICK.register(MtvWorldUiInputHook::tick); } }
    public static boolean consumesAttack() { return consumesAttack; }
    private static void tick(Minecraft client) {
        if (!WorldUiCapabilityState.getInstance().supported() || client.screen != null || client.player == null) { reset(); return; }
        var selection = select(client);
        if (selection == null) MtvWorldUiRenderer.presentation().clearHover();
        else { MtvWorldUiRenderer.presentation().update(selection.target(), selection.u(), selection.v()); INTERACTION.refreshTarget(selection.target()); }
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
                boolean wasExpanded = presentation.isExpanded(selection.target());
                INTERACTION.onPrimaryPress(selection.target(), hit, selection.u(), selection.v(), selection.durationUs());
                if (hit.isToggle()) { if (wasExpanded) presentation.collapse(); else presentation.expand(selection.target()); }
            }
        }
        if (down && consumesAttack && selection != null) INTERACTION.onPointerMove(selection.u(), selection.v());
        if (!down && primaryDown) { if (consumesAttack && selection != null) INTERACTION.onPrimaryRelease(selection.u(), selection.v(), selection.durationUs()); consumesAttack = false; }
        primaryDown = down;
    }
    private static Selection select(Minecraft client) {
        var camera = client.gameRenderer.getMainCamera();
        if (camera == null) return null;
        var origin = camera.position();
        var screens = EntityPlayerManager.getInstance().worldUiScreens();
        var hit = WorldUiScreenRaycast.select(new Vector3f((float) origin.x, (float) origin.y, (float) origin.z), new Vector3f(camera.forwardVector()), screens.stream().map(EntityPlayerHandle.WorldUiScreen::plane).toList()).orElse(null);
        if (hit == null || blockedByBlock(client, origin, hit.distance())) return null;
        for (var screen : screens) if (screen.plane() == hit.screen()) {
            var snapshot = ClientChannelPlaybackManager.getInstance().snapshot(screen.channelId());
            return new Selection(new WorldUiInteractionState.Target(screen.mtvUuid(), screen.screenId(), screen.channelId(), snapshot.revision()), hit.u(), hit.v(), snapshot.resolvedDurationUs());
        }
        return null;
    }
    private static boolean blockedByBlock(Minecraft client, net.minecraft.world.phys.Vec3 origin, float screenDistance) { return client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK && client.hitResult.getLocation().distanceToSqr(origin) + 1.0E-4D < screenDistance * screenDistance; }
    private static void reset() { MtvWorldUiRenderer.presentation().clearHover(); MtvWorldUiRenderer.presentation().collapse(); if (primaryDown && consumesAttack) INTERACTION.collapse(); primaryDown = false; consumesAttack = false; }
    private record Selection(WorldUiInteractionState.Target target, float u, float v, long durationUs) { }
}
