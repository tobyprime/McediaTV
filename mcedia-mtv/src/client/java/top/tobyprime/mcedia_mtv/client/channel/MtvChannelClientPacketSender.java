package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

final class MtvChannelClientPacketSender {
    private MtvChannelClientPacketSender() {
    }

    static void send(CustomPacketPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !ClientPlayNetworking.canSend(payload.type())) {
            return;
        }
        ClientPlayNetworking.send(payload);
    }
}
