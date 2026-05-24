package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelRemovePayload(String channelId) implements CustomPacketPayload {
    public static final Type<MtvChannelRemovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_remove"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelRemovePayload> CODEC = CustomPacketPayload.codec(MtvChannelRemovePayload::write, MtvChannelRemovePayload::new);

    private MtvChannelRemovePayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readRemove(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeRemove(buffer, channelId);
    }

    @Override
    public Type<MtvChannelRemovePayload> type() {
        return TYPE;
    }
}
