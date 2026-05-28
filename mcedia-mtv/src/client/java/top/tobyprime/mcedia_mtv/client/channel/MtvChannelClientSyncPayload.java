package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientSyncPayload(ClientChannelPlaybackSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<MtvChannelClientSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_sync"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientSyncPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientSyncPayload::write, MtvChannelClientSyncPayload::new);

    private MtvChannelClientSyncPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readSnapshot(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSnapshot(buffer, snapshot);
    }

    @Override
    public Type<MtvChannelClientSyncPayload> type() {
        return TYPE;
    }
}
