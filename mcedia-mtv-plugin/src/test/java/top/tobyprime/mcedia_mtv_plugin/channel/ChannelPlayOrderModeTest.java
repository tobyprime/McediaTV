package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelPlayOrderModeTest {

    @Test
    void nextCyclesThroughAllModes() {
        assertEquals(ChannelPlayOrderMode.SHUFFLE, ChannelPlayOrderMode.SEQUENTIAL.next());
        assertEquals(ChannelPlayOrderMode.LOOP_ALL, ChannelPlayOrderMode.SHUFFLE.next());
        assertEquals(ChannelPlayOrderMode.LOOP_ONE, ChannelPlayOrderMode.LOOP_ALL.next());
        assertEquals(ChannelPlayOrderMode.CURRENT_ONLY, ChannelPlayOrderMode.LOOP_ONE.next());
        assertEquals(ChannelPlayOrderMode.SEQUENTIAL, ChannelPlayOrderMode.CURRENT_ONLY.next());
    }
}

