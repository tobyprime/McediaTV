package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvHudBindingPayload(String channelId) implements CustomPacketPayload {
    public MtvHudBindingPayload {
        channelId = channelId != null ? channelId : "";
    }

    public static final Type<MtvHudBindingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "hud_binding"));
    public static final StreamCodec<FriendlyByteBuf, MtvHudBindingPayload> CODEC = CustomPacketPayload.codec(MtvHudBindingPayload::write, MtvHudBindingPayload::new);

    private MtvHudBindingPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readHudBinding(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeHudBinding(buffer, channelId);
    }

    @Override
    public Type<MtvHudBindingPayload> type() {
        return TYPE;
    }
}
