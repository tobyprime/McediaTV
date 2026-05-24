package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MtvChannelClientSnapshotPayload(ClientChannelPlaybackSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<MtvChannelClientSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientSnapshotPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientSnapshotPayload::write, MtvChannelClientSnapshotPayload::new);

    private MtvChannelClientSnapshotPayload(FriendlyByteBuf buffer) {
        this(MtvChannelProtocol.readSnapshot(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSnapshot(buffer, snapshot);
    }

    @Override
    public Type<MtvChannelClientSnapshotPayload> type() {
        return TYPE;
    }
}
