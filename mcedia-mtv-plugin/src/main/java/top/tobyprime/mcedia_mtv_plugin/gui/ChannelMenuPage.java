package top.tobyprime.mcedia_mtv_plugin.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.UUID;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelPlaybackStatus;
import top.tobyprime.mcedia_mtv_plugin.channel.ChannelRuntimeState;

public class ChannelMenuPage extends GuiPage {
    private static final String PLAYLIST_OFFSET_KEY = "pl_off";

    @Override
    public MtvGui.GuiType type() { return MtvGui.GuiType.CHANNEL_MENU; }

    @Override
    public Component getTitle(PageEntry entry) {
        String name = entry.getState("channel_name", "");
        if (!name.isBlank()) return Component.text("频道 - " + MtvGui.summarize(name));
        String cid = entry.getState("channel_id", "频道");
        return Component.text("频道 - " + cid);
    }

    @Override
    public Material icon() { return Material.JUKEBOX; }

    @Override
    protected void renderPage(Player player, GuiPageContext context,
                              NavigationState nav, PageEntry entry) {
        UUID uuid = entry.getEntityUuid();
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

        // Store channel name for title
        entry.putState("channel_name", state.getChannelName());

        var playState = state.getPlayState();
        boolean paused = playState.getState() != ChannelPlaybackStatus.PLAYING;
        String nowPlaying = playState.getMediaUrl().isBlank() ? "未设置"
                : MtvGui.summarize(playState.getMediaUrl());

        // Create inventory with resolved title showing channel name instead of ID
        String title = "频道 - " + MtvGui.fallback(state.getChannelName(), state.getChannelId());
        var inv = Bukkit.createInventory(
                new MtvGui.MtvHolder(type(), entry.getEntityUuid(), entry.getPeripheralId()),
                54, Component.text(title));

        // ── Row 1 (9-17): Playlist — single scrolling row ──
        int offset = parsePlaylistOffset(entry);
        var playlist = state.getPlaylist();
        int maxOffset = Math.max(0, playlist.size() - 7);
        if (offset > maxOffset) offset = maxOffset;
        if (offset < 0) offset = 0;

        boolean canScrollLeft = offset > 0;
        boolean canScrollRight = offset < maxOffset;

        inv.setItem(9, item(canScrollLeft ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                "◀ 向左滚动", canScrollLeft ? "显示前面的播放项" : ""));
        inv.setItem(17, item(canScrollRight ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
                "▶ 向右滚动", canScrollRight ? "显示后面的播放项" : ""));

        // Playlist items (slots 10-16, up to 7 visible)
        for (int i = 0; i < 7; i++) {
            int slot = 10 + i;
            int index = offset + i;
            if (index < playlist.size()) {
                var plItem = playlist.get(index);
                boolean current = index == state.getPlaylistCursor();
                inv.setItem(slot, item(
                        current ? Material.MUSIC_DISC_11 : Material.PAPER,
                        (current ? "▶ " : "") + "#" + (index + 1) + " " + MtvGui.summarize(plItem.mediaUrl()),
                        "左键播放 / 右键删除",
                        "Shift左键移到最前 / Shift右键移到最后"));
            } else {
                inv.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, ""));
            }
        }

        // ── Row 2 (18-26): Playlist management ──
        //     首加 / 下一个加 / 尾加 / 清空列表 / 播放顺序
        inv.setItem(18, item(Material.HOPPER, "首加", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(20, item(Material.HOPPER_MINECART, "下一个加",
                "插入到当前播放之后", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(22, item(Material.CHEST, "尾加", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(24, item(Material.LAVA_BUCKET, "清空列表", "清除所有播放项并停止播放"));
        inv.setItem(26, item(Material.COMPARATOR, "播放顺序", MtvGui.formatPlayOrderMode(state),
                "点击切换"));

        // ── Row 3 (27-35): Play/pause (above FF/RW) ──
        var pauseIcon = paused ? Material.YELLOW_WOOL : Material.RED_WOOL;
        var pauseName = paused ? "▶ 播放" : "⏸ 暂停";
        inv.setItem(31, item(pauseIcon, pauseName, "当前: " + nowPlaying));

        // ── Row 5 (45-53): FF/RW + 设置URL (底部居中) ──
        //     [⏮][⏪−20s][◀−5s][设置URL][▶+5s][⏩+20s][⏭]
        inv.setItem(46, item(Material.STONE_BUTTON, "⏮ 上一个媒体", "切换到上一首播放项"));
        inv.setItem(47, item(Material.RED_STAINED_GLASS, "⏪ −20 秒", "点击后退 20 秒"));
        inv.setItem(48, item(Material.RED_CONCRETE, "◀ −5 秒", "点击后退 5 秒"));
        inv.setItem(49, item(Material.MUSIC_DISC_CAT, "设置URL",
                "插入到当前播放之后并立即播放", MtvGui.MEDIA_INPUT_HINT));
        inv.setItem(50, item(Material.GREEN_CONCRETE, "▶ +5 秒", "点击前进 5 秒"));
        inv.setItem(51, item(Material.GREEN_STAINED_GLASS, "⏩ +20 秒", "点击前进 20 秒"));
        inv.setItem(52, item(Material.STONE_BUTTON, "⏭ 下一个媒体", "切换到下一首播放项"));

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

        // All other slots require playback control
        var state = context.manager().getChannelService().ensureChannelState(channelId);
        if (state == null) return true;
        if (!canControl(player, context, state)) return true;

        // ── Playlist scrolling ──
        if (slot == 9) {
            int offset = parsePlaylistOffset(entry);
            entry.putState(PLAYLIST_OFFSET_KEY, String.valueOf(Math.max(0, offset - 1)));
            context.refresh(player);
            return true;
        }
        if (slot == 17) {
            int offset = parsePlaylistOffset(entry);
            int maxOffset = Math.max(0, state.getPlaylist().size() - 7);
            entry.putState(PLAYLIST_OFFSET_KEY, String.valueOf(Math.min(maxOffset, offset + 1)));
            context.refresh(player);
            return true;
        }

        // ── Playlist item clicks (slots 10-16) ──
        if (slot >= 10 && slot <= 16) {
            int offset = parsePlaylistOffset(entry);
            int index = offset + (slot - 10);
            if (index < 0 || index >= state.getPlaylist().size()) return true;

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
            return true;
        }

        switch (slot) {
            // ── Row 2: Playlist management ──
            case 18 -> context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_prepend");
            case 20 -> context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_insert_next");
            case 22 -> context.requestInput(player, MtvGui.MEDIA_INPUT_MESSAGE, "channel_append");
            case 24 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().clearPlaylist(player, cid));
            case 26 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::cyclePlayOrderMode);

            // ── Row 3: Pause (above) ──
            case 31 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::togglePause);
            // ── Row 5: Remote-style controls (底部) ──
            case 46 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::playPreviousManual);
            case 47 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().seekRelative(cid, -20_000_000L));
            case 48 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().seekRelative(cid, -5_000_000L));
            case 49 -> context.requestInput(player,
                    "请输入要插入并立即播放的链接。" + MtvGui.MEDIA_INPUT_HINT + "。", "channel_set_url");
            case 50 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().seekRelative(cid, 5_000_000L));
            case 51 -> updateChannel(player, context, uuid, channelId,
                    cid -> context.manager().getChannelService().seekRelative(cid, 20_000_000L));
            case 52 -> updateChannel(player, context, uuid, channelId,
                    context.manager().getChannelService()::playNextManual);

            default -> { return false; }
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
            case "channel_insert_next" -> {
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().insertNextPlaylistItem(cid, input));
                } else {
                    boolean ok = context.manager().getChannelService().insertNextPlaylistItem(channelId, input);
                    if (ok) context.delay(player, () -> context.refresh(player));
                }
            }
            case "channel_set_url" -> {
                if (uuid != null) {
                    updateChannel(player, context, uuid, channelId,
                            cid -> context.manager().getChannelService().insertNextAndPlay(cid, input));
                } else {
                    boolean ok = context.manager().getChannelService().insertNextAndPlay(channelId, input);
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

    private static int parsePlaylistOffset(PageEntry entry) {
        try {
            return Integer.parseInt(entry.getState(PLAYLIST_OFFSET_KEY, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
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
