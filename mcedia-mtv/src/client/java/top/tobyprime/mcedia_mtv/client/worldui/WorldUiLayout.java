package top.tobyprime.mcedia_mtv.client.worldui;

/** Normalized coordinates for the controls painted onto an MTV screen. */
public final class WorldUiLayout {
    private static final float TOGGLE_TRIGGER_START = 0.90F;
    private static final float TOGGLE_BUTTON_START = 0.93F;

    public WorldUiHit hit(float u, float v, boolean expanded) {
        return hit(u, v, expanded, false);
    }

    public WorldUiHit hit(float u, float v, boolean expanded, boolean playlistExpanded) {
        if (!inScreen(u, v)) {
            return WorldUiHit.NONE;
        }
        if (!expanded) {
            return u >= TOGGLE_TRIGGER_START && v >= TOGGLE_TRIGGER_START
                    ? new WorldUiHit(WorldUiHit.Kind.TOGGLE) : WorldUiHit.NONE;
        }
        if (u >= TOGGLE_BUTTON_START && v >= TOGGLE_BUTTON_START) {
            return new WorldUiHit(WorldUiHit.Kind.TOGGLE);
        }
        if (playlistExpanded && u >= 0.70F && u <= 0.98F && v >= 0.10F && v <= 0.62F) {
            int row = Math.max(0, (int) ((v - 0.10F) / 0.08F));
            if (u >= 0.92F) return new WorldUiHit(WorldUiHit.Kind.REMOVE_ITEM, row);
            if (u >= 0.86F) return new WorldUiHit(WorldUiHit.Kind.MOVE_BACK, row);
            if (u >= 0.80F) return new WorldUiHit(WorldUiHit.Kind.MOVE_FRONT, row);
            return new WorldUiHit(WorldUiHit.Kind.PLAYLIST_ITEM, row);
        }
        if (playlistExpanded && v >= 0.04F && v <= 0.09F) {
            if (u >= 0.70F && u <= 0.82F) return new WorldUiHit(WorldUiHit.Kind.SET_PLAY_ORDER);
            if (u >= 0.88F && u <= 0.98F) return new WorldUiHit(WorldUiHit.Kind.CLEAR_PLAYLIST);
        }
        if (u >= 0.76F && u <= 0.82F && v >= 0.68F && v <= 0.80F) {
            return new WorldUiHit(WorldUiHit.Kind.QUEUE);
        }
        if (u >= 0.84F && u <= 0.90F && v >= 0.68F && v <= 0.80F) {
            return new WorldUiHit(WorldUiHit.Kind.ADD_MEDIA);
        }
        if (v >= 0.84F && v <= 0.92F) {
            if (u <= 0.80F) {
                return new WorldUiHit(WorldUiHit.Kind.SEEK);
            }
            if (u >= 0.84F) {
                return new WorldUiHit(WorldUiHit.Kind.VOLUME);
            }
        }
        if (v >= 0.68F && v <= 0.80F) {
            if (u >= 0.20F && u <= 0.28F) return new WorldUiHit(WorldUiHit.Kind.SPEED);
            if (u >= 0.90F && u <= 0.96F) return new WorldUiHit(WorldUiHit.Kind.MUTE);
            if (u >= 0.44F && u <= 0.56F) {
                return new WorldUiHit(WorldUiHit.Kind.TOGGLE_PAUSE);
            }
            if (u >= 0.58F && u <= 0.70F) {
                return new WorldUiHit(WorldUiHit.Kind.NEXT);
            }
            if (u >= 0.30F && u <= 0.42F) {
                return new WorldUiHit(WorldUiHit.Kind.PREVIOUS);
            }
            if (u >= 0.76F && u <= 0.82F) {
                return new WorldUiHit(WorldUiHit.Kind.ADD_MEDIA);
            }
        }
        return WorldUiHit.NONE;
    }

    private static boolean inScreen(float u, float v) {
        return Float.isFinite(u) && Float.isFinite(v) && u >= 0.0F && u <= 1.0F && v >= 0.0F && v <= 1.0F;
    }
}
