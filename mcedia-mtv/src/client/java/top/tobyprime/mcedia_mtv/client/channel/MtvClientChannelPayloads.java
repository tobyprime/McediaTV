package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class MtvClientChannelPayloads {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvClientChannelPayloads.class);

    private MtvClientChannelPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(MtvChannelClientSnapshotPayload.TYPE, MtvChannelClientSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MtvChannelRemovePayload.TYPE, MtvChannelRemovePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientHandshakePayload.TYPE, MtvChannelClientHandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientSubscribePayload.TYPE, MtvChannelClientSubscribePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientHeartbeatPayload.TYPE, MtvChannelClientHeartbeatPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MtvChannelClientMediaInfoPayload.TYPE, MtvChannelClientMediaInfoPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelClientSnapshotPayload.TYPE, (payload, context) -> MtvClientNetworkInitializer.onChannelSnapshot(payload.snapshot()));
        ClientPlayNetworking.registerGlobalReceiver(MtvChannelRemovePayload.TYPE, (payload, context) -> MtvClientNetworkInitializer.onChannelRemoved(payload.channelId()));
        ClientPlayConnectionEvents.JOIN.register(MtvClientChannelPayloads::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register(MtvClientChannelPayloads::onDisconnect);
        LOGGER.debug("Registered MTV client channel payloads and connection listeners");
    }

    private static void onJoin(ClientPacketListener handler, PacketSender sender, Minecraft client) {
        MtvClientNetworkInitializer.clearAll();
        var sessionId = UUID.randomUUID();
        MtvClientNetworkInitializer.beginSession(sessionId);
        boolean canSendHandshake = ClientPlayNetworking.canSend(MtvChannelClientHandshakePayload.TYPE);
        LOGGER.info("Open MTV client channel session: session={}, canSendHandshake={}", sessionId, canSendHandshake);
        ClientPlayNetworking.send(new MtvChannelClientHandshakePayload(sessionId));
        LOGGER.info("Sent MTV client channel handshake: session={}, canSendHandshake={}, server={}",
                sessionId, canSendHandshake, client.getCurrentServer() == null ? "singleplayer" : client.getCurrentServer().ip);
    }

    private static void onDisconnect(ClientPacketListener handler, Minecraft client) {
        LOGGER.info("Close MTV client channel session");
        MtvClientNetworkInitializer.clearAll();
    }
}
