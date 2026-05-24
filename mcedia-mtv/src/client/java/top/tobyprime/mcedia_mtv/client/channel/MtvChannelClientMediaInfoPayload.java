package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientMediaInfoPayload(MtvMediaInfoReport report) implements CustomPacketPayload {
    public static final Type<MtvChannelClientMediaInfoPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_media_info"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientMediaInfoPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientMediaInfoPayload::write, MtvChannelClientMediaInfoPayload::new);

    private MtvChannelClientMediaInfoPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readMediaInfo(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeMediaInfo(buffer, report);
    }

    @Override
    public Type<MtvChannelClientMediaInfoPayload> type() {
        return TYPE;
    }
}
