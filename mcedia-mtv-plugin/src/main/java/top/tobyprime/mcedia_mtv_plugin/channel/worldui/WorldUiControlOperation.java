package top.tobyprime.mcedia_mtv_plugin.channel.worldui;

/** Wire order is shared with the Fabric client. Do not reorder members. */
public enum WorldUiControlOperation {
    TOGGLE_PAUSE,
    SEEK_ABSOLUTE,
    SEEK_RELATIVE,
    SET_SPEED,
    PLAY_INDEX,
    NEXT,
    PREVIOUS,
    PREPEND,
    APPEND,
    INSERT_NEXT,
    INSERT_AND_PLAY,
    REMOVE,
    MOVE_FRONT,
    MOVE_BACK,
    CLEAR,
    SET_PLAY_ORDER,
    SET_MASTER_VOLUME,
    TOGGLE_MUTE,
    SET_BRIGHTNESS,
    SET_DANMAKU_VISIBLE,
    MOVE_UP,
    MOVE_DOWN;

    public boolean changesChannelRevision() {
        return this != SET_MASTER_VOLUME && this != TOGGLE_MUTE
                && this != SET_BRIGHTNESS && this != SET_DANMAKU_VISIBLE;
    }
}
