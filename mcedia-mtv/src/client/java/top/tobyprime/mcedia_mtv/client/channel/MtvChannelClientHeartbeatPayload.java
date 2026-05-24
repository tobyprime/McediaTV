package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientHeartbeatPayload(MtvAudienceHeartbeat heartbeat) implements CustomPacketPayload {
    public static final Type<MtvChannelClientHeartbeatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_heartbeat"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientHeartbeatPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientHeartbeatPayload::write, MtvChannelClientHeartbeatPayload::new);

    private MtvChannelClientHeartbeatPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readHeartbeat(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeHeartbeat(buffer, heartbeat);
    }

    @Override
    public Type<MtvChannelClientHeartbeatPayload> type() {
        return TYPE;
    }
}
