package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MtvChannelTypeTest {

    @Test
    void isBroadcast() {
        assertTrue(MtvChannelType.BROADCAST.isBroadcast());
        assertFalse(MtvChannelType.SELF.isBroadcast());
    }

    @Test
    void isSelf() {
        assertTrue(MtvChannelType.SELF.isSelf());
        assertFalse(MtvChannelType.BROADCAST.isSelf());
    }
}
