package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MtvChannelHeartbeatSender {
    private MtvChannelHeartbeatSender() {
    }

    public static void send(MtvAudienceHeartbeat heartbeat) {
        ClientPlayNetworking.send(new MtvChannelClientHeartbeatPayload(heartbeat));
    }
}
