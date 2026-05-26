package top.tobyprime.mcedia_mtv_plugin.channel;

public enum MtvChannelType {
    BROADCAST,
    SELF;

    public boolean isBroadcast() {
        return this == BROADCAST;
    }

    public boolean isSelf() {
        return this == SELF;
    }
}
