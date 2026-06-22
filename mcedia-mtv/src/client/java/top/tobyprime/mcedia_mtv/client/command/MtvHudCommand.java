package top.tobyprime.mcedia_mtv.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
            var hud = buildHudCommand();
            var mcedia = dispatcher.getRoot().getChild("mcedia");
            if (mcedia != null) {
                var mtv = mcedia.getChild("mtv");
                if (mtv != null) {
                    mtv.addChild(hud.build());
                } else {
                    mcedia.addChild(ClientCommandManager.literal("mtv").then(hud).build());
                }
            } else {
                dispatcher.register(ClientCommandManager.literal("mcedia")
                        .then(ClientCommandManager.literal("mtv").then(hud)));
            }
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildHudCommand() {
        var hud = ClientCommandManager.literal("hud");

        hud.then(ClientCommandManager.literal("status")
                .executes(ctx -> status(ctx.getSource())));

        var screen = ClientCommandManager.literal("screen");
        screen.then(ClientCommandManager.literal("on")
                .executes(ctx -> setScreen(ctx.getSource(), true)));
        screen.then(ClientCommandManager.literal("off")
                .executes(ctx -> setScreen(ctx.getSource(), false)));

        var pos = ClientCommandManager.literal("pos");
        pos.then(ClientCommandManager.argument("x", IntegerArgumentType.integer(0))
                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(0))
                        .executes(ctx -> setPos(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "x"),
                                IntegerArgumentType.getInteger(ctx, "y")))));
        screen.then(pos);

        var size = ClientCommandManager.literal("size");
        size.then(ClientCommandManager.argument("width", IntegerArgumentType.integer(1))
                .then(ClientCommandManager.argument("height", IntegerArgumentType.integer(1))
                        .executes(ctx -> setSize(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "width"),
                                IntegerArgumentType.getInteger(ctx, "height")))));
        screen.then(size);

        hud.then(screen);
        return hud;
    }

    private static int status(FabricClientCommandSource source) {
        var hud = HudChannelPlayer.getInstance();
        var channelId = hud.getCurrentChannelId();
        if (channelId != null) {
            source.sendFeedback(Component.literal("HUD channel: " + channelId));
            source.sendFeedback(Component.literal("Screen: " + (hud.isScreenEnabled() ? "enabled" : "disabled")));
            if (hud.isScreenEnabled()) {
                source.sendFeedback(Component.literal("  Position: (" + hud.getScreenX() + ", " + hud.getScreenY() + ")"));
                source.sendFeedback(Component.literal("  Size: " + hud.getScreenWidth() + "x" + hud.getScreenHeight()));
            }
        } else {
            source.sendFeedback(Component.literal("HUD not subscribed to any channel"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setScreen(FabricClientCommandSource source, boolean enabled) {
        HudChannelPlayer.getInstance().setScreenEnabled(enabled);
        source.sendFeedback(Component.literal("HUD screen " + (enabled ? "enabled" : "disabled")));
        return Command.SINGLE_SUCCESS;
    }

    private static int setPos(FabricClientCommandSource source, int x, int y) {
        HudChannelPlayer.getInstance().setScreenPosition(x, y);
        source.sendFeedback(Component.literal("HUD screen position set to (" + x + ", " + y + ")"));
        return Command.SINGLE_SUCCESS;
    }

    private static int setSize(FabricClientCommandSource source, int width, int height) {
        HudChannelPlayer.getInstance().setScreenSize(width, height);
        source.sendFeedback(Component.literal("HUD screen size set to " + width + "x" + height));
        return Command.SINGLE_SUCCESS;
    }
}
