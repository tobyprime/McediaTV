package top.tobyprime.mcedia_mtv.client.channel;

public final class MtvChannelHeartbeatSender {
    private MtvChannelHeartbeatSender() {
    }

    public static void send(MtvAudienceHeartbeat heartbeat) {
        MtvChannelClientPacketSender.send(new MtvChannelClientHeartbeatPayload(heartbeat));
    }
}
