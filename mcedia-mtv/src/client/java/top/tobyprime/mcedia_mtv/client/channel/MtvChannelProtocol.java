package top.tobyprime.mcedia_mtv.client.channel;

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

    public static ClientChannelPlaybackSnapshot readSnapshot(FriendlyByteBuf buffer) {
        return new ClientChannelPlaybackSnapshot(
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

    public static void writeSnapshot(FriendlyByteBuf buffer, ClientChannelPlaybackSnapshot snapshot) {
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

    public static String readRemove(FriendlyByteBuf buffer) {
        return buffer.readUtf();
    }

    public static void writeRemove(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId);
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

    public static MtvMediaInfoReport readMediaInfo(FriendlyByteBuf buffer) {
        return new MtvMediaInfoReport(
                buffer.readUtf(),
                buffer.readUUID(),
                buffer.readLong(),
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

    public static byte[] encodeHello(UUID sessionId) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUUID(sessionId);
        return toBytes(buffer);
    }

    public static UUID decodeHello(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return buffer.readUUID();
    }

    public static MtvChannelSubscriptionRequest readSubscription(FriendlyByteBuf buffer) {
        return new MtvChannelSubscriptionRequest(
                buffer.readUtf(),
                buffer.readUUID()
        );
    }

    public static void writeSubscription(FriendlyByteBuf buffer, MtvChannelSubscriptionRequest request) {
        buffer.writeUtf(request.channelId());
        buffer.writeUUID(request.sessionId());
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
