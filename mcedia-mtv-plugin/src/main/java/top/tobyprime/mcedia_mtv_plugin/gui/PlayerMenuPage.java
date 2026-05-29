package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaybackStatus;
import top.tobyprime.mcedia_mtv_plugin.channel.MtvChannelBinding;
import top.tobyprime.mcedia_mtv_plugin.controller.MtvPeripheralController;

public class PlayerMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.PLAYER_MENU; }

    @Override
    public Component getTitle(PageEntry entry) {
        String name = entry.getState("player_name", "MTV");
        return Component.text("MTV: " + name);
    }

    @Override
    public Material icon() { return Material.NAME_TAG; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) { player.closeInventory(); return; }
        context.read(player, uuid, snapshot -> {
            var binding = context.manager().getChannelService().resolveBinding(snapshot);
            var publicChannel = binding.isBroadcast()
                    ? context.manager().getChannelService().getPublicChannel(binding.channelId())
                    : null;

            // Update the entry title with the live player name
            entry.putState("player_name", snapshot.getName());

            var inv = createInventory(entry);

            // Common controls across both modes
            var powerIcon = snapshot.isPowered() ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(10, item(powerIcon, "电源: " + (snapshot.isPowered() ? "开" : "关"), "点击切换待机状态"));
            inv.setItem(12, item(Material.ITEM_FRAME, "外设列表", "屏幕 / 扬声器"));
            inv.setItem(13, item(Material.COMPASS, "位置与朝向", "移动 / 旋转实体"));
            inv.setItem(15, item(Material.ENDER_PEARL, "传送到实体"));
            inv.setItem(14, item(Material.NOTE_BLOCK,
                    "总音量: " + String.format("%.2f", snapshot.getMasterVolume()),
                    "左键 -0.1 / 右键 +0.1", "潜行改动 0.25"));
            inv.setItem(22, item(Material.NAME_TAG, "重命名: " + snapshot.getName(), "点击修改名称"));
            inv.setItem(53, item(Material.TNT, "删除 MTV"));

            if (binding.isBroadcast()) {
                inv.setItem(11, item(Material.BOOK, "公共频道信息",
                        "频道: " + MtvGui.fallback(publicChannel != null ? publicChannel.getChannelName() : null, binding.channelId()),
                        "ID: " + binding.channelId(),
                        "点击查看频道信息"));
                inv.setItem(31, item(Material.BOOK, "切换绑定", "重新选择要绑定的公共频道"));
                inv.setItem(32, item(Material.STRUCTURE_VOID, "取消绑定", "切回私有 self 频道"));
            } else {
                var channelState = context.manager().getChannelService().ensureChannelState(binding.channelId());
                var playState = channelState != null ? channelState.getPlayState() : null;
                float speed = playState != null ? (float) playState.getSpeed() : 1.0F;
                String mediaUrl = playState != null ? playState.getMediaUrl() : "";
                boolean paused = playState != null ? playState.getState() != ChannelPlaybackStatus.PLAYING : false;
                long startAt = playState != null ? Math.max(0L, playState.getMediaTimeMs() * 1000L) : 0L;

                inv.setItem(11, item(Material.CLOCK, "速度: " + speed + "x", "左键 -0.25 / 右键 +0.25"));
                inv.setItem(31, item(Material.JUKEBOX, "频道编辑", "打开 channel 播放与列表页"));
                inv.setItem(32, item(Material.BOOK, "公共频道", "搜索并绑定公共频道"));
                inv.setItem(33, item(Material.STRUCTURE_VOID, "切回私有频道", "恢复 self 频道绑定"));
                inv.setItem(40, item(Material.MUSIC_DISC_CAT, "设置播放链接",
                        mediaUrl.isBlank() ? "未设置" : mediaUrl, MtvGui.MEDIA_INPUT_HINT));
                inv.setItem(47, item(Material.STRUCTURE_VOID, "从头播放"));
                inv.setItem(48, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
                var pauseIcon = paused ? Material.YELLOW_WOOL : Material.RED_WOOL;
                var pauseName = paused ? "▶ 播放" : "⏸ 暂停";
                inv.setItem(49, item(pauseIcon, pauseName));
                inv.setItem(50, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
                inv.setItem(51, item(Material.COMPASS, "设置到位置: " + MtvGui.formatDurationUs(startAt), "点击输入微秒值"));
            }

            setupTitleBar(inv, nav, entry);
            openInventory(player, inv);
        });
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return false;
        context.read(player, uuid, snap -> {
            if (snap == null) return;
            var binding = context.manager().getChannelService().resolveBinding(snap);

            switch (slot) {
                // Rename
                case 22 -> {
                    context.requestInput(player, "请输入新名称。", "rename");
                }
                // Power
                case 10 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().setPowered(uuid, !snap.isPowered(), done));
                // Speed (private) / Channel info (broadcast)
                case 11 -> {
                    if (binding.isBroadcast()) {
                        var st = context.newState();
                        st.put("channel_id", binding.channelId());
                        context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE, uuid, null, st);
                        return;
                    }
                    float delta = (rightClick ? 1 : -1) * (shiftClick ? 1.0F : 0.25F);
                    var channelState = context.manager().getChannelService()
                            .ensureChannelState(binding.channelId());
                    float currentSpeed = channelState != null ? (float) channelState.getPlayState().getSpeed() : 1.0F;
                    float v = Math.max(0.25F, Math.min(4.0F, currentSpeed + delta));
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().updateSpeed(uuid, v, done));
                }
                // Peripherals
                case 12 -> context.navigateTo(player, MtvGui.GuiType.PERIPHERAL_LIST, uuid);
                // World transform
                case 13 -> context.navigateTo(player, MtvGui.GuiType.WORLD_TRANSFORM, uuid);
                // Volume
                case 14 -> {
                    float delta = (rightClick ? 1 : -1) * (shiftClick ? 0.25F : 0.1F);
                    float volume = Math.max(0.0F, Math.min(1.0F, snap.getMasterVolume() + delta));
                    context.updateAndRefresh(player, uuid,
                            done -> context.manager().setMasterVolume(uuid, volume, done));
                }
                // Teleport to entity
                case 15 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.teleport")) return;
                    var loc = snap.toLocation();
                    if (loc == null) {
                        player.sendMessage("该 MTV 的位置无效或所在世界不存在。");
                        return;
                    }
                    player.teleportAsync(loc);
                    player.sendMessage("已传送到 MTV 播放器: " + snap.getName());
                }
                // Channel edit (private) / Switch binding (broadcast)
                case 31 -> {
                    if (binding.isBroadcast()) {
                        context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, uuid);
                        return;
                    }
                    context.navigateTo(player, MtvGui.GuiType.CHANNEL_MENU, uuid);
                }
                // Public channel list (both modes)
                case 32 -> context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_LIST, uuid);
                // Unbind / switch to private
                case 33 -> context.updateAndRefresh(player, uuid,
                        done -> context.manager().updateChannelBinding(uuid, MtvChannelBinding.self(), done));
                // Set media URL (private only)
                case 40 -> {
                    if (binding.isBroadcast()) return;
                    context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "media_url");
                }
                // Restart from beginning (private only)
                case 47 -> {
                    if (binding.isBroadcast()) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().updateStartAt(uuid, 0L, done));
                }
                // Seek backward (private only)
                case 48 -> {
                    if (binding.isBroadcast()) return;
                    long delta = shiftClick ? -10_000_000L : -1_000_000L;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, delta, done));
                }
                // Pause/play toggle (private only)
                case 49 -> {
                    if (binding.isBroadcast()) return;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().togglePause(uuid, done));
                }
                // Seek forward (private only)
                case 50 -> {
                    if (binding.isBroadcast()) return;
                    long delta = shiftClick ? 10_000_000L : 1_000_000L;
                    context.updateAndRefresh(player, uuid,
                            done -> context.playbackController().seekRelative(uuid, delta, done));
                }
                // Seek to position (private only)
                case 51 -> {
                    if (binding.isBroadcast()) return;
                    context.requestInput(player, "请输入跳转位置的微秒值。", "start_at");
                }
                // Delete entity
                case 53 -> {
                    if (!MtvPeripheralController.checkPerm(player, "mcedia.mtv.delete")) return;
                    player.closeInventory();
                    context.manager().deletePlayerAsync(uuid, success ->
                            context.delay(player, () -> player.sendMessage(Boolean.TRUE.equals(success)
                                    ? "已删除 MTV 播放器。"
                                    : "删除 MTV 播放器失败。")));
                }
            }
        });
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (awaiting == null) return false;
        UUID uuid = entry.getEntityUuid();
        if (uuid == null) return true;
        String input = message.trim();

        switch (awaiting) {
            case "rename" -> context.manager().updateName(uuid, input,
                    success -> context.runOnPlayer(player, () -> {
                        if (!Boolean.TRUE.equals(success)) {
                            player.sendMessage("重命名失败。");
                            return;
                        }
                        context.refresh(player);
                    }));
            case "media_url" -> context.playbackController().updateMediaUrl(uuid, input,
                    success -> context.runOnPlayer(player, () -> {
                        if (!Boolean.TRUE.equals(success)) {
                            player.sendMessage("设置播放 URL 失败。");
                            return;
                        }
                        context.refresh(player);
                    }));
            case "start_at" -> {
                long startAt;
                try {
                    startAt = Long.parseLong(input);
                } catch (NumberFormatException e) {
                    context.runOnPlayer(player, () -> player.sendMessage("请输入有效的微秒整数。"));
                    return true;
                }
                context.playbackController().updateStartAt(uuid, startAt,
                        success -> context.runOnPlayer(player, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                player.sendMessage("设置播放位置失败。");
                                return;
                            }
                            context.refresh(player);
                        }));
            }
            default -> { return false; }
        }
        return true;
    }
}
