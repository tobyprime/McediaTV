package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientSubscribePayload(MtvChannelSubscriptionRequest request) implements CustomPacketPayload {
    public static final Type<MtvChannelClientSubscribePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_subscribe"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientSubscribePayload> CODEC = CustomPacketPayload.codec(MtvChannelClientSubscribePayload::write, MtvChannelClientSubscribePayload::new);

    private MtvChannelClientSubscribePayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readSubscription(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSubscription(buffer, request);
    }

    @Override
    public Type<MtvChannelClientSubscribePayload> type() {
        return TYPE;
    }
}
