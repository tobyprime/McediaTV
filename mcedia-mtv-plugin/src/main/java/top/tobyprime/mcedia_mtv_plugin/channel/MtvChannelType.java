package top.tobyprime.mcedia_mtv_plugin.channel;

public enum MtvChannelType {
    BROADCAST,
    SELF,
    STANDALONE;

    public boolean isBroadcast() {
        return this == BROADCAST;
    }

    public boolean isSelf() {
        return this == SELF;
    }

    public boolean isStandalone() {
        return this == STANDALONE || this == SELF;
    }
}
