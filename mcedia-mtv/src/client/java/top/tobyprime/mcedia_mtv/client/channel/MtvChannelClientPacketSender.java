package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MtvChannelClientPacketSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelClientPacketSender.class);

    private MtvChannelClientPacketSender() {
    }

    static void send(CustomPacketPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("Rejected MTV client packet: type={}, reason=no-client-player", payload.getClass().getSimpleName());
            return;
        }
        if (mc.level == null) {
            LOGGER.warn("Rejected MTV client packet: type={}, reason=no-client-level", payload.getClass().getSimpleName());
            return;
        }
        if (mc.getConnection() == null) {
            LOGGER.warn("Rejected MTV client packet: type={}, reason=no-connection", payload.getClass().getSimpleName());
            return;
        }
        if (!ClientPlayNetworking.canSend(payload.type())) {
            LOGGER.warn("Rejected MTV client packet: type={}, reason=server-does-not-support-payload", payload.getClass().getSimpleName());
            return;
        }
        ClientPlayNetworking.send(payload);
        LOGGER.debug("Sent MTV client packet: type={}", payload.getClass().getSimpleName());
    }
}
