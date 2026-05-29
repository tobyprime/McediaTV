package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaybackStatus;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;

public class ChannelMenuPage extends GuiPage {
    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.CHANNEL_MENU; }

    @Override
    public Component getTitle(PageEntry entry) {
        String cid = entry.getState("channel_id", "频道");
        return Component.text("频道 - " + cid);
    }

    @Override
    public Material icon() { return Material.JUKEBOX; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
        // Resolve channel ID: from entity binding or from state
        if (uuid != null) {
            context.read(player, uuid, snap -> {
                var binding = context.manager().getChannelService().resolveBinding(snap);
                entry.putState("channel_id", binding.channelId());
                renderChannelContent(player, context, nav, entry, uuid);
            });
        } else {
            renderChannelContent(player, context, nav, entry, null);
        }
    }

    private void renderChannelContent(Player player, GuiPageContext context,
                                       NavigationState nav, PageEntry entry,
                                       UUID entityUuid) {
        String channelId = entry.getState("channel_id", "");
        if (channelId.isBlank()) { player.closeInventory(); return; }

        var state = context.manager().getChannelService().ensureChannelState(channelId);
        if (state == null) {
            player.closeInventory();
            player.sendMessage("该频道不存在或无法加载。");
            return;
        }

        var playState = state.getPlayState();
        boolean paused = playState.getState() != ChannelPlaybackStatus.PLAYING;
        long startAtUs = Math.max(0L, playState.getMediaTimeMs() * 1000L);

        var inv = createInventory(entry);
        inv.setItem(10, item(Material.CLOCK, "速度: " + playState.getSpeed() + "x",
                "左键 -0.25 / 右键 +0.25"));
        inv.setItem(11, item(Material.MUSIC_DISC_CAT, "设置当前媒体",
                playState.getMediaUrl().isBlank() ? "未设置" : MtvGui.summarize(playState.getMediaUrl()),
                "会替换整个播放列表"));
        inv.setItem(12, item(Material.COMPARATOR, "播放顺序", MtvGui.formatPlayOrderMode(state),
                "点击切换"));
        inv.setItem(13, item(Material.STONE_BUTTON, "上一首", "始终按列表顺序"));
        var pauseIcon = paused ? Material.YELLOW_WOOL : Material.RED_WOOL;
        var pauseName = paused ? "▶ 播放" : "⏸ 暂停";
        inv.setItem(14, item(pauseIcon, pauseName));
        inv.setItem(15, item(Material.STONE_BUTTON, "下一首", "始终按列表顺序"));
        inv.setItem(16, item(Material.COMPASS, "设置到位置: " + MtvGui.formatDurationUs(startAtUs),
                "点击输入微秒值"));
        inv.setItem(19, item(Material.HOPPER, "首加播放项", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(20, item(Material.CHEST, "尾加播放项", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(21, item(Material.STRUCTURE_VOID, "从头播放当前项"));
        inv.setItem(22, item(Material.STONE_BUTTON, "后退 1 秒", "潜行点击后退 10 秒"));
        inv.setItem(23, item(Material.STONE_BUTTON, "前进 1 秒", "潜行点击前进 10 秒"));
        inv.setItem(24, item(Material.BOOK, "列表信息",
                "项目数: " + state.getPlaylist().size(),
                "当前位置: " + (state.getPlaylist().isEmpty() ? "无"
                        : (state.getPlaylistCursor() + 1))));
        inv.setItem(25, item(Material.LAVA_BUCKET, "清空播放列表", "清除所有播放项并停止播放"));
        inv.setItem(47, item(Material.ARROW, entityUuid != null ? "返回实体页" : "返回频道管理"));
        inv.setItem(49, item(Material.SPYGLASS, "刷新"));

        for (int i = 0; i < MtvGui.CHANNEL_PLAYLIST_SLOTS.length && i < state.getPlaylist().size(); i++) {
            var entry2 = state.getPlaylist().get(i);
            boolean current = i == state.getPlaylistCursor();
            inv.setItem(MtvGui.CHANNEL_PLAYLIST_SLOTS[i], item(
                    current ? Material.MUSIC_DISC_11 : Material.PAPER,
                    (current ? "▶ " : "") + "#" + (i + 1) + " " + MtvGui.summarize(entry2.mediaUrl()),
                    "左键播放 / 右键删除",
                    "Shift左键移到最前 / Shift右键移到最后"));
        }
        setupTitleBar(inv, nav, entry);
        openInventory(player, inv);
    }

    @Override
    protected boolean handleContentClick(Player player, GuiPageContext context,
                                          PageEntry entry, int slot,
                                          boolean rightClick, boolean shiftClick) {
        UUID uuid = entry.getEntityUuid();
        String channelId = entry.getState("channel_id", "");
        if (channelId.isBlank()) return false;

        if (slot == 47) {
            if (uuid != null) {
                context.navigateTo(player, MtvGui.GuiType.PLAYER_MENU, uuid);
            } else {
                var st = context.newState();
                st.put("channel_id", channelId);
                context.navigateTo(player, MtvGui.GuiType.PUBLIC_CHANNEL_MANAGE, null, null, st);
            }
            return true;
        }
        if (slot == 49) {
            context.refresh(player);
            return true;
        }

        // All other slots require playback control
        var state = context.manager().getChannelService().ensureChannelState(channelId);
        if (state == null) return true;
        if (!canControl(player, context, state)) return true;

        var playState = state.getPlayState();
        float currentSpeed = (float) playState.getSpeed();

        switch (slot) {
            case 10 -> {
                float delta = (rightClick ? 1 : -1) * (shiftClick ? 1.0F : 0.25F);
                float speed = Math.max(0.25F, Math.min(4.0F, currentSpeed + delta));
                updateChannel(player, context, uuid, channelId,
                        cid -> context.manager().getChannelService().updateSpeed(cid, speed));
            }
            case 11 -> {
                context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_media_url");
            }
            case 12 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::cyclePlayOrderMode);
            case 13 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::playPreviousManual);
            case 14 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::togglePause);
            case 15 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::playNextManual);
            case 16 -> {
                context.requestInput(player, "请输入跳转位置的微秒值。", "channel_start_at");
            }
            case 19 -> {
                context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_prepend");
            }
            case 20 -> {
                context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_append");
            }
            case 21 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().updateStartAt(cid, 0L));
            case 22 -> {
                long delta = shiftClick ? -10_000_000L : -1_000_000L;
                updateChannel(player, context, uuid, channelId,
                        cid -> context.manager().getChannelService().seekRelative(cid, delta));
            }
            case 23 -> {
                long delta = shiftClick ? 10_000_000L : 1_000_000L;
                updateChannel(player, context, uuid, channelId,
                        cid -> context.manager().getChannelService().seekRelative(cid, delta));
            }
            case 25 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().clearPlaylist(player, cid));
            default -> {
                int index = GuiPage.indexOf(MtvGui.CHANNEL_PLAYLIST_SLOTS, slot);
                if (index < 0) return false;
                if (shiftClick && rightClick) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().movePlaylistItemToBack(cid, index));
                } else if (shiftClick) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().movePlaylistItemToFront(cid, index));
                } else if (rightClick) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().removePlaylistItem(cid, index));
                } else {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().playPlaylistIndex(cid, index));
                }
            }
        }
        return true;
    }

    @Override
    public boolean handleChatInput(Player player, GuiPageContext context,
                                    PageEntry entry, String message) {
        String awaiting = entry.getState().get(MtvGui.AWAITING_KEY);
        if (awaiting == null) return false;
        UUID uuid = entry.getEntityUuid();
        String channelId = entry.getState("channel_id", "");
        String input = message.trim();

        switch (awaiting) {
            case "channel_media_url" -> {
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId, cid -> {
                        context.manager().getChannelService().updateMediaUrl(cid, input);
                        return true;
                    });
                } else {
                    boolean ok = context.manager().getChannelService().updateMediaUrl(channelId, input);
                    if (ok) context.delay(player, () -> context.refresh(player));
                }
            }
            case "channel_start_at" -> {
                long startAt;
                try { startAt = Long.parseLong(input); }
                catch (NumberFormatException e) {
                    context.runOnPlayer(player, () -> player.sendMessage("请输入有效的微秒整数。"));
                    return true;
                }
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().updateStartAt(cid, startAt));
                } else {
                    boolean ok = context.manager().getChannelService().updateStartAt(channelId, startAt);
                    if (ok) context.delay(player, () -> context.refresh(player));
                }
            }
            case "channel_prepend" -> {
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().prependPlaylistItem(cid, input));
                } else {
                    boolean ok = context.manager().getChannelService().prependPlaylistItem(channelId, input);
                    if (ok) context.delay(player, () -> context.refresh(player));
                }
            }
            case "channel_append" -> {
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().appendPlaylistItem(cid, input));
                } else {
                    boolean ok = context.manager().getChannelService().appendPlaylistItem(channelId, input);
                    if (ok) context.delay(player, () -> context.refresh(player));
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private static boolean canControl(Player player, GuiPageContext context,
                                      ChannelRuntimeState state) {
        if (context.manager().getChannelService().canControlChannelPlayback(player, state)) {
            return true;
        }
        player.sendMessage("该频道为私有频道，只有创建者或 OP 可以控制播放。");
        return false;
    }

    private static void updateChannel(Player player, GuiPageContext context,
                                       UUID uuid, String channelId,
                                       java.util.function.Function<String, Boolean> op) {
        if (uuid != null) {
            context.updateAndRefresh(player, uuid, done ->
                    context.manager().withManagedPlayer(uuid, playerEntity -> {
                        var binding = context.manager().getChannelService().resolveBinding(playerEntity);
                        return op.apply(binding.channelId());
                    }, done));
        } else {
            boolean success = op.apply(channelId);
            if (success) {
                context.delay(player, () -> context.refresh(player));
            }
        }
    }

}
