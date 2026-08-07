package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiControlStatePayload(WorldUiControlState state) implements CustomPacketPayload {
    public static final Type<MtvWorldUiControlStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "world_ui_control_state"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiControlStatePayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiControlStatePayload::write, MtvWorldUiControlStatePayload::new);

    private MtvWorldUiControlStatePayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readWorldUiControlState(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeWorldUiControlState(buffer, state);
    }

    @Override
    public Type<MtvWorldUiControlStatePayload> type() {
        return TYPE;
    }
}
