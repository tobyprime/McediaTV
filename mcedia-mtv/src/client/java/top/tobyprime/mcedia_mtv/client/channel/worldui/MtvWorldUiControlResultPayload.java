package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiControlResultPayload(WorldUiControlResult result) implements CustomPacketPayload {
    public static final Type<MtvWorldUiControlResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_control_result"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiControlResultPayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiControlResultPayload::write, MtvWorldUiControlResultPayload::new);

    private MtvWorldUiControlResultPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readControlResult(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeControlResult(buffer, result);
    }

    @Override
    public Type<MtvWorldUiControlResultPayload> type() {
        return TYPE;
    }
}
