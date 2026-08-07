package top.tobyprime.mcedia_mtv.client.worldui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldUiPlaybackPresentationTest {
    @Test
    void formatsPlaybackTimeAndKnownDuration() {
        assertEquals("1:05 / 2:00", WorldUiPlaybackPresentation.timeLabel(65_000_000L, 120_000_000L));
    }

    @Test
    void formatsUnknownDurationWithoutInventingAValue() {
        assertEquals("0:03 / --:--", WorldUiPlaybackPresentation.timeLabel(3_000_000L, 0L));
    }

    @Test
    void clampsNegativeTimeToZero() {
        assertEquals("0:00 / 0:00", WorldUiPlaybackPresentation.timeLabel(-1L, -1L));
    }
}
