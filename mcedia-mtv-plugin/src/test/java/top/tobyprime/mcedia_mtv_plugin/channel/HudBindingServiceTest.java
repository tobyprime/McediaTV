package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudBindingServiceTest {

    @Test
    void resendsPersistedBindingOnlyWhenClientRegistersHudChannel() {
        assertTrue(HudBindingService.isHudBindingChannel(MtvChannelProtocol.CHANNEL_HUD_BINDING));
        assertFalse(HudBindingService.isHudBindingChannel(MtvChannelProtocol.CHANNEL_SNAPSHOT));
        assertFalse(HudBindingService.isHudBindingChannel(MtvChannelProtocol.CHANNEL_SUBSCRIBE));
    }
}
