package top.tobyprime.mcedia_mtv_plugin.channel;

import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.Collection;

public interface ChannelRepository {
    ChannelRuntimeState load(String channelId);

    ChannelRuntimeState load(MtvChannelBinding binding, ManagedMtvPlayer player);

    void save(ChannelRuntimeState state);

    void delete(String channelId);

    Collection<ChannelRuntimeState> list();
}
