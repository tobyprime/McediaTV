package top.tobyprime.mcedia_mtv.client;

import net.fabricmc.api.ClientModInitializer;
import top.tobyprime.mcedia_core.client.entity.ClientEntityManager;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.MtvClientChannelPayloads;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

public class EntityPlayerClientEntryPoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientEntityManager.init();
        MtvClientChannelPayloads.register();
        ClientChannelPlaybackManager.getInstance().clear();
        EntityPlayerManager.getInstance().onInitialize();
    }
}
