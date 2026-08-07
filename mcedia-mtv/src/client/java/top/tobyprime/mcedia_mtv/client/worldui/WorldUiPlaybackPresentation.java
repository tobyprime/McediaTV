package top.tobyprime.mcedia_mtv.client.worldui;

/** Small mapping-independent strings for the MPV-style world controls. */
public final class WorldUiPlaybackPresentation {
    private WorldUiPlaybackPresentation() {
    }

    public static String timeLabel(long positionUs, long durationUs) {
        String duration = durationUs == 0L ? "--:--" : format(durationUs);
        return format(positionUs) + " / " + duration;
    }

    private static String format(long timeUs) {
        long seconds = Math.max(0L, timeUs) / 1_000_000L;
        long minutes = seconds / 60L;
        return minutes + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60L);
    }
}
