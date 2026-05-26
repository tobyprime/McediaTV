package top.tobyprime.mcedia_mtv.client.channel;

public final class MtvChannelSubscribeSender {
    private MtvChannelSubscribeSender() {
    }

    public static void send(MtvChannelSubscriptionRequest request) {
        MtvChannelClientPacketSender.send(new MtvChannelClientSubscribePayload(request));
    }
}
