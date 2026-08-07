package top.tobyprime.mcedia_mtv.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.MtvClientChannelPayloads;
import top.tobyprime.mcedia_mtv.client.command.MtvHudCommand;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;
import top.tobyprime.mcedia_mtv.client.worldui.MtvWorldUiRenderer;
import top.tobyprime.mcedia_mtv.client.worldui.MtvWorldUiInputHook;

public class EntityPlayerClientEntryPoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MtvClientChannelPayloads.register();
        ClientChannelPlaybackManager.getInstance().clear();
        MtvHudCommand.register();
        EntityPlayerManager.getInstance().onInitialize();
        MtvWorldUiRenderer.initialize();
        MtvWorldUiInputHook.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(client ->
                HudChannelPlayer.getInstance().onClientTick());

    }
}
