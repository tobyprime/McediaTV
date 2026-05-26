package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientUnsubscribePayload(MtvChannelSubscriptionRequest request) implements CustomPacketPayload {
    public static final Type<MtvChannelClientUnsubscribePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_unsubscribe"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientUnsubscribePayload> CODEC = CustomPacketPayload.codec(MtvChannelClientUnsubscribePayload::write, MtvChannelClientUnsubscribePayload::new);

    private MtvChannelClientUnsubscribePayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readSubscription(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSubscription(buffer, request);
    }

    @Override
    public Type<MtvChannelClientUnsubscribePayload> type() {
        return TYPE;
    }
}
