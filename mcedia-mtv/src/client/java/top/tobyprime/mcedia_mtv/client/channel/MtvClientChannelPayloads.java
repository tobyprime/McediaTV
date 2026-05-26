package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MtvClientChannelPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientChannelPayloads.class);

    private MtvClientChannelPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(MtvChannelClientSnapshotPayload.TYPE, MtvChannelClientSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvChannelRemovePayload.TYPE, MtvChannelRemovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientSubscribePayload.TYPE, MtvChannelClientSubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientUnsubscribePayload.TYPE, MtvChannelClientUnsubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientHeartbeatPayload.TYPE, MtvChannelClientHeartbeatPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelClientSnapshotPayload.TYPE, (payload, context) -> ClientChannelPlaybackManager.getInstance().onSnapshot(payload.snapshot()));
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelRemovePayload.TYPE, (payload, context) -> ClientChannelPlaybackManager.getInstance().onRemove(payload.channelId()));
        ClientPlayConnectionEvents.JOIN.register(MtvClientChannelPayloads::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(MtvClientChannelPayloads::onDisconnect);
        LOGGER.debug("Registered MTV client channel payloads and connection listeners");
    }

    private static void onJoin(ClientPacketListener handler, PacketSender sender, Minecraft client) {
        ClientChannelPlaybackManager.getInstance().clear();
        LOGGER.debug("Open MTV client channel state: server={}", client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
    }

    private static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        LOGGER.debug("Close MTV client channel state");
        ClientChannelPlaybackManager.getInstance().clear();
    }
}
