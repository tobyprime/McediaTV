package top.tobyprime.mcedia_mtv_plugin.channel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public final class MtvChannelProtocol {
    public static final String CHANNEL_HELLO = "mcedia_mtv:channel_hello";
    public static final String CHANNEL_SUBSCRIBE = "mcedia_mtv:channel_subscribe";
    public static final String CHANNEL_SNAPSHOT = "mcedia_mtv:channel_snapshot";
    public static final String CHANNEL_REMOVE = "mcedia_mtv:channel_remove";
    public static final String CHANNEL_HEARTBEAT = "mcedia_mtv:channel_heartbeat";
    public static final String CHANNEL_MEDIA_INFO = "mcedia_mtv:channel_media_info";

    private MtvChannelProtocol() {
    }

    public static byte[] encodeHello(UUID sessionId) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUUID(sessionId);
        return toBytes(buffer);
    }

    public static UUID decodeHello(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return buffer.readUUID();
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

    public static byte[] encodeMediaInfo(MtvMediaInfoReport report) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeMediaInfo(buffer, report);
        return toBytes(buffer);
    }

    public static MtvMediaInfoReport decodeMediaInfo(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readMediaInfo(buffer);
    }

    public static void writeSnapshot(FriendlyByteBuf buffer, ChannelSnapshot snapshot) {
        buffer.writeUtf(snapshot.channelId());
        buffer.writeLong(snapshot.revision());
        buffer.writeUtf(snapshot.mediaUrl());
        buffer.writeFloat(snapshot.speed());
        buffer.writeLong(snapshot.startAt());
        buffer.writeLong(snapshot.baseTime());
        buffer.writeLong(snapshot.baseOffset());
        buffer.writeBoolean(snapshot.paused());
        buffer.writeLong(snapshot.resolvedDurationUs());
        buffer.writeBoolean(snapshot.completed());
    }

    public static void writeSubscription(FriendlyByteBuf buffer, MtvChannelSubscriptionRequest request) {
        buffer.writeUtf(request.channelId());
        buffer.writeUUID(request.sessionId());
    }

    public static ChannelSnapshot readSnapshot(FriendlyByteBuf buffer) {
        return new ChannelSnapshot(
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readBoolean()
        );
    }

    public static void writeRemove(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId);
    }

    public static String readRemove(FriendlyByteBuf buffer) {
        return buffer.readUtf();
    }

    public static MtvChannelSubscriptionRequest readSubscription(FriendlyByteBuf buffer) {
        return new MtvChannelSubscriptionRequest(
                buffer.readUtf(),
                buffer.readUUID()
        );
    }

    public static void writeHeartbeat(FriendlyByteBuf buffer, MtvAudienceHeartbeat heartbeat) {
        buffer.writeUtf(heartbeat.channelId());
        buffer.writeUUID(heartbeat.sessionId());
        buffer.writeLong(heartbeat.revision());
        buffer.writeUtf(heartbeat.state());
        buffer.writeLong(heartbeat.clientMediaTimeUs());
        buffer.writeFloat(heartbeat.clientPlaybackRate());
        buffer.writeUtf(heartbeat.loadedMediaId());
        buffer.writeLong(heartbeat.durationUs());
    }

    public static MtvAudienceHeartbeat readHeartbeat(FriendlyByteBuf buffer) {
        return new MtvAudienceHeartbeat(
                buffer.readUtf(),
                buffer.readUUID(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readFloat(),
                buffer.readUtf(),
                buffer.readLong()
        );
    }

    public static void writeMediaInfo(FriendlyByteBuf buffer, MtvMediaInfoReport report) {
        buffer.writeUtf(report.channelId());
        buffer.writeUUID(report.sessionId());
        buffer.writeLong(report.revision());
        buffer.writeUtf(report.loadedMediaId());
        buffer.writeLong(report.durationUs());
    }

    public static MtvMediaInfoReport readMediaInfo(FriendlyByteBuf buffer) {
        return new MtvMediaInfoReport(
                buffer.readUtf(),
                buffer.readUUID(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readLong()
        );
    }

    private static byte[] toBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
