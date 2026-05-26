package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.UUID;

public record MtvChannelBinding(MtvChannelType type, String channelId, String regionKey) {
    public static MtvChannelBinding broadcast(String channelId) {
        return new MtvChannelBinding(MtvChannelType.BROADCAST, channelId, null);
    }

    public static MtvChannelBinding self() {
        return new MtvChannelBinding(MtvChannelType.SELF, "", null);
    }

    public static MtvChannelBinding self(UUID entityUuid) {
        return new MtvChannelBinding(MtvChannelType.SELF, runtimeSelfChannelId(entityUuid), null);
    }

    public static String runtimeSelfChannelId(UUID entityUuid) {
        return "self:" + entityUuid;
    }

    public MtvChannelBinding resolveRuntime(UUID entityUuid) {
        if (type == MtvChannelType.SELF) {
            return self(entityUuid);
        }
        return this;
    }

    public boolean isBroadcast() {
        return type == MtvChannelType.BROADCAST;
    }

    public boolean isSelf() {
        return type == MtvChannelType.SELF;
    }
}
