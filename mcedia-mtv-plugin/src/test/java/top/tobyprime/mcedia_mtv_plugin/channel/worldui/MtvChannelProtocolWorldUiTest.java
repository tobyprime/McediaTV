package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
