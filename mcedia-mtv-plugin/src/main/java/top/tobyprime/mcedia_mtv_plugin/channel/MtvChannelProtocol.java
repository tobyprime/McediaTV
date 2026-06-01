package top.tobyprime.mcedia_mtv_plugin.channel;

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

    public static byte[] encodeSnapshot(ChannelSnapshot snapshot) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeSnapshot(buffer, snapshot);
        return toBytes(buffer);
    }

    public static byte[] encodeSubscription(MtvChannelSubscriptionRequest request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeSubscription(buffer, request);
        return toBytes(buffer);
    }

    public static ChannelSnapshot decodeSnapshot(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readSnapshot(buffer);
    }

    public static byte[] encodeRemove(String channelId) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeRemove(buffer, channelId);
        return toBytes(buffer);
    }

    public static String decodeRemove(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readRemove(buffer);
    }

    public static MtvChannelSubscriptionRequest decodeSubscription(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readSubscription(buffer);
    }

    public static byte[] encodeHeartbeat(MtvAudienceHeartbeat heartbeat) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeHeartbeat(buffer, heartbeat);
        return toBytes(buffer);
    }

    public static MtvAudienceHeartbeat decodeHeartbeat(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readHeartbeat(buffer);
    }


    public static void writeSnapshot(FriendlyByteBuf buffer, ChannelSnapshot snapshot) {
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

    public static void writeSubscription(FriendlyByteBuf buffer, MtvChannelSubscriptionRequest request) {
        buffer.writeUtf(request.channelId());
    }

    public static ChannelSnapshot readSnapshot(FriendlyByteBuf buffer) {
        var snapshot = new ChannelSnapshot(
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
                buffer.isReadable() && buffer.readBoolean()
        );
        ensureFullyRead(buffer, "snapshot");
        return snapshot;
    }

    public static void writeRemove(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId);
    }

    public static String readRemove(FriendlyByteBuf buffer) {
        var channelId = buffer.readUtf();
        ensureFullyRead(buffer, "remove");
        return channelId;
    }

    public static MtvChannelSubscriptionRequest readSubscription(FriendlyByteBuf buffer) {
        var request = new MtvChannelSubscriptionRequest(buffer.readUtf());
        ensureFullyRead(buffer, "subscription");
        return request;
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


    private static byte[] toBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
