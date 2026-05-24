package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record MtvChannelClientHandshakePayload(UUID sessionId) implements CustomPacketPayload {
    public static final Type<MtvChannelClientHandshakePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_hello"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientHandshakePayload> CODEC = CustomPacketPayload.codec(MtvChannelClientHandshakePayload::write, MtvChannelClientHandshakePayload::new);

    private MtvChannelClientHandshakePayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
    }

    @Override
    public Type<MtvChannelClientHandshakePayload> type() {
        return TYPE;
    }
}
