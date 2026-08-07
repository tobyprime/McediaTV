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
        PayloadTypeRegistry.playC2S().register(MtvChannelClientSubscribePayload.TYPE, MtvChannelClientSubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientUnsubscribePayload.TYPE, MtvChannelClientUnsubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientHeartbeatPayload.TYPE, MtvChannelClientHeartbeatPayload.CODEC);
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
    }
}
