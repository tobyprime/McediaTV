package top.tobyprime.mcedia_mtv.client.channel;

public final class MtvChannelUnsubscribeSender {
    private MtvChannelUnsubscribeSender() {
    }

    public static void send(MtvChannelSubscriptionRequest request) {
        MtvChannelClientPacketSender.send(new MtvChannelClientUnsubscribePayload(request));
    }
}
