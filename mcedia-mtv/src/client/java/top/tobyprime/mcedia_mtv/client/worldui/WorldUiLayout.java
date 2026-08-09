package top.tobyprime.mcedia_mtv.client.worldui;

/** Normalized coordinates for the controls painted onto an MTV screen. */
public final class WorldUiLayout {
    private static final float TOGGLE_TRIGGER_START = 0.90F;
    private static final float TOGGLE_BUTTON_START = 0.93F;
    private static final float BRIGHTNESS_BAR_LEFT = 0.035F;
    private static final float BRIGHTNESS_BAR_RIGHT = 0.065F;
    private static final float VOLUME_BAR_LEFT = 0.075F;
    private static final float VOLUME_BAR_RIGHT = 0.105F;
    private static final float VERTICAL_BAR_TOP = 0.30F;
    private static final float VERTICAL_BAR_BOTTOM = 0.58F;
    private static final float BAR_DRAG_TOP = 0.28F;
    private static final float BAR_DRAG_BOTTOM = 0.58F;
    private static final float DANMAKU_BUTTON_LEFT = 0.035F;
    private static final float DANMAKU_BUTTON_RIGHT = 0.105F;
    private static final float DANMAKU_BUTTON_TOP = 0.60F;
    private static final float DANMAKU_BUTTON_BOTTOM = 0.68F;
    private static final float TRANSPORT_TOP = 0.82F;
    private static final float TRANSPORT_BOTTOM = 0.90F;
    private static final float PROGRESS_TOP = 0.92F;
    private static final float PROGRESS_BOTTOM = 0.97F;
    private static final float SEEK_TRACK_START = 0.16F;
    private static final float SEEK_TRACK_END = 0.96F;
    private static final float MIN_DETAIL_WIDTH = 0.80F;
    private static final float MIN_DETAIL_HEIGHT = 0.45F;
    private static final float MIN_DETAIL_ASPECT = 0.75F;
    private static final float MAX_DETAIL_ASPECT = 3.00F;

    public static boolean showsDetails(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < MIN_DETAIL_WIDTH || height < MIN_DETAIL_HEIGHT) {
            return false;
        }
        float aspect = width / height;
        return aspect >= MIN_DETAIL_ASPECT && aspect <= MAX_DETAIL_ASPECT;
    }

    /** The outer hover area that makes the collapsed button visible. */
    public boolean isToggleTrigger(float u, float v) {
        return inScreen(u, v) && u >= TOGGLE_TRIGGER_START && v >= TOGGLE_TRIGGER_START;
    }

    /** Maps a pointer U on the progress track to a normalized 0..1 value. */
    public static float seekFraction(float u) {
        return fraction(u, SEEK_TRACK_START, SEEK_TRACK_END);
    }

    /** Maps a pointer V on a vertical bar to a normalized 0..1 value (bottom = 0, top = 1). */
    private static float verticalValue(float v, float top, float bottom) {
        return 1.0F - fraction(v, top, bottom);
    }

    /** Vertical volume bar: bottom edge maps to 0, top edge to 1. */
    public static float volumeFraction(float v) {
        return verticalValue(v, VERTICAL_BAR_TOP, VERTICAL_BAR_BOTTOM);
    }

    /** Vertical brightness bar shares the same geometry as the volume bar. */
    public static float brightnessFraction(float v) {
        return volumeFraction(v);
    }

    private static float fraction(float u, float start, float end) {
        float span = end - start;
        if (!Float.isFinite(u) || span <= 0.0F) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, (u - start) / span));
    }

    public WorldUiHit hit(float u, float v, boolean expanded) {
        return hit(u, v, expanded, false);
    }

    public WorldUiHit hit(float u, float v, boolean expanded, boolean playlistExpanded) {
        if (!inScreen(u, v)) {
            return WorldUiHit.NONE;
        }
        if (!expanded) {
            return u >= TOGGLE_BUTTON_START && v >= TOGGLE_BUTTON_START
                    ? new WorldUiHit(WorldUiHit.Kind.TOGGLE) : WorldUiHit.NONE;
        }
        // Expanded: no corner toggle button; the collapse control is the transport row's last button.
        if (playlistExpanded && u >= 0.70F && u <= 0.97F && v >= 0.10F && v <= 0.61F) {
            int row = Math.max(0, (int) ((v - 0.10F) / 0.075F));
            float rowTop = 0.10F + row * 0.075F;
            boolean upper = v <= rowTop + 0.028F;
            if (u >= 0.945F && u <= 0.97F) return new WorldUiHit(WorldUiHit.Kind.REMOVE_ITEM, row);
            if (u >= 0.92F && u <= 0.945F) {
                return new WorldUiHit(upper ? WorldUiHit.Kind.MOVE_FRONT : WorldUiHit.Kind.MOVE_BACK, row);
            }
            if (u >= 0.895F && u <= 0.92F) {
                return new WorldUiHit(upper ? WorldUiHit.Kind.MOVE_UP : WorldUiHit.Kind.MOVE_DOWN, row);
            }
            return new WorldUiHit(WorldUiHit.Kind.PLAYLIST_ITEM, row);
        }
        if (playlistExpanded && v >= 0.05F && v <= 0.09F) {
            if (u >= 0.70F && u <= 0.75F) return new WorldUiHit(WorldUiHit.Kind.SET_PLAY_ORDER);
            if (u >= 0.755F && u <= 0.805F) return new WorldUiHit(WorldUiHit.Kind.PLAYLIST_PREVIOUS_PAGE);
            if (u >= 0.81F && u <= 0.86F) return new WorldUiHit(WorldUiHit.Kind.PLAYLIST_NEXT_PAGE);
            if (u >= 0.865F && u <= 0.915F) return new WorldUiHit(WorldUiHit.Kind.ADD_MEDIA);
            if (u >= 0.92F && u <= 0.97F) return new WorldUiHit(WorldUiHit.Kind.CLEAR_PLAYLIST);
        }
        if (v >= BAR_DRAG_TOP && v <= BAR_DRAG_BOTTOM) {
            if (u >= BRIGHTNESS_BAR_LEFT && u <= BRIGHTNESS_BAR_RIGHT) {
                return new WorldUiHit(WorldUiHit.Kind.BRIGHTNESS);
            }
            if (u >= VOLUME_BAR_LEFT && u <= VOLUME_BAR_RIGHT) {
                return new WorldUiHit(WorldUiHit.Kind.VOLUME);
            }
        }
        if (u >= DANMAKU_BUTTON_LEFT && u <= DANMAKU_BUTTON_RIGHT
                && v >= DANMAKU_BUTTON_TOP && v <= DANMAKU_BUTTON_BOTTOM) {
            return new WorldUiHit(WorldUiHit.Kind.DANMAKU);
        }
        if (v >= PROGRESS_TOP && v <= PROGRESS_BOTTOM) {
            if (u >= SEEK_TRACK_START && u <= SEEK_TRACK_END) {
                return new WorldUiHit(WorldUiHit.Kind.SEEK);
            }
        }
        if (v >= TRANSPORT_TOP && v <= TRANSPORT_BOTTOM) {
            if (u >= 0.16F && u <= 0.24F) return new WorldUiHit(WorldUiHit.Kind.SPEED);
            if (u >= 0.26F && u <= 0.34F) return new WorldUiHit(WorldUiHit.Kind.PREVIOUS);
            if (u >= 0.36F && u <= 0.44F) return new WorldUiHit(WorldUiHit.Kind.TOGGLE_PAUSE);
            if (u >= 0.46F && u <= 0.54F) return new WorldUiHit(WorldUiHit.Kind.NEXT);
            if (u >= 0.56F && u <= 0.64F) return new WorldUiHit(WorldUiHit.Kind.QUEUE);
            if (u >= 0.66F && u <= 0.74F) return new WorldUiHit(WorldUiHit.Kind.MUTE);
            if (u >= 0.76F && u <= 0.84F) return new WorldUiHit(WorldUiHit.Kind.TOGGLE);
        }
        return WorldUiHit.NONE;
    }

    private static boolean inScreen(float u, float v) {
        return Float.isFinite(u) && Float.isFinite(v) && u >= 0.0F && u <= 1.0F && v >= 0.0F && v <= 1.0F;
    }
}
