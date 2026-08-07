package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import top.tobyprime.mcedia_mtv.client.HudChannelPlayer;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiCapabilitiesPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiCapabilityState;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistManifestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistPagePayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiPlaylistPageRequestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiControlRequestPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiControlResultPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlSender;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiWatchPayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiUnwatchPayload;
import top.tobyprime.mcedia_mtv.client.worldui.MtvWorldUiRenderResources;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistCache;
import top.tobyprime.mcedia_mtv.client.channel.worldui.MtvWorldUiControlStatePayload;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlStateCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MtvClientChannelPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientChannelPayloads.class);

    private MtvClientChannelPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(MtvHudBindingPayload.TYPE, MtvHudBindingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvChannelClientSnapshotPayload.TYPE, MtvChannelClientSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvChannelClientSyncPayload.TYPE, MtvChannelClientSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvChannelRemovePayload.TYPE, MtvChannelRemovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvWorldUiCapabilitiesPayload.TYPE, MtvWorldUiCapabilitiesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvWorldUiPlaylistManifestPayload.TYPE, MtvWorldUiPlaylistManifestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvWorldUiPlaylistPagePayload.TYPE, MtvWorldUiPlaylistPagePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvWorldUiControlResultPayload.TYPE, MtvWorldUiControlResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvWorldUiControlStatePayload.TYPE, MtvWorldUiControlStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientSubscribePayload.TYPE, MtvChannelClientSubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientUnsubscribePayload.TYPE, MtvChannelClientUnsubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientHeartbeatPayload.TYPE, MtvChannelClientHeartbeatPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvWorldUiPlaylistPageRequestPayload.TYPE, MtvWorldUiPlaylistPageRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvWorldUiControlRequestPayload.TYPE, MtvWorldUiControlRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvWorldUiWatchPayload.TYPE, MtvWorldUiWatchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvWorldUiUnwatchPayload.TYPE, MtvWorldUiUnwatchPayload.CODEC);
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
                safeHandle("world_ui_capabilities", "", null, () ->
                        WorldUiCapabilityState.getInstance().onCapabilities(payload.capabilities())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiPlaylistManifestPayload.TYPE, (payload, context) ->
                safeHandle("playlist_manifest", payload.manifest().channelId(), payload.manifest().revision(), () ->
                        WorldUiPlaylistCache.getInstance().applyManifest(payload.manifest())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiPlaylistPagePayload.TYPE, (payload, context) ->
                safeHandle("playlist_page", payload.page().channelId(), payload.page().revision(), () ->
                        WorldUiPlaylistCache.getInstance().applyPage(payload.page())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiControlResultPayload.TYPE, (payload, context) ->
                safeHandle("world_ui_control_result", "", payload.result().revision(), () ->
                        WorldUiControlSender.getInstance().onResult(payload.result())));
        ClientPlayNetworking.registerGlobalReceiver(MtvWorldUiControlStatePayload.TYPE, (payload, context) ->
                safeHandle("world_ui_control_state", payload.state().channelId(), payload.state().channelRevision(), () ->
                        WorldUiControlStateCache.getInstance().apply(payload.state())));
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
        MtvWorldUiRenderResources.getInstance().clear();
        WorldUiControlStateCache.getInstance().clear();
        LOGGER.info("Open MTV client channel state: server={}, existing sessions retained for join payloads",
                client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
    }

    private static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        var lifecycle = new MtvClientConnectionLifecycle(
                HudChannelPlayer.getInstance()::cleanup,
                ClientChannelPlaybackManager.getInstance()::clear
        );
        LOGGER.info("Close MTV client channel state: server={}",
                client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
        lifecycle.onDisconnect();
        WorldUiCapabilityState.getInstance().clear();
        WorldUiPlaylistCache.getInstance().clear();
        MtvWorldUiRenderResources.getInstance().clear();
        WorldUiControlStateCache.getInstance().clear();
    }
}
