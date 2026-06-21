package top.tobyprime.mcedia_mtv.client.command;

import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import top.tobyprime.mcedia_mtv.client.HudChannelPlayer;

public final class MtvHudCommand {
    private MtvHudCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("mtvhud");

            root.then(ClientCommandManager.literal("status")
                    .executes(context -> showStatus(context.getSource())));

            dispatcher.register(root);
        });
    }

    private static int showStatus(FabricClientCommandSource source) {
        var hud = HudChannelPlayer.getInstance();
        var channelId = hud.getCurrentChannelId();
        if (channelId != null) {
            source.sendFeedback(Component.literal("HUD channel: " + channelId));
        } else {
            source.sendFeedback(Component.literal("HUD not subscribed to any channel"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
