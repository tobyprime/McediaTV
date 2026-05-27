package top.tobyprime.mcedia_mtv_plugin.channel;

import java.util.Collection;

public interface ChannelRepository {
    ChannelRuntimeState load(String channelId);

    void save(ChannelRuntimeState state);

    void delete(String channelId);

    Collection<ChannelRuntimeState> list();
}
