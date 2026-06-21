package top.tobyprime.mcedia_mtv.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.MtvClientChannelPayloads;
import top.tobyprime.mcedia_mtv.client.command.MtvHudCommand;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

public class EntityPlayerClientEntryPoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MtvClientChannelPayloads.register();
        ClientChannelPlaybackManager.getInstance().clear();
        MtvHudCommand.register();
        EntityPlayerManager.getInstance().onInitialize();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                HudChannelPlayer.getInstance().cleanup());
    }
}
