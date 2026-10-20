package top.tobyprime.mcedia_mtv.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import top.tobyprime.mcedia.runtime.NativeRuntime;
import top.tobyprime.mcedia_mtv.client.channel.ClientChannelPlaybackManager;
import top.tobyprime.mcedia_mtv.client.channel.MtvClientChannelPayloads;
import top.tobyprime.mcedia_mtv.client.command.MtvHudCommand;
import top.tobyprime.mcedia_mtv.client.entityplayer.EntityPlayerManager;

public class EntityPlayerClientEntryPoint implements ClientModInitializer {
    private static final String NATIVE_VERSION = "1.3.2";
    private static final String NATIVE_DOWNLOAD_BASE = "https://github.com/tobyprime/McediaTV/releases/download/v1.4.7";
    private boolean nativeNoticeShown;

    @Override
    public void onInitializeClient() {
        MtvClientChannelPayloads.register();
        ClientChannelPlaybackManager.getInstance().clear();
        MtvHudCommand.register();
        EntityPlayerManager.getInstance().onInitialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HudChannelPlayer.getInstance().onClientTick();
            if (!nativeNoticeShown) {
                nativeNoticeShown = true;
                NativeRuntime.configure(FabricLoader.getInstance().getGameDir(), NATIVE_VERSION, NATIVE_DOWNLOAD_BASE, "");
                if (!NativeRuntime.isAvailable()) MtvNativeRuntimeScreen.open(client);
            }
        });
    }
}
