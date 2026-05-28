package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record MtvChannelClientSnapshotPayload(ClientChannelPlaybackSnapshot snapshot) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelClientSnapshotPayload.class);

    public static final Type<MtvChannelClientSnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientSnapshotPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientSnapshotPayload::write, MtvChannelClientSnapshotPayload::new);

    private MtvChannelClientSnapshotPayload(FriendlyByteBuf buffer) {
        this(readSnapshotSafely(buffer));
    }

    private static ClientChannelPlaybackSnapshot readSnapshotSafely(FriendlyByteBuf buffer) {
        try {
            return MtvChannelProtocol.readSnapshot(buffer);
        } catch (Exception e) {
            int trailingBytes = buffer.readableBytes();
            if (trailingBytes > 0) {
                buffer.skipBytes(trailingBytes);
            }
            LOGGER.warn("Failed to decode MTV snapshot payload, discarded {} trailing bytes", trailingBytes, e);
            return ClientChannelPlaybackSnapshot.EMPTY;
        }
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSnapshot(buffer, snapshot);
    }

    @Override
    public Type<MtvChannelClientSnapshotPayload> type() {
        return TYPE;
    }
}
