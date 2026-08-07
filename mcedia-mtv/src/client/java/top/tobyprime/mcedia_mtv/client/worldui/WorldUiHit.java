package top.tobyprime.mcedia_mtv.client.worldui;

public record WorldUiHit(Kind kind) {
    public enum Kind {
        NONE,
        TOGGLE,
        SEEK,
        VOLUME,
        TOGGLE_PAUSE,
        NEXT,
        PREVIOUS,
        ADD_MEDIA
    }

    public static final WorldUiHit NONE = new WorldUiHit(Kind.NONE);

    public boolean isToggle() {
        return kind == Kind.TOGGLE;
    }
}
