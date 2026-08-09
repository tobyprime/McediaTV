package top.tobyprime.mcedia_mtv.client.worldui;

public record WorldUiHit(Kind kind, int index) {
    public enum Kind {
        NONE,
        TOGGLE,
        SEEK,
        VOLUME,
        SPEED,
        MUTE,
        TOGGLE_PAUSE,
        NEXT,
        PREVIOUS,
        ADD_MEDIA,
        QUEUE,
        BRIGHTNESS,
        DANMAKU,
        PLAYLIST_ITEM,
        REMOVE_ITEM,
        MOVE_FRONT,
        MOVE_BACK,
        MOVE_UP,
        MOVE_DOWN,
        CLEAR_PLAYLIST,
        SET_PLAY_ORDER,
        PLAYLIST_PREVIOUS_PAGE,
        PLAYLIST_NEXT_PAGE
    }

    public static final WorldUiHit NONE = new WorldUiHit(Kind.NONE, -1);

    public WorldUiHit(Kind kind) {
        this(kind, -1);
    }

    public boolean isToggle() {
        return kind == Kind.TOGGLE;
    }
}
