package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MtvChannelUnsubscribeSender {
    private MtvChannelUnsubscribeSender() {
    }

    public static void send(MtvChannelSubscriptionRequest request) {
        ClientPlayNetworking.send(new MtvChannelClientUnsubscribePayload(request));
    }
}
