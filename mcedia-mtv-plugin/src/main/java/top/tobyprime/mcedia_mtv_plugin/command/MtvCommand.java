package top.tobyprime.mcedia_mtv_plugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.manager.MtvPlayerManager;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;
import top.tobyprime.mcedia_mtv_plugin.selector.MtvPlayerSelector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 /mtv player set <实体选择器:类似 @e[type=item_display, range=xxx ,...]> url <url>
 /mtv player set <实体选择器> channel <channel id>
 /mtv player perip <实体选择器> set speaker <speaker id> volume <volume>
 /mtv player perip <实体选择器> set screen <screen id> light <light>
 ...
 /mtv channel pause <channel id> <true|false>
 ...
 */
public class MtvCommand implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtvCommand.class);

    private final MtvPeripheralController controller;
    private final MtvPlaybackController playbackController;
    private final MtvGui gui;
    private final MtvPlayerSelector selector;
    private final MtvPlayerManager manager;

    public MtvCommand(MtvPeripheralController controller, MtvPlaybackController playbackController, MtvGui gui, MtvPlayerSelector selector) {
        this.controller = controller;
        this.playbackController = playbackController;
        this.gui = gui;
        this.selector = selector;
        this.manager = controller.getManager();
    }

    private record TargetSelection(UUID uuid, ManagedMtvPlayer snapshot) {
    }

    @FunctionalInterface
    private interface PlayerOp {
        void run(TargetSelection selection, Consumer<Boolean> done);
    }

    public LiteralCommandNode<CommandSourceStack> buildCommandTree() {
        return Commands.literal("mtv")
                .then(Commands.literal("help")
                        .executes(this::executeHelp))
                .then(Commands.literal("gui")
                        .executes(this::executeGui))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(this::executeCreate)))
                .executes(this::executeRoot)
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildPlayerBranch() {
        return Commands.literal("player")
                .requires(source -> source.getSender().hasPermission("minecraft.command.selector"))
                .then(Commands.argument("target", ArgumentTypes.entity())
                        .then(Commands.literal("url")
                                .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.updateMediaUrl(selection.uuid(), StringArgumentType.getString(ctx, "mediaUrl"), done),
                                                "已设置播放链接。",
                                                "设置播放链接失败。"))))
                        .then(Commands.literal("current-url")
                                .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.updateMediaUrlAsCurrentOnly(selection.uuid(), StringArgumentType.getString(ctx, "mediaUrl"), done),
                                                "已设置当前媒体。",
                                                "设置当前媒体失败。"))))
                        .then(Commands.literal("pause")
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.togglePause(selection.uuid(), done),
                                        "已切换暂停状态。",
                                        "切换暂停状态失败。")))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.25F, 4.0F))
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.updateSpeed(selection.uuid(), FloatArgumentType.getFloat(ctx, "value"), done),
                                                "已更新播放速度。",
                                                "更新播放速度失败。"))))
                        .then(Commands.literal("seek")
                                .then(Commands.argument("deltaUs", LongArgumentType.longArg())
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.seekRelative(selection.uuid(), LongArgumentType.getLong(ctx, "deltaUs"), done),
                                                "已调整播放位置。",
                                                "调整播放位置失败。"))))
                        .then(Commands.literal("start-at")
                                .then(Commands.argument("positionUs", LongArgumentType.longArg(0L))
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.updateStartAt(selection.uuid(), LongArgumentType.getLong(ctx, "positionUs"), done),
                                                "已设置播放位置。",
                                                "设置播放位置失败。"))))
                        .then(Commands.literal("next")
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.playNextManual(selection.uuid(), done),
                                        "已切到下一项。",
                                        "切到下一项失败。")))
                        .then(Commands.literal("prev")
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.playPreviousManual(selection.uuid(), done),
                                        "已切到上一项。",
                                        "切到上一项失败。")))
                        .then(buildPlayerPlaylistBranch())
                        .then(Commands.literal("order")
                                .then(Commands.literal("cycle")
                                        .executes(ctx -> executePlayerPlayback(ctx,
                                                (selection, done) -> playbackController.cyclePlayOrderMode(selection.uuid(), done),
                                                "已切换播放顺序。",
                                                "切换播放顺序失败。"))))
                        .then(Commands.literal("channel")
                                .then(Commands.literal("self")
                                        .executes(ctx -> executePlayerTarget(ctx, selection -> manager.updateChannelBinding(selection.uuid(), MtvChannelBinding.self(), success -> reply(ctx, success, "已切回私有 self 频道。", "切回私有 self 频道失败。")))))
                                .then(Commands.literal("bind")
                                        .then(Commands.argument("channelId", StringArgumentType.word())
                                                .executes(this::executePlayerChannelBind))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("newName", StringArgumentType.greedyString())
                                        .executes(this::executePlayerRename)))
                        .then(Commands.literal("tp")
                                .executes(this::executePlayerTeleport))
                        .then(Commands.literal("movehere")
                                .executes(this::executePlayerMoveHere))
                        .then(Commands.literal("remove")
                                .executes(this::executePlayerRemove))
                        .then(buildPlayerPeriphBranch()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildPlayerPlaylistBranch() {
        return Commands.literal("playlist")
                .then(Commands.literal("append")
                        .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.appendPlaylistItem(selection.uuid(), StringArgumentType.getString(ctx, "mediaUrl"), done),
                                        "已尾加播放项。",
                                        "尾加播放项失败。"))))
                .then(Commands.literal("prepend")
                        .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.prependPlaylistItem(selection.uuid(), StringArgumentType.getString(ctx, "mediaUrl"), done),
                                        "已首加播放项。",
                                        "首加播放项失败。"))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.removePlaylistItem(selection.uuid(), IntegerArgumentType.getInteger(ctx, "index") - 1, done),
                                        "已删除播放项。",
                                        "删除播放项失败。"))))
                .then(Commands.literal("clear")
                        .executes(ctx -> executePlayerPlayback(ctx,
                                (selection, done) -> playbackController.clearPlaylist(selection.uuid(), done),
                                "已清空播放列表。",
                                "清空播放列表失败。")))
                .then(Commands.literal("play")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.playPlaylistIndex(selection.uuid(), IntegerArgumentType.getInteger(ctx, "index") - 1, done),
                                        "已切换到指定播放项。",
                                        "切换到指定播放项失败。"))))
                .then(Commands.literal("front")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.movePlaylistItemToFront(selection.uuid(), IntegerArgumentType.getInteger(ctx, "index") - 1, done),
                                        "已将播放项移到最前。",
                                        "移动播放项失败。"))))
                .then(Commands.literal("back")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executePlayerPlayback(ctx,
                                        (selection, done) -> playbackController.movePlaylistItemToBack(selection.uuid(), IntegerArgumentType.getInteger(ctx, "index") - 1, done),
                                        "已将播放项移到最后。",
                                        "移动播放项失败。"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildPlayerPeriphBranch() {
        return Commands.literal("periph")
                .then(buildScreenBranch())
                .then(buildSpeakerBranch());
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildScreenBranch() {
        return Commands.literal("screen")
                .then(Commands.literal("brightness")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                .executes(ctx -> executeScreenBrightness(ctx, null))))
                .then(Commands.argument("screenId", StringArgumentType.word())
                        .then(Commands.literal("brightness")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                        .executes(ctx -> executeScreenBrightness(ctx, StringArgumentType.getString(ctx, "screenId")))))
                        .then(Commands.literal("size")
                                .then(Commands.argument("width", FloatArgumentType.floatArg(0.25F))
                                        .then(Commands.argument("height", FloatArgumentType.floatArg(0.25F))
                                                .executes(ctx -> executeScreenSize(ctx, StringArgumentType.getString(ctx, "screenId"))))))
                        .then(Commands.literal("offset")
                                .then(Commands.argument("x", FloatArgumentType.floatArg())
                                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                        .executes(ctx -> executeScreenOffset(ctx, StringArgumentType.getString(ctx, "screenId")))))))
                        .then(Commands.literal("fill")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .executes(ctx -> executeScreenFill(ctx, StringArgumentType.getString(ctx, "screenId")))))
                        .then(Commands.literal("danmaku")
                                .then(Commands.argument("visible", BoolArgumentType.bool())
                                        .executes(ctx -> executeScreenDanmaku(ctx, StringArgumentType.getString(ctx, "screenId"))))))
                .then(Commands.literal("size")
                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.25F))
                                .then(Commands.argument("height", FloatArgumentType.floatArg(0.25F))
                                        .executes(ctx -> executeScreenSize(ctx, null)))))
                .then(Commands.literal("offset")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(ctx -> executeScreenOffset(ctx, null))))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> executeScreenFill(ctx, null))))
                .then(Commands.literal("danmaku")
                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                .executes(ctx -> executeScreenDanmaku(ctx, null))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSpeakerBranch() {
        return Commands.literal("speaker")
                .then(Commands.literal("volume")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 4.0F))
                                .executes(ctx -> executeSpeakerVolume(ctx, null))))
                .then(Commands.argument("speakerId", StringArgumentType.word())
                        .then(Commands.literal("volume")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 4.0F))
                                        .executes(ctx -> executeSpeakerVolume(ctx, StringArgumentType.getString(ctx, "speakerId")))))
                        .then(Commands.literal("range")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(1.0F))
                                        .executes(ctx -> executeSpeakerRange(ctx, StringArgumentType.getString(ctx, "speakerId")))))
                        .then(Commands.literal("offset")
                                .then(Commands.argument("x", FloatArgumentType.floatArg())
                                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                        .executes(ctx -> executeSpeakerOffset(ctx, StringArgumentType.getString(ctx, "speakerId"))))))))
                .then(Commands.literal("range")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(1.0F))
                                .executes(ctx -> executeSpeakerRange(ctx, null))))
                .then(Commands.literal("offset")
                        .then(Commands.argument("x", FloatArgumentType.floatArg())
                                .then(Commands.argument("y", FloatArgumentType.floatArg())
                                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                                .executes(ctx -> executeSpeakerOffset(ctx, null))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildChannelBranch() {
        return Commands.literal("channel")
                .then(Commands.argument("channelId", StringArgumentType.word())
                        .then(Commands.literal("url")
                                .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                        .executes(this::executeChannelUrl)))
                        .then(Commands.literal("pause")
                                .executes(this::executeChannelPause))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.25F, 4.0F))
                                        .executes(this::executeChannelSpeed)))
                        .then(Commands.literal("seek")
                                .then(Commands.argument("deltaUs", LongArgumentType.longArg())
                                        .executes(this::executeChannelSeek)))
                        .then(Commands.literal("start-at")
                                .then(Commands.argument("positionUs", LongArgumentType.longArg(0L))
                                        .executes(this::executeChannelStartAt)))
                        .then(Commands.literal("next")
                                .executes(this::executeChannelNext))
                        .then(Commands.literal("prev")
                                .executes(this::executeChannelPrev))
                        .then(Commands.literal("playlist")
                                .then(Commands.literal("append")
                                        .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                                .executes(this::executeChannelPlaylistAppend)))
                                .then(Commands.literal("prepend")
                                        .then(Commands.argument("mediaUrl", StringArgumentType.greedyString())
                                                .executes(this::executeChannelPlaylistPrepend)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::executeChannelPlaylistRemove)))
                                .then(Commands.literal("clear")
                                        .executes(this::executeChannelPlaylistClear))
                                .then(Commands.literal("play")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::executeChannelPlaylistPlay)))
                                .then(Commands.literal("front")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::executeChannelPlaylistFront)))
                                .then(Commands.literal("back")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(this::executeChannelPlaylistBack))))
                        .then(Commands.literal("order")
                                .then(Commands.literal("cycle")
                                        .executes(this::executeChannelOrderCycle))));
    }

    private int executeRoot(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            gui.navigateTo(player, MtvGui.GuiType.MAIN_MENU, null, null, null);
        } else {
            sendHelp(sender);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeHelp(CommandContext<CommandSourceStack> ctx) {
        sendHelp(ctx.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private int executeGui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.gui")) {
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以打开 MTV GUI。");
            return 0;
        }
        gui.navigateTo(player, MtvGui.GuiType.MAIN_MENU, null, null, null);
        return Command.SINGLE_SUCCESS;
    }

    private int executeCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.player.create")) {
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以创建 MTV 播放器。");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name").trim();
        if (name.isBlank()) {
            sender.sendMessage("名称不能为空。");
            return 0;
        }
        manager.createPlayerAsync(player.getLocation(), name, created -> player.getScheduler().run(manager.getPlugin(), task -> {
            if (created == null) {
                sender.sendMessage("创建 MTV 播放器失败。");
                return;
            }
            sender.sendMessage("已创建 MTV 播放器: " + created.getName());
            gui.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, created.getUuid(), null, null);
        }, null));
        return Command.SINGLE_SUCCESS;
    }

    private int executePlayerChannelBind(CommandContext<CommandSourceStack> ctx) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        String channelId = StringArgumentType.getString(ctx, "channelId");
        if (manager.getChannelService().getPublicChannel(channelId) == null) {
            ctx.getSource().getSender().sendMessage("该公共频道不存在: " + channelId);
            return 0;
        }
        return executePlayerTarget(ctx, selection -> manager.updateChannelBinding(selection.uuid(), MtvChannelBinding.broadcast(channelId), success -> reply(ctx, success, "已绑定公共频道。", "绑定公共频道失败。")));
    }

    private int executePlayerRename(CommandContext<CommandSourceStack> ctx) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        String newName = StringArgumentType.getString(ctx, "newName").trim();
        if (newName.isBlank()) {
            ctx.getSource().getSender().sendMessage("名称不能为空。");
            return 0;
        }
        return executePlayerTarget(ctx, selection -> manager.updateName(selection.uuid(), newName, success -> reply(ctx, success, "已重命名 MTV 播放器。", "重命名 MTV 播放器失败。")));
    }

    private int executePlayerTeleport(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.player.teleport")) {
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以传送到 MTV 播放器。");
            return 0;
        }
        return executePlayerTarget(ctx, selection -> {
            Location location = selection.snapshot().toLocation();
            if (location == null) {
                sender.sendMessage("该 MTV 的位置无效或所在世界不存在。");
                return;
            }
            player.teleportAsync(location);
            sender.sendMessage("已传送到 MTV 播放器: " + selection.snapshot().getName());
        });
    }

    private int executePlayerMoveHere(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.player.edit")) {
            return 0;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只有玩家可以将 MTV 移动到自己位置。");
            return 0;
        }
        return executePlayerTarget(ctx, selection -> manager.teleportToPlayer(selection.uuid(), player, success -> reply(ctx, success, "已将 MTV 移动到你的位置。", "移动 MTV 失败。")));
    }

    private int executePlayerRemove(CommandContext<CommandSourceStack> ctx) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        return executePlayerTarget(ctx, selection -> manager.deletePlayerAsync(selection.uuid(), success -> reply(ctx, success, "已删除 MTV 播放器。", "删除 MTV 播放器失败。")));
    }

    private int executeScreenBrightness(CommandContext<CommandSourceStack> ctx, String screenId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        return executePlayerTarget(ctx, selection -> controller.setScreenBrightness(selection.uuid(), selection.snapshot(), screenId, value, success -> reply(ctx, success, "已更新屏幕亮度。", "更新屏幕亮度失败。")));
    }

    private int executeScreenSize(CommandContext<CommandSourceStack> ctx, String screenId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        float width = FloatArgumentType.getFloat(ctx, "width");
        float height = FloatArgumentType.getFloat(ctx, "height");
        return executePlayerTarget(ctx, selection -> controller.setScreenSize(selection.uuid(), selection.snapshot(), screenId, width, height, success -> reply(ctx, success, "已更新屏幕尺寸。", "更新屏幕尺寸失败。")));
    }

    private int executeScreenOffset(CommandContext<CommandSourceStack> ctx, String screenId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        float x = FloatArgumentType.getFloat(ctx, "x");
        float y = FloatArgumentType.getFloat(ctx, "y");
        float z = FloatArgumentType.getFloat(ctx, "z");
        return executePlayerTarget(ctx, selection -> controller.setScreenOffset(selection.uuid(), selection.snapshot(), screenId, x, y, z, success -> reply(ctx, success, "已更新屏幕偏移。", "更新屏幕偏移失败。")));
    }

    private int executeScreenFill(CommandContext<CommandSourceStack> ctx, String screenId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        String mode = StringArgumentType.getString(ctx, "mode");
        return executePlayerTarget(ctx, selection -> controller.setScreenFillMode(selection.uuid(), selection.snapshot(), screenId, mode, success -> reply(ctx, success, "已更新屏幕填充模式。", "更新屏幕填充模式失败。")));
    }

    private int executeScreenDanmaku(CommandContext<CommandSourceStack> ctx, String screenId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        boolean visible = BoolArgumentType.getBool(ctx, "visible");
        return executePlayerTarget(ctx, selection -> controller.setScreenDanmakuVisible(selection.uuid(), selection.snapshot(), screenId, visible, success -> reply(ctx, success, "已更新屏幕弹幕可见性。", "更新屏幕弹幕可见性失败。")));
    }

    private int executeSpeakerVolume(CommandContext<CommandSourceStack> ctx, String speakerId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        return executePlayerTarget(ctx, selection -> controller.setSpeakerVolume(selection.uuid(), selection.snapshot(), speakerId, value, success -> reply(ctx, success, "已更新扬声器音量。", "更新扬声器音量失败。")));
    }

    private int executeSpeakerRange(CommandContext<CommandSourceStack> ctx, String speakerId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        return executePlayerTarget(ctx, selection -> controller.setSpeakerRange(selection.uuid(), selection.snapshot(), speakerId, value, success -> reply(ctx, success, "已更新扬声器范围。", "更新扬声器范围失败。")));
    }

    private int executeSpeakerOffset(CommandContext<CommandSourceStack> ctx, String speakerId) {
        if (!hasPermission(ctx.getSource().getSender(), "mtv.player.edit")) {
            return 0;
        }
        float x = FloatArgumentType.getFloat(ctx, "x");
        float y = FloatArgumentType.getFloat(ctx, "y");
        float z = FloatArgumentType.getFloat(ctx, "z");
        return executePlayerTarget(ctx, selection -> controller.setSpeakerOffset(selection.uuid(), selection.snapshot(), speakerId, x, y, z, success -> reply(ctx, success, "已更新扬声器偏移。", "更新扬声器偏移失败。")));
    }

    private int executeChannelUrl(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        String mediaUrl = StringArgumentType.getString(ctx, "mediaUrl");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().updateMediaUrl(channelIdValue, mediaUrl),
                "已更新频道媒体。",
                "更新频道媒体失败。");
    }

    private int executeChannelPause(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        return executeChannelMutation(ctx, channelId,
                manager.getChannelService()::togglePause,
                "已切换频道暂停状态。",
                "切换频道暂停状态失败。");
    }

    private int executeChannelSpeed(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        float speed = FloatArgumentType.getFloat(ctx, "value");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().updateSpeed(channelIdValue, speed),
                "已更新频道速度。",
                "更新频道速度失败。");
    }

    private int executeChannelSeek(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        long deltaUs = LongArgumentType.getLong(ctx, "deltaUs");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().seekRelative(channelIdValue, deltaUs),
                "已调整频道播放位置。",
                "调整频道播放位置失败。");
    }

    private int executeChannelStartAt(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        long positionUs = LongArgumentType.getLong(ctx, "positionUs");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().updateStartAt(channelIdValue, positionUs),
                "已设置频道播放位置。",
                "设置频道播放位置失败。");
    }

    private int executeChannelNext(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        return executeChannelMutation(ctx, channelId,
                manager.getChannelService()::playNextManual,
                "已切到下一项。",
                "切到下一项失败。");
    }

    private int executeChannelPrev(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        return executeChannelMutation(ctx, channelId,
                manager.getChannelService()::playPreviousManual,
                "已切到上一项。",
                "切到上一项失败。");
    }

    private int executeChannelPlaylistAppend(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        String mediaUrl = StringArgumentType.getString(ctx, "mediaUrl");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().appendPlaylistItem(channelIdValue, mediaUrl),
                "已尾加播放项。",
                "尾加播放项失败。");
    }

    private int executeChannelPlaylistPrepend(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        String mediaUrl = StringArgumentType.getString(ctx, "mediaUrl");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().prependPlaylistItem(channelIdValue, mediaUrl),
                "已首加播放项。",
                "首加播放项失败。");
    }

    private int executeChannelPlaylistRemove(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().removePlaylistItem(channelIdValue, index),
                "已删除播放项。",
                "删除播放项失败。");
    }

    private int executeChannelPlaylistClear(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().clearPlaylist(ctx.getSource().getSender() instanceof Player player ? player : null, channelIdValue),
                "已清空频道播放列表。",
                "清空频道播放列表失败。");
    }

    private int executeChannelPlaylistPlay(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().playPlaylistIndex(channelIdValue, index),
                "已切换到指定播放项。",
                "切换到指定播放项失败。");
    }

    private int executeChannelPlaylistFront(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().movePlaylistItemToFront(channelIdValue, index),
                "已将播放项移到最前。",
                "移动播放项失败。");
    }

    private int executeChannelPlaylistBack(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        int index = IntegerArgumentType.getInteger(ctx, "index") - 1;
        return executeChannelMutation(ctx, channelId,
                channelIdValue -> manager.getChannelService().movePlaylistItemToBack(channelIdValue, index),
                "已将播放项移到最后。",
                "移动播放项失败。");
    }

    private int executeChannelOrderCycle(CommandContext<CommandSourceStack> ctx) {
        String channelId = StringArgumentType.getString(ctx, "channelId");
        return executeChannelMutation(ctx, channelId,
                manager.getChannelService()::cyclePlayOrderMode,
                "已切换频道播放顺序。",
                "切换频道播放顺序失败。");
    }

    private int executePlayerPlayback(CommandContext<CommandSourceStack> ctx, PlayerOp operation, String successMessage, String failureMessage) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.player.edit")) {
            return 0;
        }
        return executePlayerTarget(ctx, selection -> guardPlaybackControl(ctx, selection, allowed -> {
            if (!Boolean.TRUE.equals(allowed)) {
                return;
            }
            operation.run(selection, success -> reply(ctx, success, successMessage, failureMessage));
        }));
    }

    private int executePlayerTarget(CommandContext<CommandSourceStack> ctx, Consumer<TargetSelection> consumer) {
        TargetSelection selection = resolveBrigadierTarget(ctx);
        if (selection == null) {
            return 0;
        }
        consumer.accept(selection);
        return Command.SINGLE_SUCCESS;
    }

    private TargetSelection resolveBrigadierTarget(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        EntitySelectorArgumentResolver resolver = ctx.getArgument("target", EntitySelectorArgumentResolver.class);
        final List<Entity> entities;
        try {
            entities = resolver.resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            LOGGER.debug("Failed to resolve mtv selector", ex);
            sender.sendMessage("选择器解析失败，请检查目标表达式是否有效且只命中一个 MTV 实体。");
            return null;
        }
        if (entities.isEmpty()) {
            sender.sendMessage("未找到 MTV 播放器目标。请选择唯一的 MTV 实体。");
            return null;
        }
        if (entities.size() != 1) {
            sender.sendMessage("目标不唯一，请缩小选择器范围，只保留一个 MTV 目标。");
            return null;
        }
        Entity entity = entities.get(0);
        if (!(entity instanceof ItemDisplay display) || !manager.isManagedItemDisplay(display)) {
            sender.sendMessage("目标必须是一个 MTV 播放器对应的 ItemDisplay 实体。");
            return null;
        }
        ManagedMtvPlayer snapshot = manager.readFromEntity(display);
        return new TargetSelection(display.getUniqueId(), snapshot);
    }

    private void guardPlaybackControl(CommandContext<CommandSourceStack> ctx, TargetSelection selection, Consumer<Boolean> done) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            done.accept(Boolean.TRUE);
            return;
        }
        playbackController.canControlPlayback(selection.uuid(), player, allowed -> player.getScheduler().run(manager.getPlugin(), task -> {
            if (!Boolean.TRUE.equals(allowed)) {
                sender.sendMessage("该频道未开启公开控制，只有创建者或拥有 mtv.channel.control.others 权限的玩家可以控制播放。");
                done.accept(Boolean.FALSE);
                return;
            }
            done.accept(Boolean.TRUE);
        }, null));
    }

    @FunctionalInterface
    private interface ChannelMutation {
        boolean apply(String channelId);
    }

    private int executeChannelMutation(CommandContext<CommandSourceStack> ctx,
                                       String channelId,
                                       ChannelMutation mutation,
                                       String successMessage,
                                       String failureMessage) {
        CommandSender sender = ctx.getSource().getSender();
        if (!hasPermission(sender, "mtv.player.edit")) {
            return 0;
        }
        ChannelRuntimeState state = manager.getChannelService().ensureChannelState(channelId);
        if (state == null) {
            sender.sendMessage("该频道不存在: " + channelId);
            return 0;
        }
        if (!canControlChannel(sender, state)) {
            return 0;
        }
        reply(ctx, mutation.apply(channelId), successMessage, failureMessage);
        return Command.SINGLE_SUCCESS;
    }

    private boolean canControlChannel(CommandSender sender, ChannelRuntimeState state) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!manager.getChannelService().canControlChannelPlayback(player, state)) {
            sender.sendMessage("该频道未开启公开控制，只有创建者或拥有 mtv.channel.control.others 权限的玩家可以控制播放。");
            return false;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("/mtv - 打开 MTV 主菜单");
        sender.sendMessage("/mtv gui - 打开 MTV 主菜单");
        sender.sendMessage("/mtv create <name> - 在自己位置创建 MTV 播放器");
        sender.sendMessage("player 与 channel 的控制已改为通过 GUI 完成。请使用 /mtv 进入界面操作。");
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage("你没有权限执行此操作。需要权限: " + permission);
        return false;
    }

    private void reply(CommandContext<CommandSourceStack> ctx, Boolean success, String successMessage, String failureMessage) {
        ctx.getSource().getSender().sendMessage(Boolean.TRUE.equals(success) ? successMessage : failureMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        return null;
    }
}
