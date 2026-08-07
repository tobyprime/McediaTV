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
        PLAYLIST_ITEM,
        REMOVE_ITEM,
        MOVE_FRONT,
        MOVE_BACK,
        CLEAR_PLAYLIST,
        SET_PLAY_ORDER
    }

    public static final WorldUiHit NONE = new WorldUiHit(Kind.NONE, -1);

    public WorldUiHit(Kind kind) {
        this(kind, -1);
    }

    public boolean isToggle() {
        return kind == Kind.TOGGLE;
    }
}
