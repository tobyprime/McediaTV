package top.tobyprime.mcedia_mtv_plugin.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPlaybackController;
import top.tobyprime.mcedia_mtv_plugin.gui.MtvGui;
import top.tobyprime.mcedia_mtv_plugin.model.ManagedMtvPlayer;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class MtvCommand implements CommandExecutor, TabCompleter {
    private static final double RANGE = 50.0;
    private static final String EDIT_SUBCOMMAND = "edit";
    private static final String PLAYER_SCOPE = "player";
    private static final String CHANNEL_SCOPE = "channel";

    private final MtvPeripheralController controller;
    private final MtvPlaybackController playbackController;
    private final MtvGui gui;

    public MtvCommand(MtvPeripheralController controller, MtvPlaybackController playbackController, MtvGui gui) {
        this.controller = controller;
        this.playbackController = playbackController;
        this.gui = gui;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommandTree() {
        return Commands.literal("mtv")
                .executes(ctx -> executeBrigadier(ctx.getSource()))
                .then(Commands.literal("create")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "create"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "create", ctx.getArgument("name", String.class)))))
                .then(Commands.literal("edit")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "edit"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "edit", ctx.getArgument("name", String.class)))))
                .then(buildScopedEditNode(PLAYER_SCOPE))
                .then(buildChannelNode())
                .then(Commands.literal("remove")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "remove"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "remove", ctx.getArgument("name", String.class)))))
                .then(Commands.literal("list")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "list")))
                .then(Commands.literal("gui")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "gui")))
                .then(Commands.literal("url")
                        .then(Commands.argument("url", StringArgumentType.string())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "url", ctx.getArgument("url", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "url", ctx.getArgument("url", String.class), ctx.getArgument("name", String.class))))))
                .then(Commands.literal("speed")
                        .then(Commands.argument("speed", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "speed", Float.toString(FloatArgumentType.getFloat(ctx, "speed"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "speed", Float.toString(FloatArgumentType.getFloat(ctx, "speed")), ctx.getArgument("name", String.class))))))
                .then(Commands.literal("tp")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "tp"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "tp", ctx.getArgument("name", String.class)))))
                .then(Commands.literal("movehere")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "movehere"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "movehere", ctx.getArgument("name", String.class)))))
                .then(buildSizeNode())
                .then(buildOffsetNode())
                .then(buildFillNode())
                .then(buildBrightnessNode())
                .then(buildDanmakuNode())
                .then(buildVolumeNode())
                .then(buildRangeNode())
                .then(Commands.literal("start")
                        .then(Commands.argument("startAtUs", LongArgumentType.longArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "start", Long.toString(LongArgumentType.getLong(ctx, "startAtUs"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "start", Long.toString(LongArgumentType.getLong(ctx, "startAtUs")), ctx.getArgument("name", String.class))))))
                .then(Commands.literal("name")
                        .then(Commands.argument("newName", StringArgumentType.word())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "name", ctx.getArgument("newName", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "name", ctx.getArgument("newName", String.class), ctx.getArgument("name", String.class))))))
                .then(buildRotateNode())
                .then(buildSpeakerOffsetNode())
                .then(buildSnapNode())
                .build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return dispatch(sender, args);
    }

    public boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }
        return switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "player" -> handlePlayer(sender, args);
            case "channel" -> handleChannel(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "gui" -> handleGui(sender);
            case "url" -> handleUrl(sender, args);
            case "speed" -> handleSpeed(sender, args);
            case "tp" -> handleTp(sender, args);
            case "movehere" -> handleMoveHere(sender, args);
            case "size" -> handleSize(sender, args);
            case "offset" -> handleOffset(sender, args);
            case "fill" -> handleFill(sender, args);
            case "brightness" -> handleBrightness(sender, args);
            case "danmaku" -> handleDanmaku(sender, args);
            case "volume" -> handleVolume(sender, args);
            case "range" -> handleRange(sender, args);
            case "start" -> handleStart(sender, args);
            case "rotate" -> handleRotate(sender, args);
            case "soffset" -> handleSoffset(sender, args);
            case "snap" -> handleSnap(sender, args);
            case "name" -> handleName(sender, args);
            default -> showHelp(sender);
        };
    }

    // ==================== Commands without periphId ====================

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!sender.hasPermission("mcedia.mtv.create")) {
            sender.sendMessage("你没有权限创建 MTV 播放器。");
            return true;
        }
        if (args.length >= 2) {
            String name = joinArgs(args, 1);
            var location = getTargetLocation(player);
            controller.getManager().createPlayerAsync(location, name, created -> runOnPlayer(player, () -> {
                if (created == null) {
                    player.sendMessage("创建失败，名称可能已存在。");
                    return;
                }
                player.sendMessage("已创建 MTV: " + created.getName());
                gui.openPlayerMenu(player, created);
            }));
            return true;
        }
        player.closeInventory();
        player.sendMessage("请输入 MTV 名称。");
        gui.setAwaitingInput(player, MtvGui.GuiType.MAIN_MENU, null, "create_name");
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        return openPlayerEditor(sender, args.length >= 2 ? joinArgs(args, 1) : null);
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        return handleScopedEdit(sender, args, PLAYER_SCOPE, player -> gui.openPlayerMenu((Player) sender, player));
    }

    private boolean handleChannel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv channel <edit|list|create> ...");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case EDIT_SUBCOMMAND -> openChannelEditor(sender, args.length >= 3 ? joinArgs(args, 2) : null);
            case "list" -> openChannelList(sender);
            case "create" -> openChannelCreate(sender);
            default -> {
                sender.sendMessage("用法: /mtv channel <edit|list|create> ...");
                yield true;
            }
        };
    }

    private boolean openPlayerEditor(CommandSender sender, String nameFilter) {
        return openEditor(sender, nameFilter, player -> gui.openPlayerMenu((Player) sender, player));
    }

    private boolean openChannelEditor(CommandSender sender, String nameFilter) {
        return openEditor(sender, nameFilter, player -> gui.openChannelMenu((Player) sender, player));
    }

    private boolean handleScopedEdit(CommandSender sender, String[] args, String scope, Consumer<ManagedMtvPlayer> opener) {
        if (args.length >= 2 && EDIT_SUBCOMMAND.equalsIgnoreCase(args[1])) {
            return openEditor(sender, args.length >= 3 ? joinArgs(args, 2) : null, opener);
        }
        sender.sendMessage("用法: /mtv " + scope + " edit [名称]");
        return true;
    }

    private boolean openChannelList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        gui.openPublicChannelList(player, null, "", 0, false);
        return true;
    }

    private boolean openChannelCreate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!player.hasPermission("mcedia.mtv.channel.create")) {
            player.sendMessage("你没有权限创建公共频道。");
            return true;
        }
        gui.openPublicChannelCreate(player, null, "", "", "", 0, false);
        return true;
    }

    private boolean openEditor(CommandSender sender, String nameFilter, Consumer<ManagedMtvPlayer> opener) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        resolveOne(player, nameFilter, target -> {
            if (target != null) {
                opener.accept(target);
            }
        });
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!sender.hasPermission("mcedia.mtv.delete")) {
            sender.sendMessage("你没有权限删除 MTV 播放器。");
            return true;
        }
        if (args.length >= 2) {
            String name = joinArgs(args, 1);
            findFirstByName(player, name, found -> {
                if (found == null) {
                    player.sendMessage("附近没有找到名称为 \"" + name + "\" 的 MTV 播放器。");
                    return;
                }
                controller.getManager().deletePlayerAsync(found.getUuid(), success -> runOnPlayer(player, () -> {
                    if (success) {
                        sender.sendMessage("已删除 MTV: " + name);
                    }
                }));
            });
            return true;
        }
        resolveOne(player, null, target -> {
            if (target == null) {
                return;
            }
            controller.getManager().deletePlayerAsync(target.getUuid(), success -> runOnPlayer(player, () -> {
                if (success) {
                    sender.sendMessage("已删除 MTV: " + target.getName());
                }
            }));
        });
        return true;
    }

    private boolean handleUrl(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv url <地址> [名称]");
            return true;
        }
        String url;
        String nameFilter;
        if (args.length >= 3 && !args[args.length - 1].startsWith("http")) {
            url = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length - 1)).trim();
            nameFilter = args[args.length - 1];
        } else {
            url = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
            nameFilter = null;
        }
        if (url.isBlank()) {
            sender.sendMessage("用法: /mtv url <地址> [名称]");
            return true;
        }
        resolveOne(player, nameFilter, target -> {
            if (target == null) {
                return;
            }
            playbackController.updateMediaUrl(target.getUuid(), url, success -> runOnPlayer(player, () -> {
                if (success) {
                    sender.sendMessage("已更新 " + target.getName() + " 的 URL: " + url);
                }
            }));
        });
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        return withFloatTarget(sender, args, "倍速",
                (target, value, done) -> playbackController.updateSpeed(target.getUuid(), value, done));
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.teleport")) return true;
        Consumer<ManagedMtvPlayer> callback = target -> {
            if (target == null) {
                return;
            }
            if (target.toLocation() == null) {
                sender.sendMessage("该 MTV 播放器位置无效。");
                return;
            }
            player.teleportAsync(target.toLocation());
            sender.sendMessage("正在传送到 " + target.getName());
        };

        if (args.length >= 2) {
            findFirstByName(player, joinArgs(args, 1), target -> {
                if (target == null) {
                    sender.sendMessage("附近没有找到名称包含 \"" + joinArgs(args, 1) + "\" 的 MTV 播放器。");
                    return;
                }
                callback.accept(target);
            });
        } else {
            resolveOne(player, null, callback);
        }
        return true;
    }

    private boolean handleMoveHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.movehere")) return true;
        resolveOne(player, args.length >= 2 ? joinArgs(args, 1) : null, target -> {
            if (target == null) return;
            controller.getManager().teleportToPlayer(target.getUuid(), player,
                    success -> runOnPlayer(player, () -> {
                        if (success) sender.sendMessage("已将 " + target.getName() + " 传送到你脚下。");
                    }));
        });
        return true;
    }

    // ==================== Screen commands (with optional periphId) ====================

    private boolean handleSize(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 3 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 2) {
            sender.sendMessage("用法: /mtv size [外设ID] <宽> <高> [名称]");
            return true;
        }
        try {
            float w = Float.parseFloat(args[valStart]);
            float h = Float.parseFloat(args[valStart + 1]);
            resolveWithScreen(player, periphId, args.length > valStart + 2 ? joinArgs(args, valStart + 2) : null,
                    (target, id) -> controller.getManager().setScreenSize(target.getUuid(), id, w, h,
                            success -> runOnPlayer(player, () -> {
                                if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 尺寸: " + w + " x " + h);
                            })));
        } catch (NumberFormatException e) {
            sender.sendMessage("宽高必须是数字。");
        }
        return true;
    }

    private boolean handleOffset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 4 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 3) {
            sender.sendMessage("用法: /mtv offset [外设ID] <x> <y> <z> [名称]");
            return true;
        }
        try {
            float x = Float.parseFloat(args[valStart]);
            float y = Float.parseFloat(args[valStart + 1]);
            float z = Float.parseFloat(args[valStart + 2]);
            resolveWithScreen(player, periphId, args.length > valStart + 3 ? joinArgs(args, valStart + 3) : null,
                    (target, id) -> controller.getManager().setScreenOffset(target.getUuid(), id, x, y, z,
                            success -> runOnPlayer(player, () -> {
                                if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 偏移: " + x + ", " + y + ", " + z);
                            })));
        } catch (NumberFormatException e) {
            sender.sendMessage("偏移值必须是数字。");
        }
        return true;
    }

    private boolean handleFill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 3 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 1) {
            sender.sendMessage("用法: /mtv fill [外设ID] <fill|keep_aspect_cover> [名称]");
            return true;
        }
        String mode = args[valStart];
        if (!"fill".equalsIgnoreCase(mode) && !"keep_aspect_cover".equalsIgnoreCase(mode)) {
            sender.sendMessage("填充模式必须是 fill 或 keep_aspect_cover。");
            return true;
        }
        resolveWithScreen(player, periphId, args.length > valStart + 1 ? joinArgs(args, valStart + 1) : null,
                (target, id) -> controller.getManager().setScreenFillMode(target.getUuid(), id, mode,
                        success -> runOnPlayer(player, () -> {
                            if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 填充模式: " + mode);
                        })));
        return true;
    }

    private boolean handleBrightness(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 3 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 1) {
            sender.sendMessage("用法: /mtv brightness [外设ID] <0-15> [名称]");
            return true;
        }
        try {
            int value = Integer.parseInt(args[valStart]);
            resolveWithScreen(player, periphId, args.length > valStart + 1 ? joinArgs(args, valStart + 1) : null,
                    (target, id) -> controller.getManager().setScreenBrightness(target.getUuid(), id, value,
                            success -> runOnPlayer(player, () -> {
                                if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 亮度: " + value);
                            })));
        } catch (NumberFormatException e) {
            sender.sendMessage("亮度必须是整数。");
        }
        return true;
    }

    private boolean handleDanmaku(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 3 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 1) {
            sender.sendMessage("用法: /mtv danmaku [外设ID] <on|off> [名称]");
            return true;
        }
        String value = args[valStart].toLowerCase();
        if (!"on".equals(value) && !"off".equals(value)) {
            sender.sendMessage("弹幕开关必须是 on 或 off。");
            return true;
        }
        boolean visible = "on".equals(value);
        resolveWithScreen(player, periphId, args.length > valStart + 1 ? joinArgs(args, valStart + 1) : null,
                (target, id) -> controller.getManager().setScreenDanmakuVisible(target.getUuid(), id, visible,
                        success -> runOnPlayer(player, () -> {
                            if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 弹幕: " + value);
                        })));
        return true;
    }

    private boolean handleRotate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 5 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 4) {
            sender.sendMessage("用法: /mtv rotate [外设ID] <rx> <ry> <rz> <rw> [名称]");
            return true;
        }
        try {
            float rx = Float.parseFloat(args[valStart]);
            float ry = Float.parseFloat(args[valStart + 1]);
            float rz = Float.parseFloat(args[valStart + 2]);
            float rw = Float.parseFloat(args[valStart + 3]);
            resolveWithScreen(player, periphId, args.length > valStart + 4 ? joinArgs(args, valStart + 4) : null,
                    (target, id) -> controller.getManager().setScreenRotation(target.getUuid(), id, rx, ry, rz, rw,
                            success -> runOnPlayer(player, () -> {
                                if (success) sender.sendMessage("已设置 " + target.getName() + " 屏幕[" + id + "] 旋转: " + rx + ", " + ry + ", " + rz + ", " + rw);
                            })));
        } catch (NumberFormatException e) {
            sender.sendMessage("旋转值必须是数字。");
        }
        return true;
    }

    // ==================== Speaker commands (with optional periphId) ====================

    private boolean handleVolume(CommandSender sender, String[] args) {
        return handleSpeakerFloat(sender, args, "音量",
                (target, id, value, done) -> controller.getManager().setSpeakerVolume(target.getUuid(), id, value, done));
    }

    private boolean handleRange(CommandSender sender, String[] args) {
        return handleSpeakerFloat(sender, args, "范围",
                (target, id, value, done) -> controller.getManager().setSpeakerRange(target.getUuid(), id, value, done));
    }

    private boolean handleSoffset(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 4 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 3) {
            sender.sendMessage("用法: /mtv soffset [外设ID] <x> <y> <z> [名称]");
            return true;
        }
        try {
            float x = Float.parseFloat(args[valStart]);
            float y = Float.parseFloat(args[valStart + 1]);
            float z = Float.parseFloat(args[valStart + 2]);
            resolveWithSpeaker(player, periphId, args.length > valStart + 3 ? joinArgs(args, valStart + 3) : null,
                    (target, id) -> controller.getManager().setSpeakerOffset(target.getUuid(), id, x, y, z,
                            success -> runOnPlayer(player, () -> {
                                if (success) sender.sendMessage("已设置 " + target.getName() + " 扬声器[" + id + "] 偏移: " + x + ", " + y + ", " + z);
                            })));
        } catch (NumberFormatException e) {
            sender.sendMessage("偏移值必须是数字。");
        }
        return true;
    }

    // ==================== Player-level commands ====================

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        return withLongTarget(sender, args, "起始时间",
                (target, value, done) -> playbackController.updateStartAt(target.getUuid(), value, done));
    }

    private boolean handleName(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv name <新名称> [当前名称]");
            return true;
        }
        String newName = args[1];
        String filter = args.length >= 3 ? joinArgs(args, 2) : null;
        resolveOne(player, filter, target -> {
            if (target == null) return;
            controller.getManager().updateName(target.getUuid(), newName, success -> runOnPlayer(player, () -> {
                if (success) {
                    sender.sendMessage("已重命名为: " + newName);
                }
            }));
        });
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.list")) return true;
        controller.getManager().findNearbyAsync(player, RANGE, candidates -> runOnPlayer(player, () -> {
            if (candidates.isEmpty()) {
                sender.sendMessage("附近 50 米内没有 MTV 播放器。");
                return;
            }
            sender.sendMessage("附近的 MTV 播放器 (" + candidates.size() + "):");
            for (var p : candidates) {
                double dist = Math.sqrt(player.getLocation().distanceSquared(p.toLocation()));
                sender.sendMessage("- " + p.getName() + " (" + (int) dist + "m)");
            }
        }));
        return true;
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.gui")) return true;
        gui.openMainMenu(player);
        return true;
    }

    // ==================== Snap command ====================

    private boolean handleSnap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv snap <screen|speaker|entity> [外设ID] [名称]");
            return true;
        }
        return switch (args[1].toLowerCase()) {
            case "screen" -> handleSnapScreen(player, args);
            case "speaker" -> handleSnapSpeaker(player, args);
            case "entity" -> handleSnapEntity(player, args);
            default -> {
                sender.sendMessage("未知类型，可用: screen, speaker, entity");
                yield true;
            }
        };
    }

    private boolean handleSnapScreen(Player player, String[] args) {
        String periphId = args.length >= 3 && !looksLikeName(args[2]) ? args[2] : null;
        String nameFilter = args.length >= 3 && looksLikeName(args[2]) ? joinArgs(args, 2)
                : args.length >= 4 ? joinArgs(args, 3) : null;
        resolveWithScreen(player, periphId, nameFilter,
                (target, id) -> controller.getManager().snapScreenOffset(target.getUuid(), id,
                        success -> runOnPlayer(player, () -> {
                            if (success) player.sendMessage("已吸附 " + target.getName() + " 屏幕[" + id + "] 偏移到整数网格");
                        })));
        return true;
    }

    private boolean handleSnapSpeaker(Player player, String[] args) {
        String periphId = args.length >= 3 && !looksLikeName(args[2]) ? args[2] : null;
        String nameFilter = args.length >= 3 && looksLikeName(args[2]) ? joinArgs(args, 2)
                : args.length >= 4 ? joinArgs(args, 3) : null;
        resolveWithSpeaker(player, periphId, nameFilter,
                (target, id) -> controller.getManager().snapSpeakerOffset(target.getUuid(), id,
                        success -> runOnPlayer(player, () -> {
                            if (success) player.sendMessage("已吸附 " + target.getName() + " 扬声器[" + id + "] 偏移到整数网格");
                        })));
        return true;
    }

    private boolean handleSnapEntity(Player player, String[] args) {
        String nameFilter = args.length >= 3 ? joinArgs(args, 2) : null;
        resolveOne(player, nameFilter, target -> {
            if (target == null) return;
            controller.getManager().snapEntityPosition(target.getUuid(),
                    success -> runOnPlayer(player, () -> {
                        if (success) player.sendMessage("已吸附 " + target.getName() + " 位置到整数网格");
                    }));
        });
        return true;
    }

    /** 判断是否像名称（不含 screen_/speaker_ 前缀），而非外设 ID。 */
    private static boolean looksLikeName(String s) {
        return !s.startsWith("screen_") && !s.startsWith("speaker_");
    }

    private boolean showHelp(CommandSender sender) {
        sender.sendMessage("/mtv create — 创建 MTV");
        sender.sendMessage("/mtv edit [名称] — 打开最近（或指定名称）的播放器编辑页");
        sender.sendMessage("/mtv player edit [名称] — 打开最近（或指定名称）的播放器编辑页");
        sender.sendMessage("/mtv channel edit [名称] — 打开最近（或指定名称）的频道编辑页");
        sender.sendMessage("/mtv channel list — 打开公共频道目录");
        sender.sendMessage("/mtv channel create — 创建公共频道");
        sender.sendMessage("/mtv remove <名称> [--confirm] — 删除 MTV");
        sender.sendMessage("/mtv url <地址> [名称] — 设置 URL");
        sender.sendMessage("/mtv speed <倍速> [名称] — 设置播放速度");
        sender.sendMessage("/mtv size [外设ID] <宽> <高> [名称] — 设置屏幕尺寸");
        sender.sendMessage("/mtv offset [外设ID] <x> <y> <z> [名称] — 设置屏幕偏移");
        sender.sendMessage("/mtv fill [外设ID] <fill|keep_aspect_cover> [名称] — 设置填充模式");
        sender.sendMessage("/mtv brightness [外设ID] <0-15> [名称] — 设置亮度");
        sender.sendMessage("/mtv danmaku [外设ID] <on|off> [名称] — 设置屏幕弹幕开关");
        sender.sendMessage("/mtv volume [外设ID] <0-4> [名称] — 设置音量");
        sender.sendMessage("/mtv range [外设ID] <范围> [名称] — 设置扬声器范围");
        sender.sendMessage("/mtv start <微秒> [名称] — 设置起始时间");
        sender.sendMessage("/mtv name <新名称> [名称] — 重命名");
        sender.sendMessage("/mtv rotate [外设ID] <rx> <ry> <rz> <rw> [名称] — 设置屏幕旋转");
        sender.sendMessage("/mtv soffset [外设ID] <x> <y> <z> [名称] — 设置扬声器偏移");
        sender.sendMessage("/mtv snap <screen|speaker|entity> [外设ID] [名称] — 吸附到整数网格");
        sender.sendMessage("/mtv tp [名称] — 传送到 MTV");
        sender.sendMessage("/mtv movehere [名称] — 将 MTV 传送到你脚下");
        sender.sendMessage("/mtv list — 列出附近的 MTV");
        sender.sendMessage("/mtv gui — 打开管理 GUI");
        return true;
    }

    // ==================== Peripheral-aware resolution ====================

    private void resolveWithScreen(Player player, String periphId, String nameFilter,
                                   BiConsumer<ManagedMtvPlayer, String> action) {
        resolveOne(player, nameFilter, target -> {
            if (target == null) return;
            String id = controller.resolveScreenId(target, periphId);
            if (id == null) {
                player.sendMessage("该 MTV 没有屏幕。");
                return;
            }
            action.accept(target, id);
        });
    }

    private void resolveWithSpeaker(Player player, String periphId, String nameFilter,
                                    BiConsumer<ManagedMtvPlayer, String> action) {
        resolveOne(player, nameFilter, target -> {
            if (target == null) return;
            String id = controller.resolveSpeakerId(target, periphId);
            if (id == null) {
                player.sendMessage("该 MTV 没有扬声器。");
                return;
            }
            action.accept(target, id);
        });
    }

    // ==================== Generic helpers ====================

    private void resolveOne(Player player, String nameFilter, Consumer<ManagedMtvPlayer> done) {
        controller.getManager().findNearbyAsync(player, RANGE, candidates -> runOnPlayer(player, () -> {
            if (candidates.isEmpty()) {
                player.sendMessage("附近 50 米内没有 MTV 播放器。");
                done.accept(null);
                return;
            }
            if (nameFilter != null && !nameFilter.isBlank()) {
                for (var c : candidates) {
                    if (c.getName().equalsIgnoreCase(nameFilter)) {
                        done.accept(c);
                        return;
                    }
                }
                for (var c : candidates) {
                    if (c.getName().toLowerCase().contains(nameFilter.toLowerCase())) {
                        done.accept(c);
                        return;
                    }
                }
                player.sendMessage("附近没有找到名称包含 \"" + nameFilter + "\" 的 MTV 播放器。");
                done.accept(null);
                return;
            }
            if (candidates.size() == 1) {
                done.accept(candidates.get(0));
                return;
            }
            player.sendMessage("附近有多个 MTV 播放器，请指定名称:");
            for (var c : candidates) {
                double dist = Math.sqrt(player.getLocation().distanceSquared(c.toLocation()));
                player.sendMessage("  " + c.getName() + " (" + (int) dist + "m)");
            }
            player.sendMessage("例: /mtv edit " + candidates.get(0).getName());
            done.accept(null);
        }));
    }

    private void findFirstByName(Player player, String name, Consumer<ManagedMtvPlayer> done) {
        controller.getManager().findNearbyAsync(player, RANGE, candidates -> runOnPlayer(player, () -> {
            for (var candidate : candidates) {
                if (candidate.getName().equalsIgnoreCase(name)
                        || candidate.getName().toLowerCase().contains(name.toLowerCase())) {
                    done.accept(candidate);
                    return;
                }
            }
            done.accept(null);
        }));
    }

    private boolean handleSpeakerFloat(CommandSender sender, String[] args, String label,
                                       SpeakerFloatAction action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.edit")) return true;
        String periphId;
        int valStart;
        if (args.length >= 3 && isPeriphId(args[1])) {
            periphId = args[1];
            valStart = 2;
        } else {
            periphId = null;
            valStart = 1;
        }
        if (args.length < valStart + 1) {
            sender.sendMessage("用法: /mtv " + args[0] + " [外设ID] <值> [名称]");
            return true;
        }
        try {
            float value = Float.parseFloat(args[valStart]);
            resolveOne(player, args.length > valStart + 1 ? joinArgs(args, valStart + 1) : null, target -> {
                if (target == null) return;
                String id = controller.resolveSpeakerId(target, periphId);
                if (id == null) {
                    player.sendMessage("该 MTV 没有扬声器。");
                    return;
                }
                action.accept(target, id, value, success -> runOnPlayer(player, () -> {
                    if (success) {
                        sender.sendMessage("已设置 " + target.getName() + " 扬声器[" + id + "] " + label + ": " + value);
                    }
                }));
            });
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(label + "必须是数字。");
            return true;
        }
    }

    private boolean withFloatTarget(CommandSender sender,
                                    String[] args,
                                    String label,
                                    TriConsumer<ManagedMtvPlayer, Float, Consumer<Boolean>> updater) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv " + args[0] + " <值> [名称]");
            return true;
        }
        try {
            float value = Float.parseFloat(args[1]);
            resolveOne(player, args.length >= 3 ? joinArgs(args, 2) : null, target -> {
                if (target == null) {
                    return;
                }
                updater.accept(target, value, success -> runOnPlayer(player, () -> {
                    if (success) {
                        sender.sendMessage("已设置 " + target.getName() + " " + label + ": " + value);
                    }
                }));
            });
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(label + "必须是数字。");
            return true;
        }
    }

    private boolean withLongTarget(CommandSender sender,
                                   String[] args,
                                   String label,
                                   TriConsumer<ManagedMtvPlayer, Long, Consumer<Boolean>> updater) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("只能由玩家执行该命令。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法: /mtv " + args[0] + " <值> [名称]");
            return true;
        }
        try {
            long value = Long.parseLong(args[1]);
            resolveOne(player, args.length >= 3 ? joinArgs(args, 2) : null, target -> {
                if (target == null) {
                    return;
                }
                updater.accept(target, value, success -> runOnPlayer(player, () -> {
                    if (success) {
                        sender.sendMessage("已设置 " + target.getName() + " " + label + ": " + value);
                    }
                }));
            });
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(label + "必须是整数。");
            return true;
        }
    }

    private int executeBrigadier(CommandSourceStack source, String... args) {
        dispatch(source.getSender(), args);
        return SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildScopedEditNode(String scope) {
        return Commands.literal(scope)
                .then(Commands.literal(EDIT_SUBCOMMAND)
                        .executes(ctx -> executeBrigadier(ctx.getSource(), scope, EDIT_SUBCOMMAND))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), scope, EDIT_SUBCOMMAND, ctx.getArgument("name", String.class)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildChannelNode() {
        return Commands.literal(CHANNEL_SCOPE)
                .then(Commands.literal(EDIT_SUBCOMMAND)
                        .executes(ctx -> executeBrigadier(ctx.getSource(), CHANNEL_SCOPE, EDIT_SUBCOMMAND))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), CHANNEL_SCOPE, EDIT_SUBCOMMAND, ctx.getArgument("name", String.class)))))
                .then(Commands.literal("list")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), CHANNEL_SCOPE, "list")))
                .then(Commands.literal("create")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), CHANNEL_SCOPE, "create")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSizeNode() {
        return Commands.literal("size")
                .then(Commands.argument("width", FloatArgumentType.floatArg())
                        .then(Commands.argument("height", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "size",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "width")),
                                        Float.toString(FloatArgumentType.getFloat(ctx, "height"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "size",
                                                Float.toString(FloatArgumentType.getFloat(ctx, "width")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "height")),
                                                ctx.getArgument("name", String.class))))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("width", FloatArgumentType.floatArg())
                                .then(Commands.argument("height", FloatArgumentType.floatArg())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "size",
                                                ctx.getArgument("periphId", String.class),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "width")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "height"))))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeBrigadier(ctx.getSource(), "size",
                                                        ctx.getArgument("periphId", String.class),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "width")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "height")),
                                                        ctx.getArgument("name", String.class)))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildOffsetNode() {
        var withoutPeriph = Commands.argument("x", FloatArgumentType.floatArg())
                .then(Commands.argument("y", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "offset",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                        Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                        Float.toString(FloatArgumentType.getFloat(ctx, "z"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "offset",
                                                Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "z")),
                                                ctx.getArgument("name", String.class))))));

        var withPeriph = Commands.argument("periphId", StringArgumentType.word())
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "offset",
                                                ctx.getArgument("periphId", String.class),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "z"))))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeBrigadier(ctx.getSource(), "offset",
                                                        ctx.getArgument("periphId", String.class),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "z")),
                                                        ctx.getArgument("name", String.class)))))));

        return Commands.literal("offset")
                .then(withoutPeriph)
                .then(withPeriph);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildFillNode() {
        return Commands.literal("fill")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("fill");
                            builder.suggest("keep_aspect_cover");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "fill", ctx.getArgument("mode", String.class)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "fill", ctx.getArgument("mode", String.class), ctx.getArgument("name", String.class)))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("fill");
                                    builder.suggest("keep_aspect_cover");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "fill", ctx.getArgument("periphId", String.class), ctx.getArgument("mode", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "fill", ctx.getArgument("periphId", String.class), ctx.getArgument("mode", String.class), ctx.getArgument("name", String.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildBrightnessNode() {
        return Commands.literal("brightness")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "brightness", Integer.toString(IntegerArgumentType.getInteger(ctx, "value"))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "brightness", Integer.toString(IntegerArgumentType.getInteger(ctx, "value")), ctx.getArgument("name", String.class)))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 15))
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "brightness", ctx.getArgument("periphId", String.class), Integer.toString(IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "brightness", ctx.getArgument("periphId", String.class), Integer.toString(IntegerArgumentType.getInteger(ctx, "value")), ctx.getArgument("name", String.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildDanmakuNode() {
        return Commands.literal("danmaku")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "danmaku", ctx.getArgument("value", String.class)))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "danmaku", ctx.getArgument("value", String.class), ctx.getArgument("name", String.class)))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("on");
                                    builder.suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "danmaku", ctx.getArgument("periphId", String.class), ctx.getArgument("value", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "danmaku", ctx.getArgument("periphId", String.class), ctx.getArgument("value", String.class), ctx.getArgument("name", String.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildVolumeNode() {
        return Commands.literal("volume")
                .then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "volume", Float.toString(FloatArgumentType.getFloat(ctx, "value"))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "volume", Float.toString(FloatArgumentType.getFloat(ctx, "value")), ctx.getArgument("name", String.class)))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "volume", ctx.getArgument("periphId", String.class), Float.toString(FloatArgumentType.getFloat(ctx, "value"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "volume", ctx.getArgument("periphId", String.class), Float.toString(FloatArgumentType.getFloat(ctx, "value")), ctx.getArgument("name", String.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRangeNode() {
        return Commands.literal("range")
                .then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "range", Float.toString(FloatArgumentType.getFloat(ctx, "value"))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "range", Float.toString(FloatArgumentType.getFloat(ctx, "value")), ctx.getArgument("name", String.class)))))
                .then(Commands.argument("periphId", StringArgumentType.word())
                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "range", ctx.getArgument("periphId", String.class), Float.toString(FloatArgumentType.getFloat(ctx, "value"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "range", ctx.getArgument("periphId", String.class), Float.toString(FloatArgumentType.getFloat(ctx, "value")), ctx.getArgument("name", String.class))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRotateNode() {
        var withoutPeriph = Commands.argument("rx", FloatArgumentType.floatArg())
                .then(Commands.argument("ry", FloatArgumentType.floatArg())
                        .then(Commands.argument("rz", FloatArgumentType.floatArg())
                                .then(Commands.argument("rw", FloatArgumentType.floatArg())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "rotate",
                                                Float.toString(FloatArgumentType.getFloat(ctx, "rx")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "ry")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "rz")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "rw"))))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeBrigadier(ctx.getSource(), "rotate",
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rx")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "ry")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rz")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rw")),
                                                        ctx.getArgument("name", String.class)))))));

        var withPeriph = Commands.argument("periphId", StringArgumentType.word())
                .then(Commands.argument("rx", FloatArgumentType.floatArg())
                        .then(Commands.argument("ry", FloatArgumentType.floatArg())
                                .then(Commands.argument("rz", FloatArgumentType.floatArg())
                                        .then(Commands.argument("rw", FloatArgumentType.floatArg())
                                                .executes(ctx -> executeBrigadier(ctx.getSource(), "rotate",
                                                        ctx.getArgument("periphId", String.class),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rx")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "ry")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rz")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "rw"))))
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "rotate",
                                                                ctx.getArgument("periphId", String.class),
                                                                Float.toString(FloatArgumentType.getFloat(ctx, "rx")),
                                                                Float.toString(FloatArgumentType.getFloat(ctx, "ry")),
                                                                Float.toString(FloatArgumentType.getFloat(ctx, "rz")),
                                                                Float.toString(FloatArgumentType.getFloat(ctx, "rw")),
                                                                ctx.getArgument("name", String.class))))))));

        return Commands.literal("rotate")
                .then(withoutPeriph)
                .then(withPeriph);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSpeakerOffsetNode() {
        var withoutPeriph = Commands.argument("x", FloatArgumentType.floatArg())
                .then(Commands.argument("y", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "soffset",
                                        Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                        Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                        Float.toString(FloatArgumentType.getFloat(ctx, "z"))))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "soffset",
                                                Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "z")),
                                                ctx.getArgument("name", String.class))))));

        var withPeriph = Commands.argument("periphId", StringArgumentType.word())
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                        .then(Commands.argument("y", FloatArgumentType.floatArg())
                                .then(Commands.argument("z", FloatArgumentType.floatArg())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "soffset",
                                                ctx.getArgument("periphId", String.class),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                Float.toString(FloatArgumentType.getFloat(ctx, "z"))))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeBrigadier(ctx.getSource(), "soffset",
                                                        ctx.getArgument("periphId", String.class),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "x")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "y")),
                                                        Float.toString(FloatArgumentType.getFloat(ctx, "z")),
                                                        ctx.getArgument("name", String.class)))))));

        return Commands.literal("soffset")
                .then(withoutPeriph)
                .then(withPeriph);
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSnapNode() {
        return Commands.literal("snap")
                .then(Commands.literal("screen")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "screen"))
                        .then(Commands.argument("periphId", StringArgumentType.word())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "screen", ctx.getArgument("periphId", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "screen", ctx.getArgument("periphId", String.class), ctx.getArgument("name", String.class)))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "screen", ctx.getArgument("name", String.class)))))
                .then(Commands.literal("speaker")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "speaker"))
                        .then(Commands.argument("periphId", StringArgumentType.word())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "speaker", ctx.getArgument("periphId", String.class)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "speaker", ctx.getArgument("periphId", String.class), ctx.getArgument("name", String.class)))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "speaker", ctx.getArgument("name", String.class)))))
                .then(Commands.literal("entity")
                        .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "entity"))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeBrigadier(ctx.getSource(), "snap", "entity", ctx.getArgument("name", String.class)))));
    }

    private void runOnPlayer(Player player, Runnable task) {
        player.getScheduler().run(gui.getPlugin(), scheduledTask -> task.run(), null);
    }

    private static boolean isPeriphId(String s) {
        return s.startsWith("screen_") || s.startsWith("speaker_");
    }

    private static String joinArgs(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)).trim();
    }

    private static Location getTargetLocation(Player player) {
        var trace = player.rayTraceBlocks(10);
        if (trace != null && trace.getHitPosition() != null) {
            var pos = trace.getHitPosition();
            var loc = pos.toLocation(player.getWorld());
            loc.setYaw(player.getYaw());
            loc.setPitch(player.getPitch());
            return loc;
        }
        return player.getLocation().add(player.getLocation().getDirection().multiply(5));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "edit", "player", "channel", "remove", "url", "speed", "size", "offset",
                            "fill", "brightness", "danmaku", "volume", "range", "start", "name", "rotate", "soffset",
                            "snap", "tp", "movehere", "list", "gui").stream()
                    .filter(it -> it.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args[0].equalsIgnoreCase("snap") && args.length == 2) {
            return List.of("screen", "speaker", "entity").stream()
                    .filter(it -> it.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args[0].equalsIgnoreCase(PLAYER_SCOPE) && args.length == 2) {
            return List.of(EDIT_SUBCOMMAND).stream()
                    .filter(it -> it.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args[0].equalsIgnoreCase(CHANNEL_SCOPE) && args.length == 2) {
            return List.of(EDIT_SUBCOMMAND, "list", "create").stream()
                    .filter(it -> it.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @FunctionalInterface
    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    @FunctionalInterface
    private interface SpeakerFloatAction {
        void accept(ManagedMtvPlayer target, String periphId, float value, Consumer<Boolean> done);
    }
}
