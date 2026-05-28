package top.tobyprime.mcedia_mtv.client.channel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record MtvChannelRemovePayload(String channelId) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvChannelRemovePayload.class);

    public static final Type<MtvChannelRemovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("mcedia_mtv", "channel_remove"));
    public static final StreamCodec<FriendlyByteBuf, MtvChannelRemovePayload> CODEC = CustomPacketPayload.codec(MtvChannelRemovePayload::write, MtvChannelRemovePayload::new);

    private MtvChannelRemovePayload(FriendlyByteBuf buffer) {
        this(readRemoveSafely(buffer));
    }

    private static String readRemoveSafely(FriendlyByteBuf buffer) {
        try {
            return MtvChannelProtocol.readRemove(buffer);
        } catch (Exception e) {
            int trailingBytes = buffer.readableBytes();
            if (trailingBytes > 0) {
                buffer.skipBytes(trailingBytes);
            }
            LOGGER.warn("Failed to decode MTV remove payload, discarded {} trailing bytes", trailingBytes, e);
            return "";
        }
    }

    private void write(FriendlyByteBuf buffer) {
        MtvChannelProtocol.writeRemove(buffer, channelId);
    }

    @Override
    public Type<MtvChannelRemovePayload> type() {
        return TYPE;
    }
}
