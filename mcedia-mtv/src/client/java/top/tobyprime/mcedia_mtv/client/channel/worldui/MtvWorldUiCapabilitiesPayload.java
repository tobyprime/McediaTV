package top.tobyprime.mcedia_mtv.client.channel.worldui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

public record MtvWorldUiCapabilitiesPayload(WorldUiCapabilities capabilities) implements CustomPacketPayload {
    public static final Type<MtvWorldUiCapabilitiesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "world_ui_capabilities"));
    public static final StreamCodec<FriendlyByteBuf, MtvWorldUiCapabilitiesPayload> CODEC =
            CustomPacketPayload.codec(MtvWorldUiCapabilitiesPayload::write, MtvWorldUiCapabilitiesPayload::new);

    private MtvWorldUiCapabilitiesPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readCapabilities(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeCapabilities(buffer, capabilities);
    }

    @Override
    public Type<MtvWorldUiCapabilitiesPayload> type() {
        return TYPE;
    }
}
