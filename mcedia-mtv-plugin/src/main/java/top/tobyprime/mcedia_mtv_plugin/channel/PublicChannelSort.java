package top.tobyprime.mcedia_mtv_plugin.channel;

/**
 * Sort modes for public channel search results.
 */
public enum PublicChannelSort {
    RELEVANCE,
    CREATED_NEWEST,
    CREATED_OLDEST,
    MOST_VIEWERS,
    NAME_A_Z;

    public PublicChannelSort next() {
        return switch (this) {
            case RELEVANCE -> CREATED_NEWEST;
            case CREATED_NEWEST -> CREATED_OLDEST;
            case CREATED_OLDEST -> MOST_VIEWERS;
            case MOST_VIEWERS -> NAME_A_Z;
            case NAME_A_Z -> RELEVANCE;
        };
    }

    public String displayName() {
        return switch (this) {
            case RELEVANCE -> "综合排序";
            case CREATED_NEWEST -> "最新创建";
            case CREATED_OLDEST -> "最早创建";
            case MOST_VIEWERS -> "最多观众";
            case NAME_A_Z -> "名称 A-Z";
        };
    }
}