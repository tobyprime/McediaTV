package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

class MtvChannelProtocolWorldUiTest {
    @Test
    void manifestAndPageRequestRoundTrip() {
        var manifest = new WorldUiPlaylistManifest("channel", 4L, 65, 32, "LOOP_ALL");
        var request = new WorldUiPlaylistPageRequest("channel", 4L, 32);

        assertEquals(manifest, MtvChannelProtocol.decodePlaylistManifest(MtvChannelProtocol.encodePlaylistManifest(manifest)));
        assertEquals(request, MtvChannelProtocol.decodePlaylistPageRequest(MtvChannelProtocol.encodePlaylistPageRequest(request)));
    }

    @Test
    void pageRequestRejectsTrailingBytes() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        MtvChannelProtocol.writePlaylistPageRequest(buffer, new WorldUiPlaylistPageRequest("channel", 4L, 0));
        buffer.writeByte(1);

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.readPlaylistPageRequest(buffer));
    }

    @Test
    void capabilitiesRoundTrip() {
        var capabilities = new WorldUiCapabilities(1, 32, 7L);

        assertEquals(capabilities, MtvChannelProtocol.decodeCapabilities(MtvChannelProtocol.encodeCapabilities(capabilities)));
    }

    @Test
    void watchRequestRoundTripsTargetUuid() {
        var request = new WorldUiWatchRequest(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        assertEquals(request, MtvChannelProtocol.decodeWatchRequest(MtvChannelProtocol.encodeWatchRequest(request)));
    }

    @Test
    void controlRequestAndResultRoundTrip() {
        var request = new WorldUiControlRequest(UUID.randomUUID(), "screen_0", "channel", 4L, 8L,
                0.5F, 0.75F, WorldUiControlOperation.SET_MASTER_VOLUME, new WorldUiControlArgument.Scalar(0.4F));
        var result = WorldUiControlResult.rejected(4L, WorldUiControlError.STALE_REVISION, 9L);

        assertEquals(request, MtvChannelProtocol.decodeControlRequest(MtvChannelProtocol.encodeControlRequest(request)));
        assertEquals(result, MtvChannelProtocol.decodeControlResult(MtvChannelProtocol.encodeControlResult(result)));
    }

    @Test
    void controlRequestRejectsTrailingBytes() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        MtvChannelProtocol.writeControlRequest(buffer, new WorldUiControlRequest(UUID.randomUUID(), "screen_0", "channel", 4L, 8L,
                0.5F, 0.75F, WorldUiControlOperation.NEXT, WorldUiControlArgument.None.INSTANCE));
        buffer.writeByte(1);

        assertThrows(IllegalArgumentException.class, () -> MtvChannelProtocol.readControlRequest(buffer));
    }

    @Test
    void controlStateRoundTrips() {
        var state = new WorldUiControlState(UUID.randomUUID(), "channel", .35F, true, 19L, "screen_0", 8, true);
        assertEquals(state, MtvChannelProtocol.decodeWorldUiControlState(MtvChannelProtocol.encodeWorldUiControlState(state)));
    }
}
