package top.tobyprime.mcedia_mtv.client.channel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class MtvChannelProtocol {
    public static final String CHANNEL_SUBSCRIBE = "mcedia_mtv:channel_subscribe";
    public static final String CHANNEL_UNSUBSCRIBE = "mcedia_mtv:channel_unsubscribe";
    public static final String CHANNEL_SNAPSHOT = "mcedia_mtv:channel_snapshot";
    public static final String CHANNEL_SYNC = "mcedia_mtv:channel_sync";
    public static final String CHANNEL_REMOVE = "mcedia_mtv:channel_remove";
    public static final String CHANNEL_HEARTBEAT = "mcedia_mtv:channel_heartbeat";

    private MtvChannelProtocol() {
    }

    private static IllegalArgumentException invalidPacket(String packetType, String detail) {
        return new IllegalArgumentException("Invalid MTV " + packetType + " packet: " + detail);
    }

    private static void ensureFullyRead(FriendlyByteBuf buffer, String packetType) {
        if (buffer.isReadable()) {
            throw invalidPacket(packetType, "unexpected trailing " + buffer.readableBytes() + " bytes");
        }
    }

    public static ClientChannelPlaybackSnapshot readSnapshot(FriendlyByteBuf buffer) {
        var snapshot = new ClientChannelPlaybackSnapshot(
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.isReadable() && buffer.readBoolean(),
                0L
        );
        ensureFullyRead(buffer, "snapshot");
        return snapshot;
    }

    public static void writeSnapshot(FriendlyByteBuf buffer, ClientChannelPlaybackSnapshot snapshot) {
        buffer.writeUtf(snapshot.channelId());
        buffer.writeLong(snapshot.revision());
        buffer.writeUtf(snapshot.mediaUrl());
        buffer.writeFloat(snapshot.speed());
        buffer.writeLong(snapshot.anchorMediaTimeUs());
        buffer.writeLong(snapshot.elapsedTimeMs());
        buffer.writeUtf(snapshot.state());
        buffer.writeBoolean(snapshot.paused());
        buffer.writeLong(snapshot.resolvedDurationUs());
        buffer.writeBoolean(snapshot.completed());
        buffer.writeBoolean(snapshot.audienceSuspended());
    }

    public static String readRemove(FriendlyByteBuf buffer) {
        var channelId = buffer.readUtf();
        ensureFullyRead(buffer, "remove");
        return channelId;
    }

    public static void writeRemove(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId);
    }

    public static MtvAudienceHeartbeat readHeartbeat(FriendlyByteBuf buffer) {
        String channelId = buffer.readUtf();
        long revision = buffer.readLong();
        boolean loaded = buffer.readBoolean();
        boolean completed = buffer.readBoolean();
        long durationUs = buffer.readLong();
        boolean error = buffer.isReadable() && buffer.readBoolean();
        boolean suspended = buffer.isReadable() && buffer.readBoolean();
        ensureFullyRead(buffer, "heartbeat");
        return new MtvAudienceHeartbeat(channelId, revision, loaded, completed, durationUs, error, suspended);
    }

    public static void writeHeartbeat(FriendlyByteBuf buffer, MtvAudienceHeartbeat heartbeat) {
        buffer.writeUtf(heartbeat.channelId());
        buffer.writeLong(heartbeat.revision());
        buffer.writeBoolean(heartbeat.loaded());
        buffer.writeBoolean(heartbeat.completed());
        buffer.writeLong(heartbeat.durationUs());
        buffer.writeBoolean(heartbeat.error());
        buffer.writeBoolean(heartbeat.suspended());
    }


    public static MtvChannelSubscriptionRequest readSubscription(FriendlyByteBuf buffer) {
        var request = new MtvChannelSubscriptionRequest(buffer.readUtf());
        ensureFullyRead(buffer, "subscription");
        return request;
    }

    public static void writeSubscription(FriendlyByteBuf buffer, MtvChannelSubscriptionRequest request) {
        buffer.writeUtf(request.channelId());
    }

    public static byte[] encodeSubscription(MtvChannelSubscriptionRequest request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeSubscription(buffer, request);
        return toBytes(buffer);
    }

    public static MtvChannelSubscriptionRequest decodeSubscription(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readSubscription(buffer);
    }

    private static byte[] toBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
