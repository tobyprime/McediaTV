package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record MtvChannelClientSyncPayload(ClientChannelPlaybackSnapshot snapshot) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelClientSyncPayload.class);

    public static final Type<MtvChannelClientSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_sync"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelClientSyncPayload> CODEC = CustomPacketPayload.codec(MtvChannelClientSyncPayload::write, MtvChannelClientSyncPayload::new);

    private MtvChannelClientSyncPayload(FriendlyByteBuf buffer) {
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
            LOGGER.warn("Failed to decode MTV sync payload, discarded {} trailing bytes", trailingBytes, e);
            return ClientChannelPlaybackSnapshot.EMPTY;
        }
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeSnapshot(buffer, snapshot);
    }

    @Override
    public Type<MtvChannelClientSyncPayload> type() {
        return TYPE;
    }
}
