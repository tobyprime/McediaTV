package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import top.tobyprime.mcedia_mtv.client.HudChannelPlayer;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiCapabilitiesPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiControlRequestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiControlResultPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistManifestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistPagePayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistPageRequestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiCapabilityState;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiWatchPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiUnwatchPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MtvClientChannelPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientChannelPayloads.class);

    private MtvClientChannelPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(MtvHudBindingPayload.TYPE, MtvHudBindingPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvChannelClientSnapshotPayload.TYPE, MtvChannelClientSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvChannelClientSyncPayload.TYPE, MtvChannelClientSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvChannelRemovePayload.TYPE, MtvChannelRemovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvWorldUiCapabilitiesPayload.TYPE, MtvWorldUiCapabilitiesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvWorldUiPlaylistManifestPayload.TYPE, MtvWorldUiPlaylistManifestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvWorldUiPlaylistPagePayload.TYPE, MtvWorldUiPlaylistPagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MtvWorldUiControlResultPayload.TYPE, MtvWorldUiControlResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvChannelClientSubscribePayload.TYPE, MtvChannelClientSubscribePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvChannelClientUnsubscribePayload.TYPE, MtvChannelClientUnsubscribePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvChannelClientHeartbeatPayload.TYPE, MtvChannelClientHeartbeatPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvWorldUiPlaylistPageRequestPayload.TYPE, MtvWorldUiPlaylistPageRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvWorldUiControlRequestPayload.TYPE, MtvWorldUiControlRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvWorldUiWatchPayload.TYPE, MtvWorldUiWatchPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MtvWorldUiUnwatchPayload.TYPE, MtvWorldUiUnwatchPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelClientSnapshotPayload.TYPE, (payload, context) ->
                safeHandle("snapshot", payload.snapshot().channelId(), payload.snapshot().revision(), () ->
                        ClientChannelPlaybackManager.getInstance().onSnapshot(payload.snapshot())));
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelClientSyncPayload.TYPE, (payload, context) ->
                safeHandle("sync", payload.snapshot().channelId(), payload.snapshot().revision(), () ->
                        ClientChannelPlaybackManager.getInstance().onSync(payload.snapshot())));
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelRemovePayload.TYPE, (payload, context) ->
                safeHandle("remove", payload.channelId(), null, () ->
                        ClientChannelPlaybackManager.getInstance().onRemove(payload.channelId())));
        ClientPlayNetworking.registerGlobalReceiver(MtvHudBindingPayload.TYPE, (payload, context) ->
                safeHandle("hud_binding", payload.channelId(), null, () ->
                        HudChannelPlayer.getInstance().onBinding(payload.channelId())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiCapabilitiesPayload.TYPE, (payload, context) ->
                safeHandle("world_ui_capabilities", "", null, () -> WorldUiCapabilityState.getInstance().onCapabilities(payload.capabilities())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiPlaylistManifestPayload.TYPE, (payload, context) ->
                safeHandle("playlist_manifest", payload.manifest().channelId(), payload.manifest().revision(), () -> WorldUiPlaylistCache.getInstance().applyManifest(payload.manifest())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiPlaylistPagePayload.TYPE, (payload, context) ->
                safeHandle("playlist_page", payload.page().channelId(), payload.page().revision(), () -> WorldUiPlaylistCache.getInstance().applyPage(payload.page())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiControlResultPayload.TYPE, (payload, context) ->
                safeHandle("world_ui_control_result", "", payload.result().revision(), () -> WorldUiControlSender.getInstance().onResult(payload.result())));
        ClientPlayConnectionEvents.JOIN.register(MtvClientChannelPayloads::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(MtvClientChannelPayloads::onDisconnect);
        LOGGER.debug("Registered MTV client channel payloads and connection listeners");
    }

    private static void safeHandle(String packetType, String channelId, Long revision, Runnable action) {
        try {
            LOGGER.debug("Handling MTV {} payload: channel={}, revision={}", packetType, channelId, revision);
            action.run();
        } catch (Exception e) {
            LOGGER.warn("Failed to handle MTV {} packet: channel={}, revision={}", packetType, channelId, revision, e);
        }
    }

    private static void onJoin(ClientPacketListener handler, PacketSender sender, Minecraft client) {
        var lifecycle = new MtvClientConnectionLifecycle(
                HudChannelPlayer.getInstance()::cleanup,
                ClientChannelPlaybackManager.getInstance()::clear
        );
        lifecycle.onJoin();
        WorldUiCapabilityState.getInstance().clear();
        WorldUiPlaylistCache.getInstance().clear();
        LOGGER.info("Open MTV client channel state: server={}, existing sessions retained for join payloads",
                client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
    }

    private static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        var lifecycle = new MtvClientConnectionLifecycle(
                HudChannelPlayer.getInstance()::cleanup,
                ClientChannelPlaybackManager.getInstance()::clear
        );
        LOGGER.info("Close MTV client channel state: server={}, cleanup begins",
                client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
        lifecycle.onDisconnect();
        WorldUiCapabilityState.getInstance().clear();
        WorldUiPlaylistCache.getInstance().clear();
    }
}
