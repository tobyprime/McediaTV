package top.tobyprime.mcedia_mtv_plugin.channel;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiPlaylistManifest;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiPlaylistPage;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlState;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiPlaylistPageRequest;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiCapabilities;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlArgument;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlError;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlOperation;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlRequest;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiControlResult;
import top.tobyprime.mcedia_mtv_plugin.channel.worldui.WorldUiWatchRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MtvChannelProtocol {
    public static final String CHANNEL_SUBSCRIBE = "mcedia_mtv:channel_subscribe";
    public static final String CHANNEL_UNSUBSCRIBE = "mcedia_mtv:channel_unsubscribe";
    public static final String CHANNEL_SNAPSHOT = "mcedia_mtv:channel_snapshot";
    public static final String CHANNEL_SYNC = "mcedia_mtv:channel_sync";
    public static final String CHANNEL_REMOVE = "mcedia_mtv:channel_remove";
    public static final String CHANNEL_HEARTBEAT = "mcedia_mtv:channel_heartbeat";
    public static final String CHANNEL_HUD_BINDING = "mcedia_mtv:hud_binding";
    public static final String WORLD_UI_CAPABILITIES = "mcedia_mtv:world_ui_capabilities";
    public static final String CHANNEL_PLAYLIST_MANIFEST = "mcedia_mtv:channel_playlist_manifest";
    public static final String CHANNEL_PLAYLIST_PAGE_REQUEST = "mcedia_mtv:channel_playlist_page_request";
    public static final String CHANNEL_PLAYLIST_PAGE = "mcedia_mtv:channel_playlist_page";
    public static final String CHANNEL_CONTROL_REQUEST = "mcedia_mtv:channel_control_request";
    public static final String CHANNEL_CONTROL_RESULT = "mcedia_mtv:channel_control_result";
    public static final String CHANNEL_WORLD_UI_WATCH = "mcedia_mtv:channel_world_ui_watch";
    public static final String CHANNEL_WORLD_UI_UNWATCH = "mcedia_mtv:channel_world_ui_unwatch";
    public static final String WORLD_UI_CONTROL_STATE = "mcedia_mtv:world_ui_control_state";
    public static final int WORLD_UI_PROTOCOL_VERSION = 1;
    public static final int MAX_PLAYLIST_PAGE_ITEMS = 32;
    public static final int MAX_PLAYLIST_PAGE_BYTES = 24 * 1024;
    public static final int MAX_MEDIA_URL_LENGTH = 2_048;
    public static final int MAX_COLLECTION_URLS = 200;

    private static final int MAX_CHANNEL_ID_LENGTH = 256;
    private static final int MAX_SCREEN_ID_LENGTH = 128;
    private static final int MAX_PLAY_ORDER_MODE_LENGTH = 32;
    private static final int MAX_CONTROL_REQUEST_BYTES = 64 * 1024;
    private static final int MAX_CONTROL_RESULT_BYTES = 64;
    private static final int MAX_CONTROL_STATE_BYTES = 512;
    private static final Set<String> PLAY_ORDER_MODES = Set.of("SEQUENTIAL", "SHUFFLE", "LOOP_ALL", "LOOP_ONE", "CURRENT_ONLY");

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

    public static byte[] encodePlaylistManifest(WorldUiPlaylistManifest manifest) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writePlaylistManifest(buffer, manifest);
        return toBytes(buffer);
    }

    public static WorldUiPlaylistManifest decodePlaylistManifest(byte[] message) {
        return readPlaylistManifest(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
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

    public static byte[] encodePlaylistPageRequest(WorldUiPlaylistPageRequest request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writePlaylistPageRequest(buffer, request);
        return toBytes(buffer);
    }

    public static WorldUiPlaylistPageRequest decodePlaylistPageRequest(byte[] message) {
        return readPlaylistPageRequest(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writePlaylistPageRequest(FriendlyByteBuf buffer, WorldUiPlaylistPageRequest request) {
        validatePageRequest(request);
        buffer.writeUtf(request.channelId(), MAX_CHANNEL_ID_LENGTH);
        buffer.writeLong(request.knownRevision());
        buffer.writeVarInt(request.offset());
    }

    public static WorldUiPlaylistPageRequest readPlaylistPageRequest(FriendlyByteBuf buffer) {
        var request = new WorldUiPlaylistPageRequest(
                readNonBlankUtf(buffer, MAX_CHANNEL_ID_LENGTH, "channelId"),
                readNonNegativeLong(buffer, "knownRevision"),
                readNonNegativeInt(buffer, "offset")
        );
        validatePageRequest(request);
        ensureFullyRead(buffer, "playlist page request");
        return request;
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

    public static byte[] encodeWatchRequest(WorldUiWatchRequest request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeWatchRequest(buffer, request);
        return toBytes(buffer);
    }

    public static WorldUiWatchRequest decodeWatchRequest(byte[] message) {
        return readWatchRequest(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writeWatchRequest(FriendlyByteBuf buffer, WorldUiWatchRequest request) {
        if (request == null || request.targetMtvUuid() == null) {
            throw invalidPacket("world UI watch", "targetMtvUuid is missing");
        }
        buffer.writeUUID(request.targetMtvUuid());
    }

    public static WorldUiWatchRequest readWatchRequest(FriendlyByteBuf buffer) {
        var request = new WorldUiWatchRequest(buffer.readUUID());
        ensureFullyRead(buffer, "world UI watch");
        return request;
    }

    public static WorldUiControlRequest decodeControlRequest(byte[] message) {
        if (message == null || message.length > MAX_CONTROL_REQUEST_BYTES) {
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
        String screenId = buffer.readUtf(MAX_SCREEN_ID_LENGTH);
        String channelId = buffer.readUtf(MAX_CHANNEL_ID_LENGTH);
        long requestId = buffer.readLong();
        long expectedRevision = buffer.readLong();
        float hitU = buffer.readFloat();
        float hitV = buffer.readFloat();
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
        if (message == null || message.length > MAX_CONTROL_RESULT_BYTES) {
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
                buffer.readLong(),
                buffer.readBoolean(),
                readEnum(buffer, WorldUiControlError.values(), "control error"),
                buffer.readLong()
        );
        validateControlResult(result);
        ensureFullyRead(buffer, "control result");
        return result;
    }

    public static byte[] encodeWorldUiControlState(WorldUiControlState state) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeWorldUiControlState(buffer, state);
        byte[] encoded = toBytes(buffer);
        if (encoded.length > MAX_CONTROL_STATE_BYTES) {
            throw invalidPacket("world UI control state", "encoded length exceeds " + MAX_CONTROL_STATE_BYTES + " bytes");
        }
        return encoded;
    }

    public static WorldUiControlState decodeWorldUiControlState(byte[] message) {
        if (message == null || message.length > MAX_CONTROL_STATE_BYTES) {
            throw invalidPacket("world UI control state", "encoded length exceeds " + MAX_CONTROL_STATE_BYTES + " bytes");
        }
        return readWorldUiControlState(new FriendlyByteBuf(Unpooled.wrappedBuffer(message)));
    }

    public static void writeWorldUiControlState(FriendlyByteBuf buffer, WorldUiControlState state) {
        validateWorldUiControlState(state);
        buffer.writeUUID(state.mtvUuid());
        buffer.writeUtf(state.channelId(), MAX_CHANNEL_ID_LENGTH);
        buffer.writeFloat(state.masterVolume());
        buffer.writeBoolean(state.canControl());
        buffer.writeLong(state.channelRevision());
        buffer.writeUtf(state.screenId(), MAX_SCREEN_ID_LENGTH);
        buffer.writeVarInt(state.brightness());
        buffer.writeBoolean(state.danmakuVisible());
    }

    public static WorldUiControlState readWorldUiControlState(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_CONTROL_STATE_BYTES) {
            throw invalidPacket("world UI control state", "encoded length exceeds " + MAX_CONTROL_STATE_BYTES + " bytes");
        }
        var state = new WorldUiControlState(buffer.readUUID(), buffer.readUtf(MAX_CHANNEL_ID_LENGTH),
                buffer.readFloat(), buffer.readBoolean(), buffer.readLong(),
                buffer.readUtf(MAX_SCREEN_ID_LENGTH), readNonNegativeInt(buffer, "brightness"), buffer.readBoolean());
        validateWorldUiControlState(state);
        ensureFullyRead(buffer, "world UI control state");
        return state;
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

    public static byte[] encodeHudBinding(String channelId) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeHudBinding(buffer, channelId);
        return toBytes(buffer);
    }

    public static void writeHudBinding(FriendlyByteBuf buffer, String channelId) {
        buffer.writeUtf(channelId != null ? channelId : "");
    }

    public static String decodeHudBinding(byte[] message) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
        return readHudBinding(buffer);
    }

    public static String readHudBinding(FriendlyByteBuf buffer) {
        return buffer.readUtf();
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

    private static void validatePage(WorldUiPlaylistPage page) {
        if (page == null || page.channelId() == null || page.channelId().isBlank() || page.channelId().length() > MAX_CHANNEL_ID_LENGTH
                || page.revision() < 0L || page.itemCount() < 0 || page.cursor() < 0
                || (page.itemCount() == 0 && page.cursor() != 0)
                || (page.itemCount() > 0 && page.cursor() >= page.itemCount())
                || page.playOrderMode() == null || !PLAY_ORDER_MODES.contains(page.playOrderMode())
                || page.offset() < 0 || page.offset() > page.itemCount()
                || page.mediaUrls() == null || page.mediaUrls().size() > MAX_PLAYLIST_PAGE_ITEMS
                || page.offset() + page.mediaUrls().size() > page.itemCount()) {
            throw invalidPacket("playlist page", "field is invalid");
        }
        for (String mediaUrl : page.mediaUrls()) {
            if (mediaUrl == null || mediaUrl.isBlank() || mediaUrl.length() > MAX_MEDIA_URL_LENGTH) {
                throw invalidPacket("playlist page", "mediaUrl is invalid");
            }
        }
    }

    private static void validateManifest(WorldUiPlaylistManifest manifest) {
        if (manifest == null || manifest.channelId() == null || manifest.channelId().isBlank()
                || manifest.channelId().length() > MAX_CHANNEL_ID_LENGTH || manifest.revision() < 0L
                || manifest.itemCount() < 0 || manifest.cursor() < 0
                || (manifest.itemCount() == 0 && manifest.cursor() != 0)
                || (manifest.itemCount() > 0 && manifest.cursor() >= manifest.itemCount())
                || manifest.playOrderMode() == null || !PLAY_ORDER_MODES.contains(manifest.playOrderMode())) {
            throw invalidPacket("playlist manifest", "field is invalid");
        }
    }

    private static void validateCapabilities(WorldUiCapabilities capabilities) {
        if (capabilities == null || capabilities.protocolVersion() != WORLD_UI_PROTOCOL_VERSION
                || capabilities.maxPageItems() <= 0 || capabilities.maxPageItems() > MAX_PLAYLIST_PAGE_ITEMS
                || capabilities.featureFlags() < 0L) {
            throw invalidPacket("world UI capabilities", "field is invalid");
        }
    }

    private static void validatePageRequest(WorldUiPlaylistPageRequest request) {
        if (request == null || request.channelId() == null || request.channelId().isBlank()
                || request.channelId().length() > MAX_CHANNEL_ID_LENGTH || request.knownRevision() < 0L
                || request.offset() < 0 || request.offset() % MAX_PLAYLIST_PAGE_ITEMS != 0) {
            throw invalidPacket("playlist page request", "field is invalid");
        }
    }

    private static String readNonBlankUtf(FriendlyByteBuf buffer, int maxLength, String field) {
        String value = buffer.readUtf(maxLength);
        if (value.isBlank()) {
            throw invalidPacket("playlist page", field + " is blank");
        }
        return value;
    }

    private static void writeMediaUrlList(FriendlyByteBuf buffer, List<String> urls) {
        validateMediaUrlList(new WorldUiControlArgument.MediaUrlList(urls), WorldUiControlOperation.ADD_COLLECTION);
        buffer.writeVarInt(urls.size());
        for (String url : urls) {
            buffer.writeUtf(url, MAX_MEDIA_URL_LENGTH);
        }
    }

    private static List<String> readMediaUrlList(FriendlyByteBuf buffer) {
        int size = readNonNegativeInt(buffer, "collection url count");
        if (size == 0 || size > MAX_COLLECTION_URLS) {
            throw invalidPacket("control request", "collection url count is invalid");
        }
        var urls = new java.util.ArrayList<String>(size);
        for (int index = 0; index < size; index++) {
            urls.add(readNonBlankUtf(buffer, MAX_MEDIA_URL_LENGTH, "collection url"));
        }
        return urls;
    }

    private static void validateMediaUrlList(WorldUiControlArgument argument, WorldUiControlOperation operation) {
        var urls = requireArgument(argument, WorldUiControlArgument.MediaUrlList.class, operation).urls();
        if (urls == null || urls.isEmpty() || urls.size() > MAX_COLLECTION_URLS) {
            throw invalidPacket("control request", "collection url list is invalid");
        }
        for (String url : urls) {
            if (url == null || url.isBlank() || url.length() > MAX_MEDIA_URL_LENGTH) {
                throw invalidPacket("control request", "collection url is invalid");
            }
        }
    }

    private static String readPlayOrderMode(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_PLAY_ORDER_MODE_LENGTH);
        if (!PLAY_ORDER_MODES.contains(value)) {
            throw invalidPacket("playlist page", "playOrderMode is invalid");
        }
        return value;
    }

    private static long readNonNegativeLong(FriendlyByteBuf buffer, String field) {
        long value = buffer.readLong();
        if (value < 0L) {
            throw invalidPacket("playlist page", field + " is negative");
        }
        return value;
    }

    private static int readNonNegativeInt(FriendlyByteBuf buffer, String field) {
        int value = buffer.readVarInt();
        if (value < 0) {
            throw invalidPacket("playlist page", field + " is negative");
        }
        return value;
    }

    private static void validateControlRequest(WorldUiControlRequest request) {
        if (request == null || request.targetMtvUuid() == null || request.operation() == null || request.argument() == null
                || request.screenId() == null || request.screenId().isBlank() || request.screenId().length() > MAX_SCREEN_ID_LENGTH
                || request.channelId() == null || request.channelId().isBlank() || request.channelId().length() > MAX_CHANNEL_ID_LENGTH
                || request.requestId() < 0L || request.expectedRevision() < 0L
                || !validScreenUv(request.hitU()) || !validScreenUv(request.hitV())) {
            throw invalidPacket("control request", "field is invalid");
        }
        switch (request.operation()) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> requireArgument(request.argument(), WorldUiControlArgument.None.class, request.operation());
            case SEEK_ABSOLUTE -> {
                long value = requireArgument(request.argument(), WorldUiControlArgument.PositionUs.class, request.operation()).value();
                if (value < 0L) throw invalidPacket("control request", "absolute seek is negative");
            }
            case SEEK_RELATIVE -> requireArgument(request.argument(), WorldUiControlArgument.PositionUs.class, request.operation());
            case SET_SPEED -> validateScalar(request.argument(), request.operation(), 0.25F, 4.0F);
            case SET_MASTER_VOLUME -> validateScalar(request.argument(), request.operation(), 0.0F, 1.0F);
            case SET_BRIGHTNESS -> validateScalar(request.argument(), request.operation(), 0.0F, 15.0F);
            case SET_DANMAKU_VISIBLE -> requireArgument(request.argument(), WorldUiControlArgument.BooleanValue.class, request.operation());
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK, MOVE_UP, MOVE_DOWN -> {
                if (requireArgument(request.argument(), WorldUiControlArgument.PlaylistIndex.class, request.operation()).value() < 0) {
                    throw invalidPacket("control request", "playlist index is negative");
                }
            }
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> {
                String mediaUrl = requireArgument(request.argument(), WorldUiControlArgument.MediaUrl.class, request.operation()).value();
                if (mediaUrl == null || mediaUrl.isBlank() || mediaUrl.length() > MAX_MEDIA_URL_LENGTH) {
                    throw invalidPacket("control request", "mediaUrl is invalid");
                }
            }
            case ADD_COLLECTION -> validateMediaUrlList(request.argument(), request.operation());
            case SET_PLAY_ORDER -> {
                String mode = requireArgument(request.argument(), WorldUiControlArgument.PlayOrderMode.class, request.operation()).value();
                if (mode == null || !PLAY_ORDER_MODES.contains(mode)) {
                    throw invalidPacket("control request", "play order is invalid");
                }
            }
        }
    }

    private static void validateControlResult(WorldUiControlResult result) {
        if (result == null || result.requestId() < 0L || result.revision() < 0L || result.error() == null
                || result.accepted() != (result.error() == WorldUiControlError.NONE)) {
            throw invalidPacket("control result", "field is invalid");
        }
    }

    private static void validateWorldUiControlState(WorldUiControlState state) {
        if (state == null || state.mtvUuid() == null || state.channelId() == null || state.channelId().isBlank()
                || state.channelId().length() > MAX_CHANNEL_ID_LENGTH || !Float.isFinite(state.masterVolume())
                || state.masterVolume() < 0.0F || state.masterVolume() > 1.0F || state.channelRevision() < 0L
                || state.screenId() == null || state.screenId().isBlank() || state.screenId().length() > MAX_SCREEN_ID_LENGTH
                || state.brightness() < 0 || state.brightness() > 15) {
            throw invalidPacket("world UI control state", "field is invalid");
        }
    }

    private static void writeControlArgument(FriendlyByteBuf buffer, WorldUiControlOperation operation, WorldUiControlArgument argument) {
        switch (operation) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> { }
            case SEEK_ABSOLUTE, SEEK_RELATIVE -> buffer.writeLong(((WorldUiControlArgument.PositionUs) argument).value());
            case SET_SPEED, SET_MASTER_VOLUME, SET_BRIGHTNESS -> buffer.writeFloat(((WorldUiControlArgument.Scalar) argument).value());
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK, MOVE_UP, MOVE_DOWN -> buffer.writeVarInt(((WorldUiControlArgument.PlaylistIndex) argument).value());
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> buffer.writeUtf(((WorldUiControlArgument.MediaUrl) argument).value(), MAX_MEDIA_URL_LENGTH);
            case ADD_COLLECTION -> writeMediaUrlList(buffer, ((WorldUiControlArgument.MediaUrlList) argument).urls());
            case SET_PLAY_ORDER -> buffer.writeUtf(((WorldUiControlArgument.PlayOrderMode) argument).value(), MAX_PLAY_ORDER_MODE_LENGTH);
            case SET_DANMAKU_VISIBLE -> buffer.writeBoolean(((WorldUiControlArgument.BooleanValue) argument).value());
        }
    }

    private static WorldUiControlArgument readControlArgument(FriendlyByteBuf buffer, WorldUiControlOperation operation) {
        return switch (operation) {
            case TOGGLE_PAUSE, NEXT, PREVIOUS, CLEAR, TOGGLE_MUTE -> WorldUiControlArgument.None.INSTANCE;
            case SEEK_ABSOLUTE, SEEK_RELATIVE -> new WorldUiControlArgument.PositionUs(buffer.readLong());
            case SET_SPEED, SET_MASTER_VOLUME, SET_BRIGHTNESS -> new WorldUiControlArgument.Scalar(buffer.readFloat());
            case PLAY_INDEX, REMOVE, MOVE_FRONT, MOVE_BACK, MOVE_UP, MOVE_DOWN -> new WorldUiControlArgument.PlaylistIndex(readNonNegativeInt(buffer, "playlist index"));
            case PREPEND, APPEND, INSERT_NEXT, INSERT_AND_PLAY -> new WorldUiControlArgument.MediaUrl(readNonBlankUtf(buffer, MAX_MEDIA_URL_LENGTH, "mediaUrl"));
            case ADD_COLLECTION -> new WorldUiControlArgument.MediaUrlList(readMediaUrlList(buffer));
            case SET_PLAY_ORDER -> new WorldUiControlArgument.PlayOrderMode(readPlayOrderMode(buffer));
            case SET_DANMAKU_VISIBLE -> new WorldUiControlArgument.BooleanValue(buffer.readBoolean());
        };
    }

    private static void validateScalar(WorldUiControlArgument argument, WorldUiControlOperation operation, float minimum, float maximum) {
        float value = requireArgument(argument, WorldUiControlArgument.Scalar.class, operation).value();
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw invalidPacket("control request", "scalar is invalid");
        }
    }

    private static <T extends WorldUiControlArgument> T requireArgument(WorldUiControlArgument argument, Class<T> expectedType,
                                                                          WorldUiControlOperation operation) {
        if (!expectedType.isInstance(argument)) {
            throw invalidPacket("control request", "argument does not match " + operation);
        }
        return expectedType.cast(argument);
    }

    private static <E extends Enum<E>> E readEnum(FriendlyByteBuf buffer, E[] values, String field) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw invalidPacket("control request", field + " is invalid");
        }
        return values[ordinal];
    }

    private static boolean validScreenUv(float value) {
        return Float.isFinite(value) && value >= 0.0F && value <= 1.0F;
    }


    private static byte[] toBytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
