package top.tobyprime.mcedia_mtv.client.channel.worldui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv.client.channel.MtvChannelProtocol;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldUiProtocolTest {
    @Test
    void playlistManifestRoundTripsAllFields() {
        var manifest = new WorldUiPlaylistManifest("channel:alpha", 17L, 96, 8, "LOOP_ALL");

        byte[] encoded = MtvChannelProtocol.encodePlaylistManifest(manifest);

        assertEquals(manifest, MtvChannelProtocol.decodePlaylistManifest(encoded));
    }

    @Test
    void playlistPageRoundTripsWithinTheLimits() {
        var page = new WorldUiPlaylistPage(
                "channel:alpha", 17L, 96, 8, "LOOP_ALL", 32,
                List.of("https://example.test/one", "https://example.test/two")
        );

        byte[] encoded = MtvChannelProtocol.encodePlaylistPage(page);

        assertEquals(page, MtvChannelProtocol.decodePlaylistPage(encoded));
    }

    @Test
    void playlistPageRejectsMoreThanThirtyTwoEntries() {
        var page = new WorldUiPlaylistPage(
                "channel", 1L, 33, 0, "SEQUENTIAL", 0,
                java.util.Collections.nCopies(33, "https://example.test/media")
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodePlaylistPage(page));
    }

    @Test
    void playlistPageRejectsUrlsLongerThanTwoThousandFortyEightCharacters() {
        var page = new WorldUiPlaylistPage(
                "channel", 1L, 1, 0, "SEQUENTIAL", 0,
                List.of("https://example.test/" + "x".repeat(2_048))
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodePlaylistPage(page));
    }

    @Test
    void playlistPageRejectsEncodedContentLargerThanTwentyFourKiB() {
        var page = new WorldUiPlaylistPage(
                "channel", 1L, 13, 0, "SEQUENTIAL", 0,
                java.util.Collections.nCopies(13, "https://example.test/" + "x".repeat(2_000))
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodePlaylistPage(page));
    }

    @Test
    void playlistManifestRejectsTrailingBytes() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        MtvChannelProtocol.writePlaylistManifest(buffer, new WorldUiPlaylistManifest("channel", 1L, 0, 0, "SEQUENTIAL"));
        buffer.writeByte(42);

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.readPlaylistManifest(buffer));
    }

    @Test
    void controlRequestRoundTripsItsTypedArgument() {
        var request = new WorldUiControlRequest(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "screen-main", "channel:alpha", 21L, 17L, 0.95F, 0.90F,
                WorldUiControlOperation.SEEK_ABSOLUTE,
                new WorldUiControlArgument.PositionUs(9_000_000L)
        );

        byte[] encoded = MtvChannelProtocol.encodeControlRequest(request);

        assertEquals(request, MtvChannelProtocol.decodeControlRequest(encoded));
    }

    @Test
    void moveUpAndMoveDownControlRequestsRoundTripTheirPlaylistIndex() {
        for (WorldUiControlOperation operation : List.of(WorldUiControlOperation.MOVE_UP, WorldUiControlOperation.MOVE_DOWN)) {
            var request = new WorldUiControlRequest(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    "screen-main", "channel:alpha", 22L, 17L, 0.90F, 0.15F,
                    operation, new WorldUiControlArgument.PlaylistIndex(3)
            );

            assertEquals(request, MtvChannelProtocol.decodeControlRequest(MtvChannelProtocol.encodeControlRequest(request)));
        }
    }

    @Test
    void controlRequestRejectsAParameterThatDoesNotBelongToItsOperation() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen-main", "channel", 1L, 1L, 0.5F, 0.5F,
                WorldUiControlOperation.TOGGLE_PAUSE,
                new WorldUiControlArgument.PositionUs(1L)
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodeControlRequest(request));
    }

    @Test
    void controlRequestRejectsOutOfRangeScreenUv() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen-main", "channel", 1L, 1L, 1.001F, 0.5F,
                WorldUiControlOperation.NEXT,
                WorldUiControlArgument.None.INSTANCE
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodeControlRequest(request));
    }

    @Test
    void controlResultRoundTripsTheCurrentRevisionAndStableError() {
        var result = new WorldUiControlResult(21L, false, WorldUiControlError.STALE_REVISION, 18L);

        byte[] encoded = MtvChannelProtocol.encodeControlResult(result);

        assertEquals(result, MtvChannelProtocol.decodeControlResult(encoded));
    }

    @Test
    void capabilitiesRoundTrip() {
        var capabilities = new WorldUiCapabilities(1, 32, 7L);

        assertEquals(capabilities, MtvChannelProtocol.decodeCapabilities(MtvChannelProtocol.encodeCapabilities(capabilities)));
    }

    @Test
    void playlistPageRequestRoundTrips() {
        var request = new WorldUiPlaylistPageRequest("channel", 17L, 32);

        assertEquals(request, MtvChannelProtocol.decodePlaylistPageRequest(MtvChannelProtocol.encodePlaylistPageRequest(request)));
    }

    @Test
    void watchRequestRoundTripsTargetUuid() {
        var request = new WorldUiWatchRequest(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        assertEquals(request, MtvChannelProtocol.decodeWatchRequest(MtvChannelProtocol.encodeWatchRequest(request)));
    }

    @Test
    void controlStateRoundTripsAndRejectsTrailingBytes() {
        var state = new WorldUiControlState(UUID.randomUUID(), "channel", .35F, true, 19L, "screen_0", 8, true);
        assertEquals(state, MtvChannelProtocol.decodeWorldUiControlState(MtvChannelProtocol.encodeWorldUiControlState(state)));

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        MtvChannelProtocol.writeWorldUiControlState(buffer, state);
        buffer.writeByte(1);
        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.readWorldUiControlState(buffer));
    }

    @Test
    void brightnessRequestRoundTripsItsScalarArgument() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen_0", "channel", 1L, 1L, 0.5F, 0.5F,
                WorldUiControlOperation.SET_BRIGHTNESS,
                new WorldUiControlArgument.Scalar(12.0F)
        );

        assertEquals(request, MtvChannelProtocol.decodeControlRequest(MtvChannelProtocol.encodeControlRequest(request)));
    }

    @Test
    void danmakuRequestRoundTripsItsBooleanArgument() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen_0", "channel", 1L, 1L, 0.5F, 0.5F,
                WorldUiControlOperation.SET_DANMAKU_VISIBLE,
                new WorldUiControlArgument.BooleanValue(false)
        );

        assertEquals(request, MtvChannelProtocol.decodeControlRequest(MtvChannelProtocol.encodeControlRequest(request)));
    }

    @Test
    void addCollectionRoundTripsItsUrlListArgument() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen_0", "channel", 1L, 1L, 0.5F, 0.5F,
                WorldUiControlOperation.ADD_COLLECTION,
                new WorldUiControlArgument.MediaUrlList(List.of(
                        "https://www.bilibili.com/bangumi/play/ep123",
                        "https://www.bilibili.com/video/BV1pRVF6kEPh"))
        );

        assertEquals(request, MtvChannelProtocol.decodeControlRequest(MtvChannelProtocol.encodeControlRequest(request)));
    }

    @Test
    void addCollectionRejectsEmptyList() {
        var request = new WorldUiControlRequest(
                UUID.randomUUID(), "screen_0", "channel", 1L, 1L, 0.5F, 0.5F,
                WorldUiControlOperation.ADD_COLLECTION,
                new WorldUiControlArgument.MediaUrlList(List.of())
        );

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.encodeControlRequest(request));
    }
}
