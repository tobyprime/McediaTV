package top.tobyprime.mcedia_mtv.client.channel;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class MtvChannelMediaInfoSender {
    private MtvChannelMediaInfoSender() {
    }

    public static void send(MtvMediaInfoReport report) {
        ClientPlayNetworking.send(new MtvChannelClientMediaInfoPayload(report));
    }
}
