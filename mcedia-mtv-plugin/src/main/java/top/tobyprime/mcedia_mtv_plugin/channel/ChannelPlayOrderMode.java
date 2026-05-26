package top.tobyprime.mcedia_mtv_plugin.channel;

public enum ChannelPlayOrderMode {
    SEQUENTIAL,
    SHUFFLE,
    LOOP_ALL,
    LOOP_ONE,
    CURRENT_ONLY;

    public ChannelPlayOrderMode next() {
        return switch (this) {
            case SEQUENTIAL -> SHUFFLE;
            case SHUFFLE -> LOOP_ALL;
            case LOOP_ALL -> LOOP_ONE;
            case LOOP_ONE -> CURRENT_ONLY;
            case CURRENT_ONLY -> SEQUENTIAL;
        };
    }
}
