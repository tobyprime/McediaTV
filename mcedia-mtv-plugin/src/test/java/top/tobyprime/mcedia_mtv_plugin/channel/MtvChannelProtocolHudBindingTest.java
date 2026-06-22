package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MtvChannelProtocolHudBindingTest {

    @Test
    void encodeDecodeHudBinding() {
        String channelId = "vctest";
        byte[] encoded = MtvChannelProtocol.encodeHudBinding(channelId);
        String decoded = MtvChannelProtocol.decodeHudBinding(encoded);
        assertEquals(channelId, decoded);
    }

    @Test
    void encodeDecodeHudBindingEmpty() {
        byte[] encoded = MtvChannelProtocol.encodeHudBinding("");
        String decoded = MtvChannelProtocol.decodeHudBinding(encoded);
        assertEquals("", decoded);
    }

    @Test
    void encodeDecodeHudBindingNull() {
        byte[] encoded = MtvChannelProtocol.encodeHudBinding(null);
        String decoded = MtvChannelProtocol.decodeHudBinding(encoded);
        assertEquals("", decoded);
    }

    @Test
    void encodeHudBindingConformsToProtocolPattern() {
        var snapshot = new ChannelSnapshot("ch", 1L, "url", 1.0F, 0L, 0L, "PLAYING", false, 0L, false, false);
        byte[] snapshotBytes = MtvChannelProtocol.encodeSnapshot(snapshot);
        byte[] hudBytes = MtvChannelProtocol.encodeHudBinding("test");

        assertTrue(snapshotBytes.length > 0);
        assertTrue(hudBytes.length > 0);
        assertNotNull(MtvChannelProtocol.decodeHudBinding(hudBytes));
    }

    @Test
    void channeLConstantsAreDistinct() {
        // Ensure the new constant doesn't collide with existing ones
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_SUBSCRIBE);
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_SNAPSHOT);
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_REMOVE);
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_HEARTBEAT);
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_SYNC);
        assertNotEquals(MtvChannelProtocol.CHANNEL_HUD_BINDING, MtvChannelProtocol.CHANNEL_UNSUBSCRIBE);
    }
}
