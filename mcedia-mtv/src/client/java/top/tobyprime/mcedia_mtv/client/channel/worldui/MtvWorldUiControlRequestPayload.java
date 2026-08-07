package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiControlRequestPayload(WorldUiControlRequest request) implements CustomPacketPayload {
    public static final Type<MtvWorldUiControlRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_control_request"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiControlRequestPayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiControlRequestPayload::write, MtvWorldUiControlRequestPayload::new);

    private MtvWorldUiControlRequestPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readControlRequest(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeControlRequest(buffer, request);
    }

    @Override
    public Type<MtvWorldUiControlRequestPayload> type() {
        return TYPE;
    }
}
