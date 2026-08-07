package top.tobyprime.mcedia_mtv.client.channel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistManifest;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiPlaylistPage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlOperation;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiControlResult;
import top.tobyprime.mcedia_mtv.client.channel.worldui.WorldUiCapabilities;

public final class MtvChannelProtocol {
    public static final String CHANNEL_SUBSCRIBE = "mcedia_mtv:channel_subscribe";
    public static final String CHANNEL_UNSUBSCRIBE = "mcedia_mtv:channel_unsubscribe";
    public static final String CHANNEL_SNAPSHOT = "mcedia_mtv:channel_snapshot";
    public static final String CHANNEL_SYNC = "mcedia_mtv:channel_sync";
    public static final String CHANNEL_REMOVE = "mcedia_mtv:channel_remove";
    public static final String CHANNEL_HEARTBEAT = "mcedia_mtv:channel_heartbeat";
    public static final String WORLD_UI_CAPABILITIES = "mcedia_mtv:world_ui_capabilities";
    public static final String CHANNEL_PLAYLIST_MANIFEST = "mcedia_mtv:channel_playlist_manifest";
    public static final String CHANNEL_PLAYLIST_PAGE_REQUEST = "mcedia_mtv:channel_playlist_page_request";
    public static final String CHANNEL_PLAYLIST_PAGE = "mcedia_mtv:channel_playlist_page";
    public static final String CHANNEL_CONTROL_REQUEST = "mcedia_mtv:channel_control_request";
    public static final String CHANNEL_CONTROL_RESULT = "mcedia_mtv:channel_control_result";
    public static final int WORLD_UI_PROTOCOL_VERSION = 1;
    public static final int MAX_PLAYLIST_PAGE_ITEMS = 32;
    public static final int MAX_PLAYLIST_PAGE_BYTES = 24 * 1024;
    public static final int MAX_MEDIA_URL_LENGTH = 2_048;

    private static final int MAX_CHANNEL_ID_LENGTH = 256;
    private static final int MAX_SCREEN_ID_LENGTH = 128;
    private static final int MAX_PLAY_ORDER_MODE_LENGTH = 32;
    private static final int MAX_CONTROL_REQUEST_BYTES = 4 * 1024;
    private static final int MAX_CONTROL_RESULT_BYTES = 64;
    private static final Set<String> PLAY_ORDER_MODES = Set.of("SEQUENTIAL", "LOOP", "SHUFFLE", "CURRENT_ONLY");

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

    public static void writeHudBinding(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId != null ? channelId : "");
    }

    public static String readHudBinding(FriendlyByteBuf buffer) {
        return buffer.readUtf();
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

    public static byte[] encodePlaylistManifest(WorldUiPlaylistManifest manifest) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writePlaylistManifest(buffer, manifest);
        return toBytes(buffer);
    }

    public static WorldUiPlaylistManifest decodePlaylistManifest(byte[] message) {
        return readPlaylistManifest(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writePlaylistManifest(FriendlyByteBuf buffer, WorldUiPlaylistManifest manifest) {
        validateManifest(manifest);
        buffer.writeUtf(manifest.channelId(), MAX_CHANNEL_ID_LENGTH);
        buffer.writeLong(manifest.revision());
        buffer.writeVarInt(manifest.itemCount());
        buffer.writeVarInt(manifest.cursor());
        buffer.writeUtf(manifest.playOrderMode(), MAX_PLAY_ORDER_MODE_LENGTH);
    }

    public static WorldUiPlaylistManifest readPlaylistManifest(FriendlyByteBuf buffer) {
        var manifest = new WorldUiPlaylistManifest(
                readNonBlankUtf(buffer, MAX_CHANNEL_ID_LENGTH, "channelId"),
                readNonNegativeLong(buffer, "revision"),
                readNonNegativeInt(buffer, "itemCount"),
                readNonNegativeInt(buffer, "cursor"),
                readPlayOrderMode(buffer)
        );
        validateManifest(manifest);
        ensureFullyRead(buffer, "playlist manifest");
        return manifest;
    }

    public static byte[] encodePlaylistPage(WorldUiPlaylistPage page) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writePlaylistPage(buffer, page);
        byte[] encoded = toBytes(buffer);
        if (encoded.length > MAX_PLAYLIST_PAGE_BYTES) {
            throw invalidPacket("playlist page", "encoded length exceeds " + MAX_PLAYLIST_PAGE_BYTES + " bytes");
        }
        return encoded;
    }

    public static WorldUiPlaylistPage decodePlaylistPage(byte[] message) {
        if (message.length > MAX_PLAYLIST_PAGE_BYTES) {
            throw invalidPacket("playlist page", "encoded length exceeds " + MAX_PLAYLIST_PAGE_BYTES + " bytes");
        }
        return readPlaylistPage(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writePlaylistPage(FriendlyByteBuf buffer, WorldUiPlaylistPage page) {
        validatePage(page);
        buffer.writeUtf(page.channelId(), MAX_CHANNEL_ID_LENGTH);
        buffer.writeLong(page.revision());
        buffer.writeVarInt(page.itemCount());
        buffer.writeVarInt(page.cursor());
        buffer.writeUtf(page.playOrderMode(), MAX_PLAY_ORDER_MODE_LENGTH);
        buffer.writeVarInt(page.offset());
        buffer.writeVarInt(page.mediaUrls().size());
        for (String mediaUrl : page.mediaUrls()) {
            buffer.writeUtf(mediaUrl, MAX_MEDIA_URL_LENGTH);
        }
    }

    public static WorldUiPlaylistPage readPlaylistPage(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_PLAYLIST_PAGE_BYTES) {
            throw invalidPacket("playlist page", "encoded length exceeds " + MAX_PLAYLIST_PAGE_BYTES + " bytes");
        }
        String channelId = readNonBlankUtf(buffer, MAX_CHANNEL_ID_LENGTH, "channelId");
        long revision = readNonNegativeLong(buffer, "revision");
        int itemCount = readNonNegativeInt(buffer, "itemCount");
        int cursor = readNonNegativeInt(buffer, "cursor");
        String playOrderMode = readPlayOrderMode(buffer);
        int offset = readNonNegativeInt(buffer, "offset");
        int pageSize = readNonNegativeInt(buffer, "pageSize");
        if (pageSize > MAX_PLAYLIST_PAGE_ITEMS) {
            throw invalidPacket("playlist page", "contains more than " + MAX_PLAYLIST_PAGE_ITEMS + " entries");
        }
        var mediaUrls = new java.util.ArrayList<String>(pageSize);
        for (int index = 0; index < pageSize; index++) {
            mediaUrls.add(readNonBlankUtf(buffer, MAX_MEDIA_URL_LENGTH, "mediaUrl"));
        }
        var page = new WorldUiPlaylistPage(channelId, revision, itemCount, cursor, playOrderMode, offset, mediaUrls);
        validatePage(page);
        ensureFullyRead(buffer, "playlist page");
        return page;
    }

    public static byte[] encodeControlRequest(WorldUiControlRequest request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeControlRequest(buffer, request);
        byte[] encoded = toBytes(buffer);
        if (encoded.length > MAX_CONTROL_REQUEST_BYTES) {
            throw invalidPacket("control request", "encoded length exceeds " + MAX_CONTROL_REQUEST_BYTES + " bytes");
        }
        return encoded;
    }

    public static WorldUiControlRequest decodeControlRequest(byte[] message) {
        if (message.length > MAX_CONTROL_REQUEST_BYTES) {
            throw invalidPacket("control request", "encoded length exceeds " + MAX_CONTROL_REQUEST_BYTES + " bytes");
        }
        return readControlRequest(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writeControlRequest(FriendlyByteBuf buffer, WorldUiControlRequest request) {
        validateControlRequest(request);
        buffer.writeUUID(request.targetMtvUuid());
        buffer.writeUtf(request.screenId(), MAX_SCREEN_ID_LENGTH);
        buffer.writeUtf(request.channelId(), MAX_CHANNEL_ID_LENGTH);
        buffer.writeLong(request.requestId());
        buffer.writeLong(request.expectedRevision());
        buffer.writeFloat(request.hitU());
        buffer.writeFloat(request.hitV());
        buffer.writeVarInt(request.operation().ordinal());
        writeControlArgument(buffer, request.operation(), request.argument());
    }

    public static WorldUiControlRequest readControlRequest(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_CONTROL_REQUEST_BYTES) {
            throw invalidPacket("control request", "encoded length exceeds " + MAX_CONTROL_REQUEST_BYTES + " bytes");
        }
        UUID targetMtvUuid = buffer.readUUID();
        String screenId = readNonBlankUtf(buffer, MAX_SCREEN_ID_LENGTH, "screenId");
        String channelId = readNonBlankUtf(buffer, MAX_CHANNEL_ID_LENGTH, "channelId");
        long requestId = readNonNegativeLong(buffer, "requestId");
        long expectedRevision = readNonNegativeLong(buffer, "expectedRevision");
        float hitU = readScreenUv(buffer, "hitU");
        float hitV = readScreenUv(buffer, "hitV");
        WorldUiControlOperation operation = readEnum(buffer, WorldUiControlOperation.values(), "control operation");
        WorldUiControlArgument argument = readControlArgument(buffer, operation);
        var request = new WorldUiControlRequest(targetMtvUuid, screenId, channelId, requestId, expectedRevision, hitU, hitV, operation, argument);
        validateControlRequest(request);
        ensureFullyRead(buffer, "control request");
        return request;
    }

    public static byte[] encodeControlResult(WorldUiControlResult result) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeControlResult(buffer, result);
        byte[] encoded = toBytes(buffer);
        if (encoded.length > MAX_CONTROL_RESULT_BYTES) {
            throw invalidPacket("control result", "encoded length exceeds " + MAX_CONTROL_RESULT_BYTES + " bytes");
        }
        return encoded;
    }

    public static WorldUiControlResult decodeControlResult(byte[] message) {
        if (message.length > MAX_CONTROL_RESULT_BYTES) {
            throw invalidPacket("control result", "encoded length exceeds " + MAX_CONTROL_RESULT_BYTES + " bytes");
        }
        return readControlResult(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writeControlResult(FriendlyByteBuf buffer, WorldUiControlResult result) {
        validateControlResult(result);
        buffer.writeLong(result.requestId());
        buffer.writeBoolean(result.accepted());
        buffer.writeVarInt(result.error().ordinal());
        buffer.writeLong(result.revision());
    }

    public static WorldUiControlResult readControlResult(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_CONTROL_RESULT_BYTES) {
            throw invalidPacket("control result", "encoded length exceeds " + MAX_CONTROL_RESULT_BYTES + " bytes");
        }
        var result = new WorldUiControlResult(
                readNonNegativeLong(buffer, "requestId"),
                buffer.readBoolean(),
                readEnum(buffer, WorldUiControlError.values(), "control error"),
                readNonNegativeLong(buffer, "revision")
        );
        validateControlResult(result);
        ensureFullyRead(buffer, "control result");
        return result;
    }

    public static byte[] encodeCapabilities(WorldUiCapabilities capabilities) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeCapabilities(buffer, capabilities);
        return toBytes(buffer);
    }

    public static WorldUiCapabilities decodeCapabilities(byte[] message) {
        return readCapabilities(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writeCapabilities(FriendlyByteBuf buffer, WorldUiCapabilities capabilities) {
        validateCapabilities(capabilities);
        buffer.writeVarInt(capabilities.protocolVersion());
        buffer.writeVarInt(capabilities.maxPageItems());
        buffer.writeLong(capabilities.featureFlags());
    }

    public static WorldUiCapabilities readCapabilities(FriendlyByteBuf buffer) {
        var capabilities = new WorldUiCapabilities(
                readNonNegativeInt(buffer, "protocolVersion"),
                readNonNegativeInt(buffer, "maxPageItems"),
                readNonNegativeLong(buffer, "featureFlags")
        );
        validateCapabilities(capabilities);
        ensureFullyRead(buffer, "world UI capabilities");
        return capabilities;
    }

    private static void validateControlRequest(WorldUiControlRequest request) {
        if (request == null || request.targetMtvUuid() == null || request.operation() == null || request.argument() == null) {
            throw invalidPacket("control request", "required field is missing");
        }
        validateScreenId(request.screenId());
        validateChannelId(request.channelId());
        validateRevision(request.expectedRevision());
        if (request.requestId() < 0L) {
            throw invalidPacket("control request", "requestId is negative");
        }
        validateScreenUv(request.hitU(), "hitU");
        validateScreenUv(request.hitV(), "hitV");
        validateControlArgument(request.operation(), request.argument());
    }

    private static void validateControlResult(WorldUiControlResult result) {
        if (result == null || result.error() == null || result.requestId() < 0L || result.revision() < 0L) {
            throw invalidPacket("control result", "field is invalid");
        }
        if (result.accepted() != (result.error() == WorldUiControlError.NONE)) {
            throw invalidPacket("control result", "accepted and error are inconsistent");
        }
    }

    private static void validateCapabilities(WorldUiCapabilities capabilities) {
        if (capabilities == null
                || capabilities.protocolVersion() != WORLD_UI_PROTOCOL_VERSION
                || capabilities.maxPageItems() <= 0
                || capabilities.maxPageItems() > MAX_PLAYLIST_PAGE_ITEMS
                || capabilities.featureFlags() < 0L) {
            throw invalidPacket("world UI capabilities", "field is invalid");
        }
    }

    private static void writeControlArgument(FriendlyByteBuf buffer, WorldUiControlOperation operation, WorldUiControlArgument argument) {
        switch (operation) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> { }
            case SEEK_ABSOLUTE, SEEK_RELATIVE -> buffer.writeLong(((WorldUiControlArgument.PositionUs) argument).value());
            case SET_SPEED, SET_MASTER_VOLUME -> buffer.writeFloat(((WorldUiControlArgument.Scalar) argument).value());
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK -> buffer.writeVarInt(((WorldUiControlArgument.PlaylistIndex) argument).value());
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> buffer.writeUtf(((WorldUiControlArgument.MediaUrl) argument).value(), MAX_MEDIA_URL_LENGTH);
            case SET_PLAY_ORDER -> buffer.writeUtf(((WorldUiControlArgument.PlayOrderMode) argument).value(), MAX_PLAY_ORDER_MODE_LENGTH);
        }
    }

    private static WorldUiControlArgument readControlArgument(FriendlyByteBuf buffer, WorldUiControlOperation operation) {
        return switch (operation) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> WorldUiControlArgument.None.INSTANCE;
            case SEEK_ABSOLUTE, SEEK_RELATIVE -> new WorldUiControlArgument.PositionUs(buffer.readLong());
            case SET_SPEED, SET_MASTER_VOLUME -> new WorldUiControlArgument.Scalar(buffer.readFloat());
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK -> new WorldUiControlArgument.PlaylistIndex(readNonNegativeInt(buffer, "playlist index"));
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> new WorldUiControlArgument.MediaUrl(readNonBlankUtf(buffer, MAX_MEDIA_URL_LENGTH, "mediaUrl"));
            case SET_PLAY_ORDER -> new WorldUiControlArgument.PlayOrderMode(readPlayOrderMode(buffer));
        };
    }

    private static void validateControlArgument(WorldUiControlOperation operation, WorldUiControlArgument argument) {
        switch (operation) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> requireArgument(argument, WorldUiControlArgument.None.class, operation);
            case SEEK_ABSOLUTE -> {
                var value = requireArgument(argument, WorldUiControlArgument.PositionUs.class, operation).value();
                if (value < 0L) throw invalidPacket("control request", "absolute seek is negative");
            }
            case SEEK_RELATIVE -> requireArgument(argument, WorldUiControlArgument.PositionUs.class, operation);
            case SET_SPEED -> {
                float value = requireArgument(argument, WorldUiControlArgument.Scalar.class, operation).value();
                if (!Float.isFinite(value) || value < 0.25F || value > 4.0F) throw invalidPacket("control request", "speed is invalid");
            }
            case SET_MASTER_VOLUME -> {
                float value = requireArgument(argument, WorldUiControlArgument.Scalar.class, operation).value();
                if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) throw invalidPacket("control request", "volume is invalid");
            }
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK -> {
                if (requireArgument(argument, WorldUiControlArgument.PlaylistIndex.class, operation).value() < 0) {
                    throw invalidPacket("control request", "playlist index is negative");
                }
            }
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> {
                String value = requireArgument(argument, WorldUiControlArgument.MediaUrl.class, operation).value();
                if (value == null || value.isBlank() || value.length() > MAX_MEDIA_URL_LENGTH) {
                    throw invalidPacket("control request", "mediaUrl is invalid");
                }
            }
            case SET_PLAY_ORDER -> validatePlayOrderMode(requireArgument(argument, WorldUiControlArgument.PlayOrderMode.class, operation).value());
        }
    }

    private static <T extends WorldUiControlArgument> T requireArgument(WorldUiControlArgument argument, Class<T> expectedType, WorldUiControlOperation operation) {
        if (!expectedType.isInstance(argument)) {
            throw invalidPacket("control request", "argument does not match " + operation);
        }
        return expectedType.cast(argument);
    }

    private static void validateScreenId(String screenId) {
        if (screenId == null || screenId.isBlank() || screenId.length() > MAX_SCREEN_ID_LENGTH) {
            throw invalidPacket("control request", "screenId is invalid");
        }
    }

    private static float readScreenUv(FriendlyByteBuf buffer, String field) {
        float value = buffer.readFloat();
        validateScreenUv(value, field);
        return value;
    }

    private static void validateScreenUv(float value, String field) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw invalidPacket("control request", field + " is outside the screen");
        }
    }

    private static <T> T readEnum(FriendlyByteBuf buffer, T[] values, String field) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw invalidPacket("world UI", field + " is unknown");
        }
        return values[ordinal];
    }

    private static void validateManifest(WorldUiPlaylistManifest manifest) {
        if (manifest == null) {
            throw invalidPacket("playlist manifest", "is missing");
        }
        validateChannelId(manifest.channelId());
        validateRevision(manifest.revision());
        validatePlaylistPosition(manifest.itemCount(), manifest.cursor(), "playlist manifest");
        validatePlayOrderMode(manifest.playOrderMode());
    }

    private static void validatePage(WorldUiPlaylistPage page) {
        if (page == null) {
            throw invalidPacket("playlist page", "is missing");
        }
        validateChannelId(page.channelId());
        validateRevision(page.revision());
        validatePlaylistPosition(page.itemCount(), page.cursor(), "playlist page");
        validatePlayOrderMode(page.playOrderMode());
        if (page.offset() < 0 || page.offset() > page.itemCount()) {
            throw invalidPacket("playlist page", "offset is outside the playlist");
        }
        List<String> mediaUrls = page.mediaUrls();
        if (mediaUrls.size() > MAX_PLAYLIST_PAGE_ITEMS || page.offset() + mediaUrls.size() > page.itemCount()) {
            throw invalidPacket("playlist page", "item range is invalid");
        }
        for (String mediaUrl : mediaUrls) {
            if (mediaUrl == null || mediaUrl.isBlank() || mediaUrl.length() > MAX_MEDIA_URL_LENGTH) {
                throw invalidPacket("playlist page", "mediaUrl is invalid");
            }
        }
    }

    private static void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank() || channelId.length() > MAX_CHANNEL_ID_LENGTH) {
            throw invalidPacket("playlist", "channelId is invalid");
        }
    }

    private static void validateRevision(long revision) {
        if (revision < 0L) {
            throw invalidPacket("playlist", "revision is negative");
        }
    }

    private static void validatePlaylistPosition(int itemCount, int cursor, String packetType) {
        if (itemCount < 0 || cursor < 0 || (itemCount == 0 && cursor != 0) || (itemCount > 0 && cursor >= itemCount)) {
            throw invalidPacket(packetType, "itemCount or cursor is invalid");
        }
    }

    private static void validatePlayOrderMode(String playOrderMode) {
        if (playOrderMode == null || !PLAY_ORDER_MODES.contains(playOrderMode)) {
            throw invalidPacket("playlist", "playOrderMode is invalid");
        }
    }

    private static String readNonBlankUtf(FriendlyByteBuf buffer, int maxLength, String field) {
        String value = buffer.readUtf(maxLength);
        if (value.isBlank()) {
            throw invalidPacket("playlist", field + " is blank");
        }
        return value;
    }

    private static String readPlayOrderMode(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_PLAY_ORDER_MODE_LENGTH);
        validatePlayOrderMode(value);
        return value;
    }

    private static long readNonNegativeLong(FriendlyByteBuf buffer, String field) {
        long value = buffer.readLong();
        if (value < 0L) {
            throw invalidPacket("playlist", field + " is negative");
        }
        return value;
    }

    private static int readNonNegativeInt(FriendlyByteBuf buffer, String field) {
        int value = buffer.readVarInt();
        if (value < 0) {
            throw invalidPacket("playlist", field + " is negative");
        }
        return value;
    }

    private static byte[] toBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
