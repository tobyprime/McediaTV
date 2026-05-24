package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MtvChannelSubscribeSender {
    private MtvChannelSubscribeSender() {
    }

    public static void send(MtvChannelSubscriptionRequest request) {
        ClientPlayNetworking.send(new MtvChannelClientSubscribePayload(request));
    }
}
